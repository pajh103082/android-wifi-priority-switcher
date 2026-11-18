package com.claude.wifipriority;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 贊助頁面：說明 + 前往 Ko-fi 的按鈕。 */
public class SponsorActivity extends Activity {
    static final String KOFI_URL = "https://ko-fi.com/sagiriben";

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(String s, float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(0xFF212121);
        t.setLineSpacing(dp(3), 1f);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle(L.t(this, "贊助作者", "Support the developer"));
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(32));
        root.setBackgroundColor(0xFFF1F4F8);

        TextView cup = text("☕", 64, false);
        cup.setGravity(Gravity.CENTER);
        root.addView(cup);

        TextView title = text(L.t(this, "請作者喝杯咖啡", "Buy me a coffee"), 26, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(16));
        root.addView(title);

        TextView body = text(L.t(this,
                "這個 App 完全免費、沒有廣告，也不會收集你的任何資料。\n\n"
                + "如果它幫你省了每天手動切 Wi-Fi 的麻煩，歡迎透過 Ko-fi 小額贊助，"
                + "支持我繼續維護和加新功能。\n\n"
                + "不贊助也完全沒關係，所有功能都一樣能用 🙂",
                "This app is completely free, has no ads, and collects none of your data.\n\n"
                + "If it saves you from switching Wi-Fi by hand, a small tip on Ko-fi "
                + "helps me keep maintaining it and adding features.\n\n"
                + "No pressure at all — every feature works the same either way 🙂"), 18, false);
        root.addView(body);

        Button go = new Button(this);
        go.setText(L.t(this, "☕  到 Ko-fi 贊助", "☕  Support on Ko-fi"));
        go.setTextSize(20);
        go.setAllCaps(false);
        go.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFF5E5B); // Ko-fi 品牌紅
        bg.setCornerRadius(dp(28));
        go.setBackground(bg);
        go.setPadding(dp(24), dp(14), dp(24), dp(14));
        go.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(KOFI_URL)));
            } catch (Exception e) {
                Toast.makeText(this, L.t(this, "找不到瀏覽器，請手動開啟：", "No browser found. Please open: ") + KOFI_URL, Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(28), 0, dp(12));
        root.addView(go, lp);

        TextView url = text(KOFI_URL, 15, false);
        url.setGravity(Gravity.CENTER);
        url.setTextColor(0xFF666666);
        url.setTextIsSelectable(true);
        root.addView(url);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    @Override public boolean onNavigateUp() {
        finish();
        return true;
    }
}
