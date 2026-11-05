package com.psd.nfcapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Register one physical Aakash ID card, or edit its AIDs / APDU rules from a reader trace. */
public class RegisterActivity extends Activity implements NfcAdapter.ReaderCallback {
    public static final String EXTRA_EDIT = "edit_existing";

    private NfcAdapter nfc;
    private CardStore store;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private boolean editMode;
    private CardProfile captured;
    private List<CardProfile.ApduRule> rules = new ArrayList<>();

    private TextView status;
    private TextView compat;
    private TextView detail;
    private TextView rulesView;
    private EditText inputName;
    private EditText inputStudentId;
    private EditText inputAids;
    private Button btnSave;
    private Button btnRescan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        store = new CardStore(this);
        nfc = NfcAdapter.getDefaultAdapter(this);

        status = findViewById(R.id.scanStatus);
        compat = findViewById(R.id.cardCompat);
        detail = findViewById(R.id.scanDetail);
        rulesView = findViewById(R.id.rulesView);
        inputName = findViewById(R.id.inputCardName);
        inputStudentId = findViewById(R.id.inputStudentId);
        inputAids = findViewById(R.id.inputAids);
        btnSave = findViewById(R.id.btnSave);
        btnRescan = findViewById(R.id.btnRescan);

        findViewById(R.id.btnAddRule).setOnClickListener(v -> showRuleDialog());
        findViewById(R.id.btnClearRules).setOnClickListener(v -> {
            rules.clear();
            renderRules();
        });
        btnSave.setOnClickListener(v -> save());
        btnRescan.setOnClickListener(v -> {
            editMode = false;
            captured = null;
            rules.clear();
            renderProfile(null);
            renderRules();
            status.setText("Hold your Aakash ID card against the back of the phone");
            enableReaderMode();
        });

        if (!HceRegistrar.supportsHce(this)) {
            status.setText("This phone does not support Android Host Card Emulation (HCE). "
                    + "The card can still be registered for diagnostics, but it cannot be emulated.");
        } else if (nfc == null) {
            status.setText("NFC is not available on this device");
        } else {
            status.setText("Hold your Aakash ID card against the back of the phone");
        }

        if (getIntent().getBooleanExtra(EXTRA_EDIT, false)) {
            CardProfile existing = store.loadProfile();
            if (existing == null) {
                Toast.makeText(this, "No registered card to edit", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            editMode = true;
            captured = existing;
            populateInputs(existing);
            renderProfile(existing);
            status.setText("Editing registered card. Add AIDs / APDU rules from a reader trace, "
                    + "or tap SCAN AGAIN to re-read the physical card.");
            btnRescan.setVisibility(View.VISIBLE);
        }
        renderRules();
    }

    @Override
    protected void onResume() {
        super.onResume();
        HceRegistrar.setPreferred(this, true);
        if (!editMode && nfc != null && HceRegistrar.supportsHce(this)) enableReaderMode();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfc != null) nfc.disableReaderMode(this);
        HceRegistrar.setPreferred(this, false);
    }

    private void enableReaderMode() {
        Bundle options = new Bundle();
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);
        nfc.enableReaderMode(this, this,
                NfcAdapter.FLAG_READER_NFC_A
                        | NfcAdapter.FLAG_READER_NFC_B
                        | NfcAdapter.FLAG_READER_NFC_F
                        | NfcAdapter.FLAG_READER_NFC_V
                        | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                options);
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        if (editMode || !busy.compareAndSet(false, true)) return;
        final CardProfile profile = CardReader.read(tag);
        runOnUiThread(() -> {
            captured = profile;
            rules = new ArrayList<>(profile.rules);
            populateInputs(profile);
            renderProfile(profile);
            status.setText("Card captured: " + profile.modeLabel()
                    + ". Review compatibility below, then save.");
            btnSave.setVisibility(View.VISIBLE);
            btnRescan.setVisibility(View.VISIBLE);
            if (nfc != null) nfc.disableReaderMode(this);
            busy.set(false);
        });
    }

    private void populateInputs(CardProfile p) {
        inputName.setText(TextUtils.isEmpty(p.cardLabel) ? "My Aakash ID card" : p.cardLabel);
        inputStudentId.setText(TextUtils.isEmpty(p.studentId) ? "" : p.studentId);
        StringBuilder sb = new StringBuilder();
        for (String aid : p.aids) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(aid);
        }
        inputAids.setText(sb.toString());
        rules = new ArrayList<>(p.rules);
        renderRules();
    }

    private void renderProfile(CardProfile p) {
        if (p == null) {
            detail.setText("No card captured yet.\n\nHold the physical card to the phone. "
                    + "If nothing happens, the card may be a barcode, QR code or 125 kHz "
                    + "proximity card — Android NFC cannot read those.");
            compat.setText("");
            btnSave.setEnabled(false);
            btnSave.setVisibility(View.GONE);
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("UID: ").append(TextUtils.isEmpty(p.uidHex) ? "unknown" : p.uidHex).append('\n');
        sb.append("Tech: ").append(TextUtils.isEmpty(p.techList) ? "unknown" : p.techList).append('\n');
        sb.append("Mode: ").append(p.modeLabel()).append('\n');
        if (!TextUtils.isEmpty(p.atqaHex)) sb.append("ATQA: ").append(p.atqaHex).append("  ");
        if (!TextUtils.isEmpty(p.sakHex)) sb.append("SAK: ").append(p.sakHex).append('\n');
        if (!TextUtils.isEmpty(p.historicalBytesHex)) {
            sb.append("ATS / historical bytes: ").append(p.historicalBytesHex).append('\n');
        }
        if (!TextUtils.isEmpty(p.ndefHex)) {
            sb.append("NDEF bytes: ").append(p.ndefHex.length() / 2).append('\n');
        }
        if (!TextUtils.isEmpty(p.ndefFileHex)) {
            sb.append("Exact Type 4 file bytes: ").append(p.ndefFileHex.length() / 2).append('\n');
        }
        if (!TextUtils.isEmpty(p.mifareType)) {
            sb.append("MIFARE: ").append(p.mifareType).append(", ").append(p.mifareSize)
                    .append(" bytes, ").append(p.mifareSectorCount).append(" sectors\n");
        }
        if (!TextUtils.isEmpty(p.ultralightType)) {
            sb.append("Ultralight: ").append(p.ultralightType).append('\n');
        }
        sb.append("Discovered AIDs: ").append(p.aidListText()).append('\n');
        sb.append("Captured APDU rules: ").append(rules.size()).append('\n');
        for (String note : p.notes) sb.append("• ").append(note).append('\n');
        detail.setText(sb.toString());
        compat.setText(p.compatibilityText());
        compat.setTextColor(p.canEmulate() ? 0xFFFFD166 : 0xFFFF6B6B);
        btnSave.setEnabled(true);
        btnSave.setVisibility(View.VISIBLE);
        btnRescan.setVisibility(View.VISIBLE);
    }

    private void renderRules() {
        if (rules.isEmpty()) {
            rulesView.setText("No custom APDU rules. Captured card responses are used automatically.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rules.size(); i++) {
            CardProfile.ApduRule r = rules.get(i);
            sb.append(i + 1).append(". ").append(r.commandHex)
                    .append("  ->  ").append(r.responseHex);
            if (!TextUtils.isEmpty(r.note)) sb.append("  [").append(r.note).append(']');
            if (i < rules.size() - 1) sb.append('\n');
        }
        rulesView.setText(sb.toString());
    }

    private void showRuleDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, pad);

        EditText cmd = makeInput("C-APDU hex, e.g. 00A4040007D276000085010100 or 00B0????02*");
        EditText resp = makeInput("Full R-APDU hex incl SW, e.g. 6F0B8407D2760000850101A5009000");
        EditText note = makeInput("Optional note");
        box.addView(cmd, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(resp, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(note, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        new AlertDialog.Builder(this)
                .setTitle("Add APDU rule")
                .setMessage("Use ?? for a wildcard byte or a trailing * for prefix match. "
                        + "The response must include its status word (normally 9000).")
                .setView(scroll)
                .setPositiveButton("Add", (d, w) -> {
                    String c = cmd.getText().toString().trim();
                    String r = resp.getText().toString().trim();
                    String n = note.getText().toString().trim();
                    if (!NfcUtils.isValidRulePattern(c)) {
                        Toast.makeText(this, "Invalid command pattern", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    byte[] parsed = NfcUtils.hexToBytesStrict(r);
                    if (parsed == null || parsed.length < 2) {
                        Toast.makeText(this, "Response must be full hex incl status word",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    rules.add(new CardProfile.ApduRule(c, NfcUtils.cleanHex(r), n));
                    renderRules();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void save() {
        if (captured == null) {
            Toast.makeText(this, "Scan the physical card first", Toast.LENGTH_SHORT).show();
            return;
        }
        captured.cardLabel = inputName.getText().toString().trim();
        if (captured.cardLabel.isEmpty()) captured.cardLabel = "My Aakash ID card";
        captured.studentId = inputStudentId.getText().toString().trim();

        captured.aids.clear();
        for (String line : inputAids.getText().toString().split("[\\r\\n,]+")) {
            String aid = NfcUtils.normalizeAid(line);
            if (aid != null) captured.addAid(aid);
        }
        captured.rules.clear();
        for (CardProfile.ApduRule r : rules) {
            byte[] parsedResponse = NfcUtils.hexToBytesStrict(r.responseHex);
            if (NfcUtils.isValidRulePattern(r.commandHex)
                    && parsedResponse != null && parsedResponse.length >= 2) {
                captured.rules.add(new CardProfile.ApduRule(
                        r.commandHex, NfcUtils.cleanHex(r.responseHex), r.note));
            }
        }

        store.saveProfile(captured);
        store.setArmed(true);
        boolean aidOk = HceRegistrar.sync(this, captured);
        finishAfterSave(aidOk);
    }

    private void finishAfterSave(boolean aidOk) {
        String suffix = aidOk
                ? "Card registered and punch mode armed."
                : "Card saved, but Android rejected dynamic AID registration. Check diagnostics.";
        if (!captured.canEmulate()) {
            new AlertDialog.Builder(this)
                    .setTitle("Hardware limitation")
                    .setMessage(captured.compatibilityText() + "\n\n" + suffix
                            + "\n\nPunch mode can still be armed to capture a reader diagnostic log, "
                            + "but this app cannot make the reader accept this card.")
                    .setCancelable(false)
                    .setPositiveButton("Open diagnostics", (d, w) -> {
                        startActivity(new Intent(this, PunchActivity.class));
                        finish();
                    })
                    .setNegativeButton("Close", (d, w) -> finish())
                    .show();
        } else {
            Toast.makeText(this, suffix, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, PunchActivity.class));
            finish();
        }
    }

    private EditText makeInput(String hint) {
        EditText e = new EditText(this);
        e.setBackgroundResource(R.drawable.input_bg);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(0xFF777777);
        int pad = dp(12);
        e.setPadding(pad, pad, pad, pad);
        return e;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
