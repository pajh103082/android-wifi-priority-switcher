package com.claude.wifipriority;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 開機或 App 更新後，若使用者有開啟自動切換就自動啟動。 */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        if (Prefs.isEnabled(context)) {
            try { WifiPriorityService.start(context); } catch (Exception ignored) {}
        }
    }
}
