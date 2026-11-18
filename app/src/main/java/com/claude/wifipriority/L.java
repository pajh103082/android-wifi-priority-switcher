package com.claude.wifipriority;

import android.content.Context;

import java.util.Locale;

/**
 * 簡易雙語工具（繁中／英文）。
 * 用法：L.t(context, "中文", "English")。
 * 語言設定存在 Prefs（auto / zh / en）；auto＝手機語言是中文就顯示中文，其他語言一律英文。
 * 新增文字時，一律中英兩種都要寫。
 */
final class L {
    private L() {}

    static final String AUTO = "auto";
    static final String ZH = "zh";
    static final String EN = "en";

    static boolean isEnglish(Context c) {
        String mode = Prefs.getLang(c);
        if (EN.equals(mode)) return true;
        if (ZH.equals(mode)) return false;
        return !"zh".equals(Locale.getDefault().getLanguage());
    }

    static String t(Context c, String zh, String en) {
        return isEnglish(c) ? en : zh;
    }
}
