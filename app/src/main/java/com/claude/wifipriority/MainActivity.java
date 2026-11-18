package com.claude.wifipriority;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity {
    private static final int REQ_LOC = 1;
    private static final int BLUE = 0xFF1565C0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout listBox;
    private TextView statusView, warnView, logView, thresholdLabel;
    private Button warnButton;
    private Switch enableSwitch;

    private final Runnable autoRefresh = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 4_000L);
        }
    };

    // ---------- 版面 ----------

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(String s, float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(0xFF212121);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(17);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private View card(View... children) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(12));
        box.setBackground(bg);
        box.setElevation(dp(2));
        for (View v : children) box.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        box.setLayoutParams(lp);
        return box;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(24));
        root.setBackgroundColor(0xFFF1F4F8);

        // 警告區（權限、定位）
        warnView = text("", 16, false);
        warnView.setTextColor(0xFF8A4B00);
        warnButton = button("前往設定", v -> fixWarning());
        View warnCard = card(warnView, warnButton);
        ((GradientDrawable) warnCard.getBackground()).setColor(0xFFFFF3CD);
        warnCard.setTag("warn");
        root.addView(warnCard);

        // 狀態 + 開關
        statusView = text("", 18, true);
        enableSwitch = new Switch(this);
        enableSwitch.setText("自動切換到順位最高的 Wi-Fi");
        enableSwitch.setTextSize(18);
        enableSwitch.setPadding(0, dp(10), 0, dp(4));
        enableSwitch.setChecked(Prefs.isEnabled(this));
        enableSwitch.setOnCheckedChangeListener((v, on) -> {
            Prefs.setEnabled(this, on);
            if (on) {
                WifiPriorityService.start(this);
                Prefs.log(this, "已開啟自動切換");
            } else {
                WifiPriorityService.stop(this);
                Prefs.log(this, "已關閉自動切換");
            }
            refresh();
        });
        Button checkNow = button("立即掃描並檢查", v -> checkNow());
        root.addView(card(text("目前狀態", 15, false), statusView, enableSwitch, checkNow));

        // 優先清單
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout addRow = new LinearLayout(this);
        addRow.addView(button("＋ 從附近／已儲存加入", v -> showAddPicker()), new LinearLayout.LayoutParams(0, -2, 1));
        addRow.addView(button("＋ 手動輸入", v -> showManualAdd("")), new LinearLayout.LayoutParams(0, -2, 1));
        TextView hint = text("越上面越優先。用 ▲ ▼ 調整順序。", 14, false);
        hint.setTextColor(0xFF666666);
        root.addView(card(text("優先順序清單", 20, true), hint, listBox, addRow));

        // 設定
        thresholdLabel = text("", 16, false);
        SeekBar seek = new SeekBar(this);
        seek.setMax(40); // -90 ~ -50
        seek.setProgress(Prefs.getThreshold(this) + 90);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user) Prefs.setThreshold(MainActivity.this, p - 90);
                updateThresholdLabel();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { refresh(); }
        });
        updateThresholdLabel();
        CheckBox unlisted = new CheckBox(this);
        unlisted.setText("目前連的網路不在清單中時，也切到清單裡的網路");
        unlisted.setTextSize(16);
        unlisted.setChecked(Prefs.switchFromUnlisted(this));
        unlisted.setOnCheckedChangeListener((v, on) -> Prefs.setSwitchFromUnlisted(this, on));
        Button battery = button("讓系統不要限制本 App（避免背景被關）", v -> askBatteryWhitelist());
        root.addView(card(text("設定", 20, true), thresholdLabel, seek, unlisted, battery));

        // 紀錄
        logView = text("", 14, false);
        logView.setTextColor(0xFF444444);
        logView.setTextIsSelectable(true);
        Button clear = button("清除紀錄", v -> { Prefs.clearLog(this); refresh(); });
        root.addView(card(text("切換紀錄", 20, true), logView, clear));

        // 贊助
        TextView thanks = text("覺得好用的話，可以請作者喝杯咖啡 ☕", 16, false);
        Button sponsor = button("☕ 贊助作者", v -> startActivity(new Intent(this, SponsorActivity.class)));
        sponsor.setTextColor(Color.WHITE);
        GradientDrawable kofi = new GradientDrawable();
        kofi.setColor(0xFFFF5E5B);
        kofi.setCornerRadius(dp(24));
        sponsor.setBackground(kofi);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(0, dp(10), 0, 0);
        sponsor.setLayoutParams(sp);
        root.addView(card(thanks, sponsor));

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);

        if (!WifiHelper.hasLocationPermission(this)) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
        }
        if (Prefs.isEnabled(this)) WifiPriorityService.start(this);
    }

    @Override protected void onResume() {
        super.onResume();
        try { WifiHelper.wm(this).startScan(); } catch (Exception ignored) {}
        handler.post(autoRefresh);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(autoRefresh);
        super.onPause();
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        try { WifiHelper.wm(this).startScan(); } catch (Exception ignored) {}
        refresh();
    }

    private void updateThresholdLabel() {
        int t = Prefs.getThreshold(this);
        thresholdLabel.setText("切換門檻：目標訊號要強於 " + t + " dBm（" + WifiHelper.signalWord(t)
                + "）才切過去。\n往右＝更挑剔，往左＝訊號弱也切。");
    }

    // ---------- 畫面更新 ----------

    private void refresh() {
        // 警告
        String warn = null;
        if (!WifiHelper.hasLocationPermission(this)) {
            warn = "⚠ 需要「位置」權限，並選「精確位置」。Android 規定要有這個權限才能看到附近的 Wi-Fi 名稱。";
        } else if (!WifiHelper.isLocationOn(this)) {
            warn = "⚠ 手機的「定位」是關的。請打開，否則 App 看不到附近的 Wi-Fi。";
        } else if (Prefs.isEnabled(this) && !WifiHelper.hasBackgroundLocation(this)) {
            warn = "建議：把位置權限改成「一律允許」，關掉螢幕後才能持續自動切換。";
        }
        View warnCard = getWindow().getDecorView().findViewWithTag("warn");
        if (warnCard != null) warnCard.setVisibility(warn == null ? View.GONE : View.VISIBLE);
        if (warn != null) warnView.setText(warn);

        statusView.setText(WifiHelper.evaluate(this, false));

        // 清單
        List<String> order = Prefs.getOrder(this);
        Map<String, Integer> scan = WifiHelper.scanMap(this);
        String cur = WifiHelper.currentSsid(this);
        listBox.removeAllViews();
        if (order.isEmpty()) {
            TextView empty = text("（還沒有網路，按下方按鈕加入）", 16, false);
            empty.setPadding(0, dp(10), 0, dp(10));
            listBox.addView(empty);
        }
        for (int i = 0; i < order.size(); i++) listBox.addView(row(order, i, scan, cur));

        String log = Prefs.getLog(this);
        logView.setText(log.isEmpty() ? "（尚無紀錄）" : log);
    }

    private View row(List<String> order, int i, Map<String, Integer> scan, String cur) {
        String ssid = order.get(i);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView num = text(String.valueOf(i + 1), 22, true);
        num.setTextColor(BLUE);
        num.setGravity(Gravity.CENTER);
        row.addView(num, new LinearLayout.LayoutParams(dp(34), -2));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(ssid + (ssid.equals(cur) ? "  ● 已連線" : ""), 18, true);
        if (ssid.equals(cur)) name.setTextColor(0xFF2E7D32);
        Integer lv = scan.get(ssid);
        String sig = lv == null ? "不在附近" : "訊號 " + WifiHelper.signalWord(lv) + "（" + lv + " dBm）";
        if (Prefs.recentlyFailed(this, ssid)) sig += "・剛才連線失敗";
        TextView sub = text(sig, 14, false);
        sub.setTextColor(lv == null ? 0xFF999999 : 0xFF555555);
        info.addView(name);
        info.addView(sub);
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

        row.addView(small("▲", i > 0, v -> move(i, -1)));
        row.addView(small("▼", i < order.size() - 1, v -> move(i, 1)));
        row.addView(small("✕", true, v -> confirmRemove(ssid)));
        return row;
    }

    private Button small(String s, boolean enabled, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(18);
        b.setMinWidth(0);
        b.setMinimumWidth(dp(48));
        b.setEnabled(enabled);
        b.setOnClickListener(l);
        return b;
    }

    // ---------- 動作 ----------

    private void move(int i, int d) {
        List<String> order = Prefs.getOrder(this);
        int j = i + d;
        if (j < 0 || j >= order.size()) return;
        Collections.swap(order, i, j);
        Prefs.setOrder(this, order);
        refresh();
    }

    private void confirmRemove(String ssid) {
        new AlertDialog.Builder(this)
                .setMessage("把「" + ssid + "」從優先清單移除？\n（不會刪除手機裡儲存的 Wi-Fi）")
                .setPositiveButton("移除", (d, w) -> {
                    List<String> order = Prefs.getOrder(this);
                    order.remove(ssid);
                    Prefs.setOrder(this, order);
                    refresh();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void addToList(String ssid) {
        List<String> order = Prefs.getOrder(this);
        if (!order.contains(ssid)) order.add(ssid);
        Prefs.setOrder(this, order);
        Prefs.clearFailed(this, ssid);
        refresh();
        Toast.makeText(this, "已加入「" + ssid + "」", Toast.LENGTH_SHORT).show();
    }

    private void showAddPicker() {
        List<String> order = Prefs.getOrder(this);
        Set<String> saved = WifiHelper.savedSsids(this);
        Map<String, Integer> scan = WifiHelper.scanMap(this);

        List<String> names = new ArrayList<>();
        for (String s : scan.keySet()) if (!order.contains(s)) names.add(s);
        for (String s : saved) if (!order.contains(s) && !names.contains(s)) names.add(s);
        names.sort((a, b) -> {
            int la = scan.containsKey(a) ? scan.get(a) : -200;
            int lb = scan.containsKey(b) ? scan.get(b) : -200;
            if (la != lb) return lb - la;
            return a.compareToIgnoreCase(b);
        });
        if (names.isEmpty()) {
            Toast.makeText(this, "找不到可加入的網路。可以按「手動輸入」。", Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[names.size()];
        for (int i = 0; i < names.size(); i++) {
            String s = names.get(i);
            Integer lv = scan.get(s);
            labels[i] = s + "\n    " + (saved.contains(s) ? "已儲存" : "未儲存（需輸入密碼）")
                    + "・" + (lv == null ? "不在附近" : "訊號" + WifiHelper.signalWord(lv));
        }
        new AlertDialog.Builder(this)
                .setTitle("選擇要加入的網路")
                .setItems(labels, (d, w) -> {
                    String s = names.get(w);
                    if (WifiHelper.findNetId(this, s) >= 0) addToList(s);
                    else showManualAdd(s);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showManualAdd(String presetSsid) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText ssid = new EditText(this);
        ssid.setHint("Wi-Fi 名稱（SSID）");
        ssid.setText(presetSsid);
        ssid.setSingleLine();
        EditText pw = new EditText(this);
        pw.setHint("密碼（沒有密碼就留空）");
        pw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        pw.setSingleLine();
        CheckBox hidden = new CheckBox(this);
        hidden.setText("這是隱藏的網路");
        box.addView(ssid);
        box.addView(pw);
        box.addView(hidden);
        if (!presetSsid.isEmpty()) pw.requestFocus();

        new AlertDialog.Builder(this)
                .setTitle("新增 Wi-Fi")
                .setView(box)
                .setPositiveButton("加入", (d, w) -> {
                    String s = ssid.getText().toString().trim();
                    if (s.isEmpty()) return;
                    if (WifiHelper.findNetId(this, s) >= 0) {
                        // 系統已有這個網路的設定，直接用
                        addToList(s);
                        return;
                    }
                    int id = WifiHelper.addNetwork(this, s, pw.getText().toString(), hidden.isChecked());
                    if (id == -1) {
                        new AlertDialog.Builder(this)
                                .setMessage("系統不接受這組設定。\n若這是 WPA3 或企業網路，請先到手機的 Wi-Fi 設定連過一次，再回來加入。")
                                .setPositiveButton("好", null).show();
                    } else {
                        Prefs.log(this, "已新增網路設定「" + s + "」");
                        addToList(s);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void checkNow() {
        try { WifiHelper.wm(this).startScan(); } catch (Exception ignored) {}
        statusView.setText("掃描中…");
        handler.postDelayed(() -> {
            String s = WifiHelper.evaluate(this, true);
            Toast.makeText(this, s, Toast.LENGTH_LONG).show();
            refresh();
        }, 3_000L);
    }

    private void fixWarning() {
        if (!WifiHelper.hasLocationPermission(this)) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                    || getSharedPreferences("ui", MODE_PRIVATE).getInt("asked", 0) < 2) {
                getSharedPreferences("ui", MODE_PRIVATE).edit()
                        .putInt("asked", getSharedPreferences("ui", MODE_PRIVATE).getInt("asked", 0) + 1).apply();
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
                return;
            }
            openAppSettings();
        } else if (!WifiHelper.isLocationOn(this)) {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } else {
            Toast.makeText(this, "請點「權限」→「位置」→「一律允許」", Toast.LENGTH_LONG).show();
            openAppSettings();
        }
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private void askBatteryWhitelist() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, "已經設定好了 ✓", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }
}
