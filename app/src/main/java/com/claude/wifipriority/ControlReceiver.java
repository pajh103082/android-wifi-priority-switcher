package com.claude.wifipriority;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 通知列按鈕：「停用自動切換」。 */
public class ControlReceiver extends BroadcastReceiver {
    static final String ACTION_DISABLE = "com.claude.wifipriority.DISABLE";

    @Override public void onReceive(Context context, Intent intent) {
        if (!ACTION_DISABLE.equals(intent.getAction())) return;
        Prefs.setEnabled(context, false);
        Prefs.log(context, L.t(context, "已從通知列停用自動切換", "Auto-switch turned off from the notification"));
        WifiPriorityService.stop(context);
    }
}
