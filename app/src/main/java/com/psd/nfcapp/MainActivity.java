package com.psd.nfcapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Home screen: one card, punch mode, and an honest compatibility status. */
public class MainActivity extends Activity {
    private CardStore store;

    private TextView armedStatus;
    private TextView cardSummary;
    private TextView compatText;
    private TextView hceStatus;
    private TextView logPreview;
    private Button btnPunch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = new CardStore(this);

        armedStatus = findViewById(R.id.armedStatus);
        cardSummary = findViewById(R.id.cardSummary);
        compatText = findViewById(R.id.compatText);
        hceStatus = findViewById(R.id.hceStatus);
        logPreview = findViewById(R.id.logPreview);
        btnPunch = findViewById(R.id.btnPunch);

        findViewById(R.id.btnRegister).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
        findViewById(R.id.btnEdit).setOnClickListener(v -> {
            CardProfile p = store.loadProfile();
            Intent i = new Intent(this, RegisterActivity.class);
            if (p != null) i.putExtra(RegisterActivity.EXTRA_EDIT, true);
            startActivity(i);
        });
        btnPunch.setOnClickListener(v -> togglePunch());
        findViewById(R.id.btnDiagnostics).setOnClickListener(v ->
                startActivity(new Intent(this, PunchActivity.class)));
        findViewById(R.id.btnClearLog).setOnClickListener(v -> {
            store.clearLog();
            render(store.loadProfile());
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        CardProfile p = store.loadProfile();
        HceRegistrar.sync(this, p);
        HceRegistrar.setPreferred(this, true);
        render(p);
        showFirstRunHelp();
    }

    @Override
    protected void onPause() {
        super.onPause();
        HceRegistrar.setPreferred(this, false);
    }

    private void render(CardProfile p) {
        boolean armed = store.isArmed();
        armedStatus.setText(armed ? "● PUNCH MODE ARMED" : "○ PUNCH MODE OFF");
        armedStatus.setTextColor(armed ? 0xFF7CFC00 : 0xFF9E9E9E);

        if (p == null) {
            cardSummary.setText("No Aakash ID card registered.\n\nTap REGISTER / REPLACE CARD "
                    + "and hold your physical card against the back of the phone.");
            compatText.setText("Compatibility: unknown until a card is registered.");
            compatText.setTextColor(0xFF9E9E9E);
            btnPunch.setEnabled(false);
            btnPunch.setText("START PUNCH MODE");
        } else {
            cardSummary.setText("Card: " + p.displayName()
                    + (TextUtils.isEmpty(p.studentId) ? "" : "\nStudent ID: " + p.studentId)
                    + "\nUID: " + (TextUtils.isEmpty(p.uidHex) ? "unknown" : p.uidHex)
                    + "\nTech: " + p.techList
                    + "\nMode: " + p.modeLabel()
                    + "\nAIDs: " + p.aidListText()
                    + "\nAPDU rules: " + p.rules.size());
            compatText.setText(p.compatibilityText());
            compatText.setTextColor(p.canEmulate() ? 0xFFFFD166 : 0xFFFF6B6B);
            btnPunch.setEnabled(true);
            btnPunch.setText(armed ? "STOP PUNCH MODE" : "START PUNCH MODE");
        }

        hceStatus.setText(HceRegistrar.statusText(this, store));
        List<String> log = store.getLog();
        if (log.isEmpty()) {
            logPreview.setText("No reader APDUs yet. If you tap the attendance reader and this "
                    + "stays empty, the reader is not opening an HCE session.");
        } else {
            int from = Math.max(0, log.size() - 8);
            logPreview.setText(TextUtils.join("\n", log.subList(from, log.size())));
        }
    }

    private void togglePunch() {
        CardProfile p = store.loadProfile();
        if (p == null) return;
        if (store.isArmed()) {
            setArmedInternal(false, p);
        } else if (!p.canEmulate()) {
            showHardwareWarning(p);
        } else {
            setArmedInternal(true, p);
        }
    }

    private void setArmedInternal(boolean on, CardProfile p) {
        store.setArmed(on);
        HceRegistrar.sync(this, p);
        render(p);
        Toast.makeText(this, on
                ? "Punch mode armed. Keep the screen on and hold the phone to the reader."
                : "Punch mode stopped", Toast.LENGTH_LONG).show();
    }

    private void showHardwareWarning(CardProfile p) {
        new AlertDialog.Builder(this)
                .setTitle("Android HCE cannot clone this card")
                .setMessage(p.compatibilityText()
                        + "\n\nYou can still arm punch mode to capture a diagnostic APDU log, "
                        + "but the reader will not accept this phone as your card. Use the physical "
                        + "card, or ask the institute to enroll a phone-compatible credential.")
                .setPositiveButton("Arm for diagnostics", (d, w) -> setArmedInternal(true, p))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showFirstRunHelp() {
        android.content.SharedPreferences ui = getSharedPreferences("aakash_ui", MODE_PRIVATE);
        if (ui.getBoolean("seen_help_v2", false)) return;
        ui.edit().putBoolean("seen_help_v2", true).apply();
        new AlertDialog.Builder(this)
                .setTitle("How this app actually works")
                .setMessage("1. Register YOUR OWN Aakash ID card once.\n\n"
                        + "2. Read the compatibility line carefully. Android HCE can only emulate "
                        + "ISO-DEP/AID or NFC Forum Type 4 NDEF cards. It CANNOT clone a fixed UID, "
                        + "MIFARE Classic, MIFARE Ultralight, 125 kHz RFID, barcode, or QR card.\n\n"
                        + "3. If compatible, start Punch Mode, keep NFC ON + screen ON, and hold "
                        + "the phone's NFC antenna to the attendance reader.\n\n"
                        + "4. Open PUNCH DIAGNOSTICS. If the APDU log stays empty, the reader never "
                        + "spoke to Android HCE. If APDUs appear but the reader rejects them, copy "
                        + "the diagnostics and add the reader's AID / APDU responses.\n\n"
                        + "5. This app does not record attendance and does not talk to any server; "
                        + "only the institute's reader records a punch. Use it only where permitted.")
                .setPositiveButton("Got it", null)
                .show();
    }
}
