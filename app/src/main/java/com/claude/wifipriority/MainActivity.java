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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
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
    private Button powerButton, intervalButton;
    private TextView timingView;
    private int tickCount = 0;

    /** 每秒更新倒數；每 4 秒重畫整個畫面（清單訊號、狀態、紀錄）。 */
    private final Runnable autoRefresh = new Runnable() {
        @Override public void run() {
            if (tickCount++ % 4 == 0) refresh(); else updateTiming();
            handler.postDelayed(this, 1_000L);
        }
    };

    // ---------- 版面 ----------

    /** 依目前語言回傳中文或英文。 */
    private String t(String zh, String en) { return L.t(this, zh, en); }

    /** 語言選擇：跟隨系統／中文／English。標題永遠雙語，切錯語言也找得到。 */
    private View languagePicker() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(14), 0, 0);
        box.addView(text("語言 Language", 16, true));
        RadioGroup g = new RadioGroup(this);
        g.setOrientation(RadioGroup.HORIZONTAL);
        String[][] opts = {{L.AUTO, t("跟隨系統", "System")}, {L.ZH, "中文"}, {L.EN, "English"}};
        String current = Prefs.getLang(this);
        for (int i = 0; i < opts.length; i++) {
            RadioButton r = new RadioButton(this);
            r.setId(View.generateViewId());
            r.setText(opts[i][1]);
            r.setTextSize(16);
            r.setTag(opts[i][0]);
            g.addView(r);
            if (opts[i][0].equals(current)) r.setChecked(true);
        }
        g.setOnCheckedChangeListener((group, id) -> {
            RadioButton r = group.findViewById(id);
            if (r == null || r.getTag().equals(Prefs.getLang(this))) return;
            Prefs.setLang(this, (String) r.getTag());
            recreate();
        });
        box.addView(g);
        return box;
    }

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
        setTitle(t("Wi-Fi 優先順序", "Wi-Fi Priority"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(24));
        root.setBackgroundColor(0xFFF1F4F8);

        // 最上方：啟用／停用大按鈕
        powerButton = new Button(this);
        powerButton.setAllCaps(false);
        powerButton.setTextSize(20);
        powerButton.setTextColor(Color.WHITE);
        powerButton.setPadding(dp(16), dp(18), dp(16), dp(18));
        powerButton.setOnClickListener(v -> setEnabled(!Prefs.isEnabled(this)));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.setMargins(0, 0, 0, dp(12));
        root.addView(powerButton, plp);

        // 警告區（權限、定位）
        warnView = text("", 16, false);
        warnView.setTextColor(0xFF8A4B00);
        warnButton = button(t("前往設定", "Open settings"), v -> fixWarning());
        View warnCard = card(warnView, warnButton);
        ((GradientDrawable) warnCard.getBackground()).setColor(0xFFFFF3CD);
        warnCard.setTag("warn");
        root.addView(warnCard);

        // 狀態 + 開關
        statusView = text("", 18, true);
        timingView = text("", 15, false);
        timingView.setTextColor(0xFF1565C0);
        timingView.setPadding(0, dp(8), 0, dp(4));
        Button checkNow = button(t("立即掃描並檢查", "Scan and check now"), v -> checkNow());
        root.addView(card(text(t("目前狀態", "Status"), 15, false), statusView, timingView, checkNow));

        // 優先清單
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout addRow = new LinearLayout(this);
        addRow.addView(button(t("＋ 從附近／已儲存加入", "＋ Nearby / saved"), v -> showAddPicker()), new LinearLayout.LayoutParams(0, -2, 1));
        addRow.addView(button(t("＋ 手動輸入", "＋ Enter manually"), v -> showManualAdd("")), new LinearLayout.LayoutParams(0, -2, 1));
        TextView hint = text(t("越上面越優先。用 ▲ ▼ 調整順序。", "Top = highest priority. Use ▲ ▼ to reorder."), 14, false);
        hint.setTextColor(0xFF666666);
        root.addView(card(text(t("優先順序清單", "Priority list"), 20, true), hint, listBox, addRow));

        // 設定
        intervalButton = button("", v -> showIntervalPicker());
        TextView intervalHint = text(t("多久檢查一次要不要切換網路。越頻繁切得越快，但比較耗電。",
                "How often to check whether to switch. More often = faster switching but more battery use."), 14, false);
        intervalHint.setTextColor(0xFF666666);
        thresholdLabel = text("", 16, false);
        thresholdLabel.setPadding(0, dp(12), 0, 0);
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
        unlisted.setText(t("目前連的網路不在清單中時，也切到清單裡的網路", "Also switch away when the current network is not in the list"));
        unlisted.setTextSize(16);
        unlisted.setChecked(Prefs.switchFromUnlisted(this));
        unlisted.setOnCheckedChangeListener((v, on) -> Prefs.setSwitchFromUnlisted(this, on));
        Button battery = button(t("讓系統不要限制本 App（避免背景被關）", "Don't restrict this app (keeps it running in background)"), v -> askBatteryWhitelist());
        root.addView(card(text(t("設定", "Settings"), 20, true), intervalButton, intervalHint, thresholdLabel, seek, unlisted, battery, languagePicker()));

        // 紀錄
        logView = text("", 14, false);
        logView.setTextColor(0xFF444444);
        logView.setTextIsSelectable(true);
        Button clear = button(t("清除紀錄", "Clear log"), v -> { Prefs.clearLog(this); refresh(); });
        root.addView(card(text(t("切換紀錄", "Switch log"), 20, true), logView, clear));

        // 贊助
        TextView thanks = text(t("覺得好用的話，可以請作者喝杯咖啡 ☕", "Find it useful? You can buy the developer a coffee ☕"), 16, false);
        Button sponsor = button(t("☕ 贊助作者", "☕ Support the developer"), v -> startActivity(new Intent(this, SponsorActivity.class)));
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

    // ---------- 啟用開關、檢查頻率 ----------

    private void setEnabled(boolean on) {
        Prefs.setEnabled(this, on);
        if (on) {
            WifiPriorityService.start(this);
            Prefs.log(this, t("已開啟自動切換（" , "Auto-switch turned on (")
                    + WifiPriorityService.intervalLabel(this, Prefs.getIntervalSec(this)) + t("）", ")"));
        } else {
            WifiPriorityService.stop(this);
            Prefs.log(this, t("已停用自動切換", "Auto-switch turned off"));
        }
        refresh();
    }

    private void updatePowerButton() {
        boolean on = Prefs.isEnabled(this);
        powerButton.setText(on
                ? t("● 自動切換：開啟中\n點一下停用", "● Auto-switch: ON\nTap to turn off")
                : t("○ 自動切換：已停用\n點一下啟用", "○ Auto-switch: OFF\nTap to turn on"));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(on ? 0xFF2E7D32 : 0xFF757575);
        bg.setCornerRadius(dp(14));
        powerButton.setBackground(bg);
    }

    private String freqText(int sec) {
        if (sec < 60) return t("每 " + sec + " 秒", "every " + sec + " s");
        return t("每 " + (sec / 60) + " 分鐘", "every " + (sec / 60) + " min");
    }

    private void updateTiming() {
        if (timingView == null) return;
        int sec = Prefs.getIntervalSec(this);
        if (intervalButton != null) intervalButton.setText(t("檢查頻率：", "Check frequency: ") + freqText(sec) + "  ▸");
        if (!Prefs.isEnabled(this)) {
            timingView.setText(t("已停用，不會自動切換網路。", "Turned off — the app won't switch networks."));
            return;
        }
        long now = System.currentTimeMillis();
        long next = Prefs.getNextCheck(this);
        long last = Prefs.getLastCheck(this);
        String lastStr = last > 0
                ? new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date(last))
                : t("尚未", "not yet");
        String nextStr;
        if (next <= 0) nextStr = t("啟動中…", "starting…");
        else {
            long left = Math.max(0, (next - now + 999) / 1000);
            nextStr = left >= 60
                    ? t("還有 " + (left / 60) + " 分 " + (left % 60) + " 秒", "in " + (left / 60) + " min " + (left % 60) + " s")
                    : t("還有 " + left + " 秒", "in " + left + " s");
        }
        timingView.setText(t("⏱ " + freqText(sec) + "檢查一次\n上次檢查：" + lastStr + "　下次檢查：" + nextStr,
                "⏱ Checks " + freqText(sec) + "\nLast check: " + lastStr + "   Next check: " + nextStr));
    }

    private void showIntervalPicker() {
        int[] opts = Prefs.INTERVAL_OPTIONS;
        String[] labels = new String[opts.length];
        int current = Prefs.getIntervalSec(this), checked = -1;
        for (int i = 0; i < opts.length; i++) {
            labels[i] = freqText(opts[i]);
            if (opts[i] == 30) labels[i] += t("（建議）", " (recommended)");
            if (opts[i] == current) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(t("多久檢查一次？", "How often to check?"))
                .setSingleChoiceItems(labels, checked, (d, w) -> {
                    Prefs.setIntervalSec(this, opts[w]);
                    Prefs.log(this, t("檢查頻率改為", "Check frequency set to ") + freqText(opts[w]));
                    WifiPriorityService.reschedule(this);
                    d.dismiss();
                    refresh();
                })
                .setNegativeButton(t("取消", "Cancel"), null)
                .show();
    }

    private void updateThresholdLabel() {
        int t = Prefs.getThreshold(this);
        String w = WifiHelper.signalWord(this, t);
        thresholdLabel.setText(t("切換門檻：目標訊號要強於 " + t + " dBm（" + w + "）才切過去。\n往右＝更挑剔，往左＝訊號弱也切。",
                "Switch threshold: only switch when the target is stronger than " + t + " dBm (" + w + ").\nRight = pickier, left = switch even on weak signal."));
    }

    // ---------- 畫面更新 ----------

    private void refresh() {
        // 警告
        String warn = null;
        if (!WifiHelper.hasLocationPermission(this)) {
            warn = t("⚠ 需要「位置」權限，並選「精確位置」。Android 規定要有這個權限才能看到附近的 Wi-Fi 名稱。", "⚠ Location permission (\"Precise\") is required. Android only shows nearby Wi-Fi names to apps that have it.");
        } else if (!WifiHelper.isLocationOn(this)) {
            warn = t("⚠ 手機的「定位」是關的。請打開，否則 App 看不到附近的 Wi-Fi。", "⚠ Location is turned off. Please turn it on, otherwise the app can't see nearby Wi-Fi.");
        } else if (Prefs.isEnabled(this) && !WifiHelper.hasBackgroundLocation(this)) {
            warn = t("建議：把位置權限改成「一律允許」，關掉螢幕後才能持續自動切換。", "Tip: set location permission to \"Allow all the time\" so auto-switch keeps working with the screen off.");
        }
        View warnCard = getWindow().getDecorView().findViewWithTag("warn");
        if (warnCard != null) warnCard.setVisibility(warn == null ? View.GONE : View.VISIBLE);
        if (warn != null) warnView.setText(warn);

        statusView.setText(WifiHelper.evaluate(this, false));
        updatePowerButton();
        updateTiming();

        // 清單
        List<String> order = Prefs.getOrder(this);
        Map<String, Integer> scan = WifiHelper.scanMap(this);
        String cur = WifiHelper.currentSsid(this);
        listBox.removeAllViews();
        if (order.isEmpty()) {
            TextView empty = text(t("（還沒有網路，按下方按鈕加入）", "(No networks yet — add one with the buttons below)"), 16, false);
            empty.setPadding(0, dp(10), 0, dp(10));
            listBox.addView(empty);
        }
        for (int i = 0; i < order.size(); i++) listBox.addView(row(order, i, scan, cur));

        String log = Prefs.getLog(this);
        logView.setText(log.isEmpty() ? t("（尚無紀錄）", "(no entries yet)") : log);
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
        TextView name = text(ssid + (ssid.equals(cur) ? t("  ● 已連線", "  ● connected") : ""), 18, true);
        if (ssid.equals(cur)) name.setTextColor(0xFF2E7D32);
        Integer lv = scan.get(ssid);
        String sig = lv == null ? t("不在附近", "not in range")
                : t("訊號 " + WifiHelper.signalWord(this, lv) + "（" + lv + " dBm）", "Signal " + WifiHelper.signalWord(this, lv) + " (" + lv + " dBm)");
        if (Prefs.recentlyFailed(this, ssid)) sig += t("・剛才連線失敗", " · last attempt failed");
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
                .setMessage(t("把「" + ssid + "」從優先清單移除？\n（不會刪除手機裡儲存的 Wi-Fi）", "Remove \"" + ssid + "\" from the priority list?\n(The Wi-Fi saved on your phone is not deleted.)"))
                .setPositiveButton(t("移除", "Remove"), (d, w) -> {
                    List<String> order = Prefs.getOrder(this);
                    order.remove(ssid);
                    Prefs.setOrder(this, order);
                    refresh();
                })
                .setNegativeButton(t("取消", "Cancel"), null)
                .show();
    }

    private void addToList(String ssid) {
        List<String> order = Prefs.getOrder(this);
        if (!order.contains(ssid)) order.add(ssid);
        Prefs.setOrder(this, order);
        Prefs.clearFailed(this, ssid);
        refresh();
        Toast.makeText(this, t("已加入「" + ssid + "」", "Added \"" + ssid + "\""), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, t("找不到可加入的網路。可以按「手動輸入」。", "No networks found to add. Try \"Enter manually\"."), Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[names.size()];
        for (int i = 0; i < names.size(); i++) {
            String s = names.get(i);
            Integer lv = scan.get(s);
            labels[i] = s + "\n    " + (saved.contains(s) ? t("已儲存", "saved") : t("未儲存（需輸入密碼）", "not saved (password needed)"))
                    + " · " + (lv == null ? t("不在附近", "not in range") : t("訊號", "signal ") + WifiHelper.signalWord(this, lv));
        }
        new AlertDialog.Builder(this)
                .setTitle(t("選擇要加入的網路", "Choose a network to add"))
                .setItems(labels, (d, w) -> {
                    String s = names.get(w);
                    if (WifiHelper.findNetId(this, s) >= 0) addToList(s);
                    else showManualAdd(s);
                })
                .setNegativeButton(t("取消", "Cancel"), null)
                .show();
    }

    private void showManualAdd(String presetSsid) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText ssid = new EditText(this);
        ssid.setHint(t("Wi-Fi 名稱（SSID）", "Wi-Fi name (SSID)"));
        ssid.setText(presetSsid);
        ssid.setSingleLine();
        EditText pw = new EditText(this);
        pw.setHint(t("密碼（沒有密碼就留空）", "Password (leave empty if none)"));
        pw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        pw.setSingleLine();
        CheckBox hidden = new CheckBox(this);
        hidden.setText(t("這是隱藏的網路", "This is a hidden network"));
        box.addView(ssid);
        box.addView(pw);
        box.addView(hidden);
        if (!presetSsid.isEmpty()) pw.requestFocus();

        new AlertDialog.Builder(this)
                .setTitle(t("新增 Wi-Fi", "Add Wi-Fi"))
                .setView(box)
                .setPositiveButton(t("加入", "Add"), (d, w) -> {
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
                                .setMessage(t("系統不接受這組設定。\n若這是 WPA3 或企業網路，請先到手機的 Wi-Fi 設定連過一次，再回來加入。", "Android rejected these settings.\nIf this is a WPA3 or enterprise network, connect to it once in the phone's Wi-Fi settings, then come back and add it."))
                                .setPositiveButton(t("好", "OK"), null).show();
                    } else {
                        Prefs.log(this, t("已新增網路設定「" + s + "」", "Added network settings for \"" + s + "\""));
                        addToList(s);
                    }
                })
                .setNegativeButton(t("取消", "Cancel"), null)
                .show();
    }

    private void checkNow() {
        try { WifiHelper.wm(this).startScan(); } catch (Exception ignored) {}
        statusView.setText(t("掃描中…", "Scanning…"));
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
            Toast.makeText(this, t("請點「權限」→「位置」→「一律允許」", "Tap Permissions → Location → Allow all the time"), Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, t("已經設定好了 ✓", "Already set ✓"), Toast.LENGTH_SHORT).show();
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
