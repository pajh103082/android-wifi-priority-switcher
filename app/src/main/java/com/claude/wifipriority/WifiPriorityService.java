package com.claude.wifipriority;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 常駐背景服務：依使用者設定的「檢查頻率」定期掃描並依優先順序切換 Wi-Fi。
 * 只在「自動切換」啟用時存在；停用（App 內大按鈕或通知列「停用」）就會完全停止並移除通知。
 */
@SuppressWarnings("deprecation")
public class WifiPriorityService extends Service {
    static final String ACTION_RESCHEDULE = "com.claude.wifipriority.RESCHEDULE";

    private static final String CHANNEL = "status";
    private static final int NOTIF_ID = 1;
    /** Android 前景 App 掃描上限約 2 分鐘 4 次，所以主動掃描最多每 30 秒一次。 */
    private static final long MIN_SCAN_GAP_MS = 30_000L;
    /** 主動掃描後等多久再判斷（讓掃描結果出來）。 */
    private static final long SCAN_WAIT_MS = 4_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastScanAt = 0;
    private String lastText = "";

    private final Runnable checkRunnable = this::check;

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            long interval = Prefs.getIntervalMs(WifiPriorityService.this);
            long now = System.currentTimeMillis();
            Prefs.setNextCheck(WifiPriorityService.this, now + interval);
            if (now - lastScanAt >= MIN_SCAN_GAP_MS) {
                lastScanAt = now;
                try { WifiHelper.wm(WifiPriorityService.this).startScan(); } catch (Exception ignored) {}
                handler.postDelayed(checkRunnable, SCAN_WAIT_MS);
            } else {
                check(); // 間隔比 30 秒短時，沿用最近一次掃描結果
            }
            handler.postDelayed(this, interval);
        }
    };

    /** 只在 Wi-Fi 斷線或剛打開時提前檢查一次；平常一律照使用者設定的頻率。 */
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            boolean urgent = false;
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                NetworkInfo ni = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                urgent = ni != null && ni.getState() == NetworkInfo.State.DISCONNECTED;
            } else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                urgent = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1) == WifiManager.WIFI_STATE_ENABLED;
            }
            if (urgent) {
                handler.removeCallbacks(checkRunnable);
                handler.postDelayed(checkRunnable, 3_000L);
            }
        }
    };

    static void start(Context c) {
        startWith(c, null);
    }

    /** 檢查頻率改了：讓服務立刻用新頻率重新排程。 */
    static void reschedule(Context c) {
        if (Prefs.isEnabled(c)) startWith(c, ACTION_RESCHEDULE);
    }

    private static void startWith(Context c, String action) {
        Intent i = new Intent(c, WifiPriorityService.class);
        if (action != null) i.setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i);
        else c.startService(i);
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, WifiPriorityService.class));
        Prefs.setNextCheck(c, 0);
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL,
                L.t(this, "Wi-Fi 優先順序狀態", "Wi-Fi Priority status"), NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        goForeground(L.t(this, "啟動中…", "Starting…"));

        IntentFilter f = new IntentFilter();
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(receiver, f);
        handler.post(loop);
    }

    private void goForeground(String text) {
        Notification n = build(text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // 每次 startForegroundService 都要再呼叫一次 startForeground，否則系統可能判定逾時
        goForeground(lastText.isEmpty() ? L.t(this, "啟動中…", "Starting…") : lastText);
        if (!Prefs.isEnabled(this)) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_RESCHEDULE.equals(intent.getAction())) {
            handler.removeCallbacks(loop);
            handler.removeCallbacks(checkRunnable);
            handler.post(loop);
        }
        return START_STICKY;
    }

    private void check() {
        if (!Prefs.isEnabled(this)) { stopSelf(); return; }
        String status;
        try {
            status = WifiHelper.evaluate(this, true);
        } catch (Exception e) {
            status = L.t(this, "發生錯誤：", "Error: ") + e.getMessage();
        }
        Prefs.setLastCheck(this, System.currentTimeMillis());
        lastText = status;
        getSystemService(NotificationManager.class).notify(NOTIF_ID, build(status));
    }

    private Notification build(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getBroadcast(this, 1,
                new Intent(this, ControlReceiver.class).setAction(ControlReceiver.ACTION_DISABLE),
                PendingIntent.FLAG_IMMUTABLE);

        String first = text.contains("\n") ? text.substring(0, text.indexOf('\n')) : text;
        long next = Prefs.getNextCheck(this);
        String nextLine = next > 0
                ? L.t(this, "下次檢查 ", "Next check ") + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(next))
                : "";
        String freq = intervalLabel(this, Prefs.getIntervalSec(this));

        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(L.t(this, "自動切換開啟中・", "Auto-switch on · ") + freq)
                .setContentText(first)
                .setStyle(new Notification.BigTextStyle().bigText(text + (nextLine.isEmpty() ? "" : "\n" + nextLine)))
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, L.t(this, "停用自動切換", "Turn off"), stop).build())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    /** 「每 30 秒」「每 5 分鐘」這類文字。 */
    static String intervalLabel(Context c, int sec) {
        if (sec < 60) return L.t(c, "每 " + sec + " 秒檢查", "checks every " + sec + " s");
        int min = sec / 60;
        return L.t(c, "每 " + min + " 分鐘檢查", "checks every " + min + " min");
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        Prefs.setNextCheck(this, 0);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
