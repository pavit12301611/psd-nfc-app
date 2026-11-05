package com.psd.nfcapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Home screen: one card, real NTAG sticker path, and an honest HCE compatibility status. */
public class MainActivity extends Activity {
    private CardStore store;

    private TextView armedStatus;
    private TextView cardSummary;
    private TextView branchInfo;
    private TextView compatText;
    private TextView hceStatus;
    private TextView logPreview;
    private Button btnPunch;
    private Button btnWriteSticker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = new CardStore(this);

        armedStatus = findViewById(R.id.armedStatus);
        cardSummary = findViewById(R.id.cardSummary);
        branchInfo = findViewById(R.id.branchInfo);
        compatText = findViewById(R.id.compatText);
        hceStatus = findViewById(R.id.hceStatus);
        logPreview = findViewById(R.id.logPreview);
        btnPunch = findViewById(R.id.btnPunch);
        btnWriteSticker = findViewById(R.id.btnWriteSticker);

        findViewById(R.id.btnRegister).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
        findViewById(R.id.btnEdit).setOnClickListener(v -> {
            CardProfile p = store.loadProfile();
            Intent i = new Intent(this, RegisterActivity.class);
            if (p != null) i.putExtra(RegisterActivity.EXTRA_EDIT, true);
            startActivity(i);
        });
        btnPunch.setOnClickListener(v -> togglePunch());
        btnWriteSticker.setOnClickListener(v ->
                startActivity(new Intent(this, WriteTagActivity.class)));
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
        boolean type2 = p != null && CardProfile.MODE_TYPE2_NDEF.equals(p.mode);
        if (armed && type2) {
            armedStatus.setText("● TYPE-4 HCE FALLBACK ARMED (NOT TYPE-2)");
            armedStatus.setTextColor(0xFFFFB74D);
        } else if (armed) {
            armedStatus.setText("● PUNCH MODE ARMED");
            armedStatus.setTextColor(0xFF7CFC00);
        } else {
            armedStatus.setText(type2 ? "○ TYPE-4 HCE FALLBACK OFF" : "○ PUNCH MODE OFF");
            armedStatus.setTextColor(0xFF9E9E9E);
        }

        branchInfo.setText(BranchInfo.centreSummary());

        if (p == null) {
            cardSummary.setText("No Aakash ID card registered.\n\nTap REGISTER / REPLACE CARD "
                    + "and hold your physical card against the back of the phone.");
            compatText.setText("Compatibility: unknown until a card is registered.");
            compatText.setTextColor(0xFF9E9E9E);
            btnPunch.setEnabled(false);
            btnPunch.setText("START PUNCH MODE");
            btnWriteSticker.setVisibility(View.GONE);
        } else {
            cardSummary.setText("Card: " + p.displayName()
                    + (TextUtils.isEmpty(p.studentId) ? "" : "\nStudent ID: " + p.studentId)
                    + "\nUID: " + (TextUtils.isEmpty(p.uidHex) ? "unknown" : p.uidHex)
                    + "\nTech: " + p.techList
                    + "\nMode: " + p.modeLabel()
                    + "\nNDEF text: " + p.ndefTextListText()
                    + "\nAakash ID: " + (TextUtils.isEmpty(p.aakashId) ? "not found" : p.aakashId)
                    + "\nPossible PSID: " + (TextUtils.isEmpty(p.psidGuess) ? "not found" : p.psidGuess)
                    + "\nExact NDEF: " + (TextUtils.isEmpty(p.ndefHex) ? "no" : p.ndefHex.length() / 2 + " bytes")
                    + "\nNTAG version: " + (TextUtils.isEmpty(p.ntagVersionHex) ? "n/a" : p.ntagVersionHex)
                    + "\nOriginality signature: " + (TextUtils.isEmpty(p.originalitySignatureHex) ? "not captured" : "captured")
                    + "\nAIDs: " + p.aidListText()
                    + "\nAPDU rules: " + p.rules.size()
                    + "\nRecommended: " + p.recommendedActionText());
            compatText.setText(p.compatibilityText());
            compatText.setTextColor(p.canEmulate() ? 0xFFFFD166 : 0xFFFF6B6B);
            btnPunch.setEnabled(true);
            if (type2) {
                btnPunch.setText(armed ? "STOP TYPE-4 FALLBACK" : "ARM TYPE-4 FALLBACK (NOT TYPE-2)");
                btnPunch.setTextColor(0xFFFFB74D);
            } else {
                btnPunch.setText(armed ? "STOP PUNCH MODE" : (p.canEmulate()
                        ? "START PUNCH MODE" : "ARM FOR DIAGNOSTICS"));
                btnPunch.setTextColor(armed ? 0xFF7CFC00 : 0xFFFFFFFF);
            }
            btnWriteSticker.setVisibility(p.canWriteSticker() ? View.VISIBLE : View.GONE);
        }

        hceStatus.setText(HceRegistrar.statusText(this, store));
        List<String> log = store.getLog();
        if (log.isEmpty()) {
            logPreview.setText("No reader APDUs or tag writes yet. If you tap the attendance reader "
                    + "and this stays empty, the reader is not opening an HCE session.");
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
        } else if (p.canEmulate()) {
            setArmedInternal(true, p);
        } else if (p.canHceFallback()) {
            showFallbackWarning(p);
        } else {
            showHardwareWarning(p);
        }
    }

    private void setArmedInternal(boolean on, CardProfile p) {
        store.setArmed(on);
        HceRegistrar.sync(this, p);
        render(p);
        String msg;
        if (!on) {
            msg = "HCE stopped";
        } else if (CardProfile.MODE_TYPE2_NDEF.equals(p.mode)) {
            msg = "Type-4 fallback armed. A Type-2 reader will NOT see it; use the written NTAG sticker.";
        } else {
            msg = "Punch mode armed. Keep the screen on and hold the phone to the reader.";
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void showFallbackWarning(CardProfile p) {
        new AlertDialog.Builder(this)
                .setTitle("Type-4 fallback only — not a Type-2 clone")
                .setMessage("Your registered card is NFC Forum Type 2 / NTAG. Stock Android HCE "
                        + "cannot emulate it. This fallback only serves the exact NDEF to readers "
                        + "that ALSO support Type 4 and ignore the fixed UID. A dedicated "
                        + "Type-2 attendance reader will not reach this app.\n\n"
                        + "For the real reader, write the exact NDEF to a blank NTAG sticker "
                        + "(works only if UID/signature/counter are not checked), or use the "
                        + "physical card / an authorised external Type-2 emulator.")
                .setPositiveButton("Arm fallback anyway", (d, w) -> setArmedInternal(true, p))
                .setNeutralButton("Write NTAG sticker", (d, w) ->
                        startActivity(new Intent(this, WriteTagActivity.class)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showHardwareWarning(CardProfile p) {
        new AlertDialog.Builder(this)
                .setTitle("Android HCE cannot clone this card")
                .setMessage(p.compatibilityText()
                        + "\n\nYou can still arm punch mode to capture a diagnostic APDU log, "
                        + "but the reader will not accept this phone as your card. Use the physical "
                        + "card, an authorised external emulator, or ask the institute to enroll "
                        + "a phone-compatible credential.")
                .setPositiveButton("Arm for diagnostics", (d, w) -> setArmedInternal(true, p))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showFirstRunHelp() {
        android.content.SharedPreferences ui = getSharedPreferences("aakash_ui", MODE_PRIVATE);
        if (ui.getBoolean("seen_help_v3", false)) return;
        ui.edit().putBoolean("seen_help_v3", true).apply();
        new AlertDialog.Builder(this)
                .setTitle("Saharanpur Aakash ID card — how this works")
                .setMessage("1. Register YOUR OWN Aakash ID card once. The app reads its exact "
                        + "NDEF text, UID, tech and NTAG diagnostics.\n\n"
                        + "2. Public Aakash sources list the Court Road, Saharanpur centre; "
                        + "CC-028 is an AIATS exam-centre code, NOT a confirmed NFC branch ID. "
                        + "The app never guesses a branch string — it uses the text on your card.\n\n"
                        + "3. If the card is Type 2 / NTAG, Android HCE CANNOT emulate it. Use "
                        + "WRITE EXACT NDEF TO NTAG STICKER, or the physical card. A sticker "
                        + "only works if the reader checks NDEF and not UID/originality/counter.\n\n"
                        + "4. The TYPE-4 FALLBACK button is for readers that also support Type 4; "
                        + "it is not a Type-2 clone. PUNCH DIAGNOSTICS shows APDUs and tag writes.\n\n"
                        + "5. This app does not record attendance and does not talk to any server; "
                        + "only the institute's reader records a punch. Use it only where permitted.")
                .setPositiveButton("Got it", null)
                .show();
    }
}
