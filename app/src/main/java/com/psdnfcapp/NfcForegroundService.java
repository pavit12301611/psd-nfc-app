package com.psdnfcapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/**
 * Foreground service that keeps the app "always active":
 * - Keeps the NFC adapter enabled (re-tries every 30s)
 * - Shows a persistent notification so the system doesn't kill the process
 * - Started on boot via BootReceiver when "NFC always ON" is chosen
 */
public class NfcForegroundService extends Service {
    private static final int NOTIF_ID = 1001;
    private static final String CHANNEL = "psdnfc_nfc_channel";
    private static final String ACTION_STOP = "STOP";

    private NfcAdapter nfcAdapter;
    private Handler keepAliveHandler;
    private Runnable keepAliveTask;

    @Override
    public void onCreate() {
        super.onCreate();
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        createNotificationChannel();
        keepAliveHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIF_ID, buildNotification());
        ensureNfcOn();
        scheduleKeepAlive();
        return START_STICKY;
    }

    private Notification buildNotification() {
        Context ctx = this;
        Intent stop = new Intent(ctx, NfcForegroundService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(ctx, 0, stop,
                PendingIntent.FLAG_IMMUTABLE);
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(ctx, 0, open,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(ctx.getString(R.string.app_name))
                .setContentText(ctx.getString(R.string.nfc_active))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(android.R.drawable.ic_input_delete, ctx.getString(R.string.stop), stopPi)
                .setContentIntent(openPi)
                .build();
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription(getString(R.string.channel_desc));
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(ch);
            }
        }
    }

    private void ensureNfcOn() {
        if (nfcAdapter != null && !nfcAdapter.isEnabled()) {
            try {
                nfcAdapter.enable();
            } catch (Exception e) {
                // System toggle may block this; user must keep NFC on via quick settings.
                android.util.Log.w("NfcForegroundService", "Could not auto-enable NFC", e);
            }
        }
    }

    private void scheduleKeepAlive() {
        if (keepAliveTask != null) {
            return;
        }
        keepAliveTask = new Runnable() {
            @Override
            public void run() {
                ensureNfcOn();
                keepAliveHandler.postDelayed(this, 30000);
            }
        };
        keepAliveHandler.postDelayed(keepAliveTask, 30000);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
        if (keepAliveHandler != null && keepAliveTask != null) {
            keepAliveHandler.removeCallbacks(keepAliveTask);
            keepAliveTask = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void startIfNeeded(Context context) {
        ContextCompat.startForegroundService(context,
                new Intent(context, NfcForegroundService.class));
    }
}
