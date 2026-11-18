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
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** 常駐背景服務：定期掃描並依優先順序切換 Wi-Fi。 */
@SuppressWarnings("deprecation")
public class WifiPriorityService extends Service {
    private static final String CHANNEL = "status";
    private static final int NOTIF_ID = 1;
    private static final long TICK_MS = 20_000L;   // 每 20 秒檢查一次
    private static final int SCAN_EVERY = 3;       // 每 3 次（約 60 秒）主動掃描一次

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int tick = 0;
    private String lastText = "";

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (tick++ % SCAN_EVERY == 0) {
                try { WifiHelper.wm(WifiPriorityService.this).startScan(); } catch (Exception ignored) {}
            }
            check();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private final Runnable debounced = this::check;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(debounced);
            handler.postDelayed(debounced, 2_000L);
        }
    };

    static void start(Context c) {
        Intent i = new Intent(c, WifiPriorityService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i);
        else c.startService(i);
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, WifiPriorityService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, L.t(this, "Wi-Fi 優先順序狀態", "Wi-Fi Priority status"), NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);

        Notification n = build(L.t(this, "啟動中…", "Starting…"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIF_ID, n);
        }

        IntentFilter f = new IntentFilter();
        f.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(receiver, f);
        handler.post(loop);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.isEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void check() {
        String status;
        try {
            status = WifiHelper.evaluate(this, true);
        } catch (Exception e) {
            status = L.t(this, "發生錯誤：", "Error: ") + e.getMessage();
        }
        if (!status.equals(lastText)) {
            lastText = status;
            getSystemService(NotificationManager.class).notify(NOTIF_ID, build(status));
        }
    }

    private Notification build(String text) {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        String first = text.contains("\n") ? text.substring(0, text.indexOf('\n')) : text;
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(L.t(this, "Wi-Fi 優先順序：自動切換中", "Wi-Fi Priority: auto-switch on"))
                .setContentText(first)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
