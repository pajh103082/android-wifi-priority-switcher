package com.claude.wifipriority;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 儲存使用者設定：優先清單、開關、門檻、紀錄。 */
final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("wifi_priority", Context.MODE_PRIVATE);
    }

    static List<String> getOrder(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("order", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {}
        return out;
    }

    static void setOrder(Context c, List<String> order) {
        JSONArray a = new JSONArray();
        for (String s : order) a.put(s);
        sp(c).edit().putString("order", a.toString()).apply();
    }

    static boolean isEnabled(Context c) { return sp(c).getBoolean("enabled", false); }
    static void setEnabled(Context c, boolean v) { sp(c).edit().putBoolean("enabled", v).apply(); }

    /** 目標網路訊號至少要達到這個 dBm 才會切過去（預設 -75）。 */
    static int getThreshold(Context c) { return sp(c).getInt("threshold", -75); }
    static void setThreshold(Context c, int v) { sp(c).edit().putInt("threshold", v).apply(); }

    /** 目前連的網路不在清單中時，是否也要切到清單中的網路。 */
    static boolean switchFromUnlisted(Context c) { return sp(c).getBoolean("from_unlisted", true); }
    static void setSwitchFromUnlisted(Context c, boolean v) { sp(c).edit().putBoolean("from_unlisted", v).apply(); }

    static long getLastSwitch(Context c) { return sp(c).getLong("last_switch", 0); }
    static String getLastTarget(Context c) { return sp(c).getString("last_target", ""); }
    static void setLastSwitch(Context c, String target) {
        sp(c).edit().putLong("last_switch", System.currentTimeMillis()).putString("last_target", target).apply();
    }

    /** 切換失敗的網路暫時跳過，避免一直重試。 */
    static void markFailed(Context c, String ssid) {
        sp(c).edit().putLong("fail_" + ssid, System.currentTimeMillis()).apply();
    }
    static boolean recentlyFailed(Context c, String ssid) {
        return System.currentTimeMillis() - sp(c).getLong("fail_" + ssid, 0) < 5 * 60_000L;
    }
    static void clearFailed(Context c, String ssid) { sp(c).edit().remove("fail_" + ssid).apply(); }

    static synchronized void log(Context c, String msg) {
        String time = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.TAIWAN).format(new Date());
        String old = sp(c).getString("log", "");
        String[] lines = old.isEmpty() ? new String[0] : old.split("\n");
        StringBuilder sb = new StringBuilder(time).append("  ").append(msg);
        for (int i = 0; i < lines.length && i < 39; i++) sb.append('\n').append(lines[i]);
        sp(c).edit().putString("log", sb.toString()).apply();
    }

    static String getLog(Context c) { return sp(c).getString("log", ""); }
    static void clearLog(Context c) { sp(c).edit().remove("log").apply(); }
}
