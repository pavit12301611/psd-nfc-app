package com.psdnfcapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;

/**
 * Restarts the foreground service after reboot so NFC stays active.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && PrefHelper.getNfcAlways(context)) {
            NfcForegroundService.startIfNeeded(context);
        }
    }
}
