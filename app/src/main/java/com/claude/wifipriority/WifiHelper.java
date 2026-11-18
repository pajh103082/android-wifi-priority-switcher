package com.claude.wifipriority;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Wi-Fi 相關操作與「該不該切換」的判斷邏輯。 */
@SuppressWarnings("deprecation")
final class WifiHelper {
    private WifiHelper() {}

    /** 剛切換後的冷卻時間，避免來回跳。 */
    private static final long COOLDOWN_MS = 45_000L;
    /** 切換後多久還沒連上就算失敗。 */
    private static final long FAIL_CHECK_MS = 40_000L;
    /** 目前網路（較高順位）訊號要比門檻再低這麼多才放棄它，避免邊緣抖動。 */
    private static final int HYSTERESIS_DB = 5;

    static WifiManager wm(Context c) {
        return (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    static String clean(String ssid) {
        if (ssid == null) return "";
        String s = ssid;
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        if (s.equals("<unknown ssid>") || s.equals("0x")) return "";
        return s;
    }

    static boolean hasLocationPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean hasBackgroundLocation(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true;
        return c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean isLocationOn(Context c) {
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return lm.isLocationEnabled();
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    static String currentSsid(Context c) {
        WifiInfo info = wm(c).getConnectionInfo();
        if (info == null || info.getNetworkId() == -1) return "";
        return clean(info.getSSID());
    }

    static int currentRssi(Context c) {
        WifiInfo info = wm(c).getConnectionInfo();
        return info == null ? -127 : info.getRssi();
    }

    /** 每個 SSID 在掃描結果中最強的訊號。 */
    static Map<String, Integer> scanMap(Context c) {
        Map<String, Integer> m = new HashMap<>();
        try {
            List<ScanResult> list = wm(c).getScanResults();
            if (list != null) for (ScanResult r : list) {
                String s = clean(r.SSID);
                if (s.isEmpty()) continue;
                Integer old = m.get(s);
                if (old == null || r.level > old) m.put(s, r.level);
            }
        } catch (SecurityException ignored) {}
        return m;
    }

    static List<WifiConfiguration> savedConfigs(Context c) {
        try {
            List<WifiConfiguration> l = wm(c).getConfiguredNetworks();
            return l == null ? java.util.Collections.emptyList() : l;
        } catch (SecurityException e) {
            return java.util.Collections.emptyList();
        }
    }

    static Set<String> savedSsids(Context c) {
        Set<String> out = new LinkedHashSet<>();
        for (WifiConfiguration w : savedConfigs(c)) {
            String s = clean(w.SSID);
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    static int findNetId(Context c, String ssid) {
        int found = -1;
        for (WifiConfiguration w : savedConfigs(c)) {
            if (ssid.equals(clean(w.SSID))) found = Math.max(found, w.networkId);
        }
        return found;
    }

    /** 新增一個網路到系統（App 自己建立的，之後才能指定連線）。回傳 networkId，失敗為 -1。 */
    static int addNetwork(Context c, String ssid, String password, boolean hidden) {
        WifiConfiguration cfg = new WifiConfiguration();
        cfg.SSID = "\"" + ssid + "\"";
        cfg.hiddenSSID = hidden;
        if (password == null || password.isEmpty()) {
            cfg.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        } else {
            cfg.preSharedKey = "\"" + password + "\"";
            cfg.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        }
        int id = wm(c).addNetwork(cfg);
        if (id != -1) wm(c).saveConfiguration();
        return id;
    }

    static String signalWord(Context c, int rssi) {
        if (rssi >= -60) return L.t(c, "強", "strong");
        if (rssi >= -70) return L.t(c, "中", "fair");
        if (rssi >= -80) return L.t(c, "弱", "weak");
        return L.t(c, "很弱", "very weak");
    }

    static String describe(Context c, List<String> order, String ssid) {
        if (ssid.isEmpty()) return L.t(c, "未連線", "not connected");
        int r = order.indexOf(ssid);
        if (r >= 0) return ssid + L.t(c, "（第 " + (r + 1) + " 順位）", " (#" + (r + 1) + ")");
        return ssid + L.t(c, "（不在清單）", " (not in list)");
    }

    /**
     * 檢查目前狀態，必要時切到順位最高、訊號夠強的網路。
     * @param allowSwitch false 時只回報，不動作
     * @return 給人看的狀態說明（依目前語言）
     */
    static synchronized String evaluate(Context c, boolean allowSwitch) {
        WifiManager wm = wm(c);
        if (!wm.isWifiEnabled()) return L.t(c, "Wi-Fi 目前是關閉的", "Wi-Fi is turned off");
        if (!hasLocationPermission(c)) return L.t(c, "需要「精確位置」權限才能看到附近的 Wi-Fi",
                "\"Precise location\" permission is needed to see nearby Wi-Fi");
        if (!isLocationOn(c)) return L.t(c, "請開啟手機的「定位」，否則系統不給 App 看附近的 Wi-Fi",
                "Please turn on Location, otherwise Android hides nearby Wi-Fi from apps");

        List<String> order = Prefs.getOrder(c);
        if (order.isEmpty()) return L.t(c, "優先清單是空的，請先加入網路", "Your priority list is empty — add a network first");

        String cur = currentSsid(c);
        int curRank = order.indexOf(cur);
        int threshold = Prefs.getThreshold(c);
        Map<String, Integer> scan = scanMap(c);
        String now = L.t(c, "目前：", "Now: ") + describe(c, order, cur);

        // 剛切換過：檢查是否成功
        String lastTarget = Prefs.getLastTarget(c);
        long since = System.currentTimeMillis() - Prefs.getLastSwitch(c);
        if (!lastTarget.isEmpty()) {
            if (lastTarget.equals(cur)) {
                Prefs.clearFailed(c, cur);
            } else if (since > FAIL_CHECK_MS && since < FAIL_CHECK_MS * 3 && !Prefs.recentlyFailed(c, lastTarget)) {
                Prefs.markFailed(c, lastTarget);
                Prefs.log(c, L.t(c,
                        "連到「" + lastTarget + "」失敗（密碼錯誤或訊號不穩？），5 分鐘內先跳過",
                        "Failed to connect to \"" + lastTarget + "\" (wrong password or unstable signal?) — skipping it for 5 min"));
            }
        }

        // 找清單中第一個「附近有、訊號夠、最近沒失敗」的網路
        String best = null;
        int bestRank = -1;
        for (int i = 0; i < order.size(); i++) {
            String s = order.get(i);
            Integer lv = scan.get(s);
            if (lv == null || lv < threshold) continue;
            if (!s.equals(cur) && Prefs.recentlyFailed(c, s)) continue;
            best = s;
            bestRank = i;
            break;
        }

        if (best == null) return now + L.t(c, "\n附近沒有清單中訊號夠強的網路", "\nNo network from your list is strong enough nearby");
        if (best.equals(cur)) return now + L.t(c, "\n已經是最佳選擇 ✓", "\nAlready the best choice ✓");

        boolean shouldSwitch;
        String reason;
        if (cur.isEmpty()) {
            shouldSwitch = true;
            reason = L.t(c, "目前沒有連線", "not connected");
        } else if (curRank == -1) {
            shouldSwitch = Prefs.switchFromUnlisted(c);
            reason = L.t(c, "目前的網路不在清單中", "current network is not in the list");
        } else if (bestRank < curRank) {
            shouldSwitch = true;
            reason = L.t(c, "「" + best + "」順位較高", "\"" + best + "\" has higher priority");
        } else {
            // 目前網路順位較高，但訊號太差了
            int curRssi = currentRssi(c);
            shouldSwitch = curRssi < threshold - HYSTERESIS_DB;
            reason = L.t(c, "目前網路訊號太弱（" + curRssi + " dBm）", "current signal too weak (" + curRssi + " dBm)");
        }

        if (!shouldSwitch) return now + L.t(c, "\n維持不動", "\nStaying on this network");

        String advice = now + L.t(c, "\n→ 應切到「" + best + "」（" + reason + "）",
                "\n→ Should switch to \"" + best + "\" (" + reason + ")");
        if (!allowSwitch) return advice;

        if (since < COOLDOWN_MS) return advice + L.t(c, "\n（剛切換過，稍等一下）", "\n(just switched, waiting a moment)");

        int netId = findNetId(c, best);
        if (netId < 0) {
            Prefs.markFailed(c, best);
            Prefs.log(c, L.t(c,
                    "找不到「" + best + "」的連線設定，請在 App 裡按「新增」輸入它的密碼",
                    "No saved settings for \"" + best + "\" — tap \"Add\" in the app and enter its password"));
            return advice + L.t(c, "\n但找不到它的連線設定", "\nbut its connection settings were not found");
        }

        boolean ok = wm.enableNetwork(netId, true);
        wm.reconnect();
        Prefs.setLastSwitch(c, best);
        Integer lv = scan.get(best);
        Prefs.log(c, (cur.isEmpty() ? L.t(c, "（未連線）", "(none)") : cur) + " → " + best
                + L.t(c, "（訊號 " + lv + " dBm，" + reason + "）", " (signal " + lv + " dBm, " + reason + ")")
                + (ok ? "" : L.t(c, " ⚠ 系統拒絕", " ⚠ refused by system")));
        return now + L.t(c, "\n正在切換到「" + best + "」…", "\nSwitching to \"" + best + "\"…");
    }
}
