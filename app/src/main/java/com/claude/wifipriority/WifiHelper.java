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

    static String signalWord(int rssi) {
        if (rssi >= -60) return "強";
        if (rssi >= -70) return "中";
        if (rssi >= -80) return "弱";
        return "很弱";
    }

    static String describe(List<String> order, String ssid) {
        if (ssid.isEmpty()) return "未連線";
        int r = order.indexOf(ssid);
        return ssid + (r >= 0 ? "（第 " + (r + 1) + " 順位）" : "（不在清單）");
    }

    /**
     * 檢查目前狀態，必要時切到順位最高、訊號夠強的網路。
     * @param allowSwitch false 時只回報，不動作
     * @return 給人看的狀態說明
     */
    static synchronized String evaluate(Context c, boolean allowSwitch) {
        WifiManager wm = wm(c);
        if (!wm.isWifiEnabled()) return "Wi-Fi 目前是關閉的";
        if (!hasLocationPermission(c)) return "需要「精確位置」權限才能看到附近的 Wi-Fi";
        if (!isLocationOn(c)) return "請開啟手機的「定位」，否則系統不給 App 看附近的 Wi-Fi";

        List<String> order = Prefs.getOrder(c);
        if (order.isEmpty()) return "優先清單是空的，請先加入網路";

        String cur = currentSsid(c);
        int curRank = order.indexOf(cur);
        int threshold = Prefs.getThreshold(c);
        Map<String, Integer> scan = scanMap(c);
        String curDesc = describe(order, cur);

        // 剛切換過：檢查是否成功
        String lastTarget = Prefs.getLastTarget(c);
        long since = System.currentTimeMillis() - Prefs.getLastSwitch(c);
        if (!lastTarget.isEmpty()) {
            if (lastTarget.equals(cur)) {
                Prefs.clearFailed(c, cur);
            } else if (since > FAIL_CHECK_MS && since < FAIL_CHECK_MS * 3 && !Prefs.recentlyFailed(c, lastTarget)) {
                Prefs.markFailed(c, lastTarget);
                Prefs.log(c, "連到「" + lastTarget + "」失敗（密碼錯誤或訊號不穩？），5 分鐘內先跳過");
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

        if (best == null) return "目前：" + curDesc + "\n附近沒有清單中訊號夠強的網路";
        if (best.equals(cur)) return "目前：" + curDesc + "\n已經是最佳選擇 ✓";

        boolean shouldSwitch;
        String reason;
        if (cur.isEmpty()) {
            shouldSwitch = true;
            reason = "目前沒有連線";
        } else if (curRank == -1) {
            shouldSwitch = Prefs.switchFromUnlisted(c);
            reason = "目前的網路不在清單中";
        } else if (bestRank < curRank) {
            shouldSwitch = true;
            reason = "「" + best + "」順位較高";
        } else {
            // 目前網路順位較高，但訊號太差了
            int curRssi = currentRssi(c);
            shouldSwitch = curRssi < threshold - HYSTERESIS_DB;
            reason = "目前網路訊號太弱（" + curRssi + " dBm）";
        }

        if (!shouldSwitch) return "目前：" + curDesc + "\n維持不動";

        String advice = "目前：" + curDesc + "\n→ 應切到「" + best + "」（" + reason + "）";
        if (!allowSwitch) return advice;

        if (since < COOLDOWN_MS) return advice + "\n（剛切換過，稍等一下）";

        int netId = findNetId(c, best);
        if (netId < 0) {
            Prefs.markFailed(c, best);
            Prefs.log(c, "找不到「" + best + "」的連線設定，請在 App 裡按「新增」輸入它的密碼");
            return advice + "\n但找不到它的連線設定";
        }

        boolean ok = wm.enableNetwork(netId, true);
        wm.reconnect();
        Prefs.setLastSwitch(c, best);
        Integer lv = scan.get(best);
        Prefs.log(c, (cur.isEmpty() ? "（未連線）" : cur) + " → " + best
                + "（訊號 " + lv + " dBm，" + reason + "）" + (ok ? "" : " ⚠ 系統拒絕"));
        return "目前：" + curDesc + "\n正在切換到「" + best + "」…";
    }
}
