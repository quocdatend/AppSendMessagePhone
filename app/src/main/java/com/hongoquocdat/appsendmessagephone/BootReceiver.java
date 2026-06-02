package com.hongoquocdat.appsendmessagephone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent serviceIntent = new Intent(context, SMSForwarderService.class);
            // Bug #2: Dùng startForegroundService cho API 26+ thay vì startService
            ContextCompat.startForegroundService(context, serviceIntent);
        }
    }
}
