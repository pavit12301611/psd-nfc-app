package com.psd.nfcapp;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Punch-mode screen: live reader APDU log and a copyable diagnostic report. */
public class PunchActivity extends Activity {
    private CardStore store;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView txStatus;
    private TextView txCardSummary;
    private TextView txCompatibility;
    private TextView txHceInfo;
    private TextView txLastContact;
    private TextView txLog;
    private Button btnStop;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transmit);
        store = new CardStore(this);

        txStatus = findViewById(R.id.txStatus);
        txCardSummary = findViewById(R.id.txCardSummary);
        txCompatibility = findViewById(R.id.txCompatibility);
        txHceInfo = findViewById(R.id.txHceInfo);
        txLastContact = findViewById(R.id.txLastContact);
        txLog = findViewById(R.id.txLog);
        btnStop = findViewById(R.id.btnStop);

        findViewById(R.id.btnRefreshLog).setOnClickListener(v -> refresh());
        findViewById(R.id.btnClearLog).setOnClickListener(v -> {
            store.clearLog();
            refresh();
        });
        findViewById(R.id.btnCopyLog).setOnClickListener(v -> copyDiagnostics());
        btnStop.setOnClickListener(v -> {
            CardProfile p = store.loadProfile();
            store.setArmed(false);
            HceRegistrar.sync(this, p);
            Toast.makeText(this, "Punch mode stopped", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        HceRegistrar.setPreferred(this, true);
        refresh();
        handler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshRunnable);
        HceRegistrar.setPreferred(this, false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(refreshRunnable);
    }

    private void refresh() {
        CardProfile p = store.loadProfile();
        boolean armed = store.isArmed();
        txStatus.setText(armed ? "● PUNCH MODE ARMED" : "○ PUNCH MODE OFF");
        txStatus.setTextColor(armed ? 0xFF7CFC00 : 0xFF9E9E9E);
        btnStop.setText(armed ? "STOP PUNCH MODE" : "CLOSE");

        if (p == null) {
            txCardSummary.setText("No card registered.");
            txCompatibility.setText("Register a card first.");
            txCompatibility.setTextColor(0xFFFF6B6B);
        } else {
            txCardSummary.setText("Card: " + p.displayName()
                    + "\nUID: " + (TextUtils.isEmpty(p.uidHex) ? "unknown" : p.uidHex)
                    + "\nTech: " + p.techList
                    + "\nMode: " + p.modeLabel()
                    + "\nAIDs: " + p.aidListText()
                    + "\nAPDU rules: " + p.rules.size());
            txCompatibility.setText(p.compatibilityText());
            txCompatibility.setTextColor(p.canEmulate() ? 0xFFFFD166 : 0xFFFF6B6B);
        }

        txHceInfo.setText(HceRegistrar.statusText(this, store));
        long last = store.getLastReaderContact();
        txLastContact.setText("Last reader contact: " + formatTime(last));

        List<String> log = store.getLog();
        if (log.isEmpty()) {
            txLog.setText("No APDUs yet.\n\n"
                    + "If you tap the attendance reader and this stays empty, the reader did not "
                    + "open an ISO-DEP / HCE session. That usually means it is UID-only, MIFARE "
                    + "Classic, MIFARE Ultralight, 125 kHz, barcode/QR, or is selecting an AID "
                    + "this app has not registered. No app-only fix exists for those cases.");
        } else {
            txLog.setText(TextUtils.join("\n", log));
        }
    }

    private void copyDiagnostics() {
        CardProfile p = store.loadProfile();
        List<String> log = store.getLog();
        StringBuilder sb = new StringBuilder();
        sb.append("Aakash ID Punch diagnostics\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append(HceRegistrar.statusText(this, store)).append('\n');
        sb.append("Punch mode: ").append(store.isArmed() ? "ARMED" : "OFF").append('\n');
        sb.append("Last reader contact: ").append(formatTime(store.getLastReaderContact())).append('\n');
        if (p != null) {
            sb.append("Card: ").append(p.displayName()).append('\n');
            sb.append("UID: ").append(p.uidHex).append('\n');
            sb.append("Tech: ").append(p.techList).append('\n');
            sb.append("Mode: ").append(p.modeLabel()).append('\n');
            sb.append("Compatibility: ").append(p.compatibilityText()).append('\n');
            sb.append("AIDs: ").append(p.aidListText()).append('\n');
            sb.append("APDU rules: ").append(p.rules.size()).append('\n');
            for (String note : p.notes) sb.append("Note: ").append(note).append('\n');
        } else {
            sb.append("Card: none registered\n");
        }
        sb.append("\nAPDU log (newest first):\n");
        sb.append(log.isEmpty() ? "(empty)" : TextUtils.join("\n", log)).append('\n');

        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Aakash punch diagnostics", sb.toString()));
            Toast.makeText(this, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private String formatTime(long millis) {
        if (millis <= 0) return "never";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(millis));
    }
}
