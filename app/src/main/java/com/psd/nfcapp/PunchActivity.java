package com.psd.nfcapp;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Punch-mode screen: live reader/tag log and a copyable diagnostic report. */
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
    private Button btnWriteSticker;

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
        btnWriteSticker = findViewById(R.id.btnWriteSticker);

        findViewById(R.id.btnRefreshLog).setOnClickListener(v -> refresh());
        findViewById(R.id.btnClearLog).setOnClickListener(v -> {
            store.clearLog();
            refresh();
        });
        findViewById(R.id.btnCopyLog).setOnClickListener(v -> copyDiagnostics());
        btnWriteSticker.setOnClickListener(v -> openCredentialWriter());
        btnStop.setOnClickListener(v -> {
            CardProfile p = store.loadProfile();
            store.setArmed(false);
            HceRegistrar.sync(this, p);
            boolean type2 = p != null && CardProfile.MODE_TYPE2_NDEF.equals(p.mode);
            Toast.makeText(this, type2 ? "Type-4 fallback stopped" : "Punch mode stopped",
                    Toast.LENGTH_SHORT).show();
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

    private void openCredentialWriter() {
        CardProfile p = store.loadProfile();
        if (p == null) {
            Toast.makeText(this, "Register your physical card first", Toast.LENGTH_SHORT).show();
            return;
        }
        Class<?> target;
        if (CardProfile.MODE_MIFARE_CLASSIC.equals(p.mode)) {
            target = MifareMagicActivity.class;
        } else if (p.canWriteSticker()) {
            target = WriteTagActivity.class;
        } else {
            Toast.makeText(this, "No physical-tag write path for this card", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this, target));
    }

    private void refresh() {
        CardProfile p = store.loadProfile();
        boolean armed = store.isArmed();
        boolean type2 = p != null && CardProfile.MODE_TYPE2_NDEF.equals(p.mode);
        boolean mifare = p != null && CardProfile.MODE_MIFARE_CLASSIC.equals(p.mode);
        if (armed && type2) {
            txStatus.setText("● TYPE-4 FALLBACK ARMED (NOT TYPE-2)");
            txStatus.setTextColor(0xFFFFB74D);
        } else if (armed && mifare) {
            txStatus.setText("● HCE DIAGNOSTICS ARMED (NOT MIFARE CLASSIC)");
            txStatus.setTextColor(0xFFFFB74D);
        } else {
            txStatus.setText(armed ? "● PUNCH MODE ARMED" : "○ PUNCH MODE OFF");
            txStatus.setTextColor(armed ? 0xFF7CFC00 : 0xFF9E9E9E);
        }
        btnStop.setText(armed
                ? (type2 ? "STOP TYPE-4 FALLBACK" : (mifare ? "STOP HCE DIAGNOSTICS" : "STOP PUNCH MODE"))
                : "CLOSE");
        boolean showWriter = p != null && (p.canWriteSticker() || mifare);
        btnWriteSticker.setVisibility(showWriter ? View.VISIBLE : View.GONE);
        if (showWriter) {
            btnWriteSticker.setText(mifare
                    ? "WRITE / EXPORT MIFARE MAGIC CARD DUMP"
                    : "WRITE EXACT NDEF TO NTAG STICKER");
        }

        if (p == null) {
            txCardSummary.setText("No card registered.");
            txCompatibility.setText("Register a card first.");
            txCompatibility.setTextColor(0xFFFF6B6B);
        } else {
            txCardSummary.setText("Card: " + p.displayName()
                    + "\nUID: " + (TextUtils.isEmpty(p.uidHex) ? "unknown" : p.uidHex)
                    + "\nTech: " + p.techList
                    + "\nMode: " + p.modeLabel()
                    + "\nNDEF text: " + p.ndefTextListText()
                    + "\nAakash ID: " + (TextUtils.isEmpty(p.aakashId) ? "not found" : p.aakashId)
                    + "\nExact NDEF: " + (TextUtils.isEmpty(p.ndefHex) ? "no" : p.ndefHex.length() / 2 + " bytes")
                    + (mifare ? "\nMIFARE dump: " + (p.mifareDumpComplete ? "COMPLETE " : "PARTIAL ")
                    + p.mifareReadableSectors + "/" + p.mifareSectorCount + " sectors, "
                    + p.mifareSectorKeys.size() + " key entries"
                    + "\nBlock 0: " + p.mifareBlock0Hex
                    + "\nKeys: " + p.mifareKeyMapText() : "")
                    + "\nAIDs: " + p.aidListText()
                    + "\nAPDU rules: " + p.rules.size()
                    + "\nRecommended: " + p.recommendedActionText());
            txCompatibility.setText(p.compatibilityText());
            txCompatibility.setTextColor(p.canEmulate() ? 0xFFFFD166 : 0xFFFF6B6B);
        }

        txHceInfo.setText(HceRegistrar.statusText(this, store));
        long last = store.getLastReaderContact();
        txLastContact.setText("Last reader / tag contact: " + formatTime(last));

        List<String> log = store.getLog();
        if (log.isEmpty()) {
            txLog.setText("No APDUs or tag writes yet.\n\n"
                    + "A MIFARE Classic reader will ALWAYS look empty here: it never reaches "
                    + "HostApduService. Use WRITE / EXPORT MIFARE MAGIC CARD DUMP. "
                    + "MIFARE_WRITE / MIFARE_VERIFY lines appear after using the magic-card writer. "
                    + "TAG_WRITE / TAG_VERIFY lines are for Type-2/NTAG stickers only.");
        } else {
            txLog.setText(TextUtils.join("\n", log));
        }
    }

    private void copyDiagnostics() {
        CardProfile p = store.loadProfile();
        List<String> log = store.getLog();
        StringBuilder sb = new StringBuilder();
        sb.append("Aakash Saharanpur ID diagnostics\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append(HceRegistrar.statusText(this, store)).append('\n');
        sb.append("Punch mode: ").append(store.isArmed() ? "ARMED" : "OFF").append('\n');
        sb.append("Last reader / tag contact: ")
                .append(formatTime(store.getLastReaderContact())).append('\n');
        sb.append("Saharanpur centre: ").append(BranchInfo.CENTRE_NAME).append('\n');
        sb.append("Public AIATS code: ").append(BranchInfo.AIATS_CENTRE_CODE)
                .append(" (not confirmed NFC branch ID)\n");
        if (p != null) {
            sb.append("Card: ").append(p.displayName()).append('\n');
            sb.append("UID: ").append(p.uidHex).append('\n');
            sb.append("Tech: ").append(p.techList).append('\n');
            sb.append("Mode: ").append(p.modeLabel()).append('\n');
            sb.append("NDEF text: ").append(p.ndefTextListText()).append('\n');
            sb.append("Aakash identifier: ").append(p.aakashId).append('\n');
            sb.append("Possible PSID: ").append(p.psidGuess).append('\n');
            sb.append("Expected branch prefix: ").append(p.expectedBranchPrefix).append('\n');
            sb.append("Exact NDEF bytes: ")
                    .append(TextUtils.isEmpty(p.ndefHex) ? "0" : String.valueOf(p.ndefHex.length() / 2))
                    .append('\n');
            if (CardProfile.MODE_MIFARE_CLASSIC.equals(p.mode)) {
                sb.append("MIFARE size: ").append(p.mifareSize).append(" bytes, sectors ")
                        .append(p.mifareSectorCount).append(", blocks ").append(p.mifareBlockCount)
                        .append('\n');
                sb.append("MIFARE dump complete: ").append(p.mifareDumpComplete).append('\n');
                sb.append("MIFARE readable sectors: ").append(p.mifareReadableSectors).append('\n');
                sb.append("MIFARE block 0: ").append(p.mifareBlock0Hex).append('\n');
                sb.append("MIFARE key map:\n").append(p.mifareKeyMapText()).append('\n');
                sb.append("MIFARE dump:\n").append(p.mifareDumpFormatted()).append('\n');
            }
            sb.append("NTAG GET_VERSION: ").append(p.ntagVersionHex).append('\n');
            sb.append("Originality signature: ")
                    .append(TextUtils.isEmpty(p.originalitySignatureHex) ? "not captured" : "captured")
                    .append('\n');
            sb.append("Compatibility: ").append(p.compatibilityText()).append('\n');
            sb.append("Recommended: ").append(p.recommendedActionText()).append('\n');
            sb.append("AIDs: ").append(p.aidListText()).append('\n');
            sb.append("APDU rules: ").append(p.rules.size()).append('\n');
            for (String note : p.notes) sb.append("Note: ").append(note).append('\n');
        } else {
            sb.append("Card: none registered\n");
        }
        sb.append("\nReader / tag log (newest first):\n");
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
