package com.psd.nfcapp;

import android.app.Activity;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.UUID;

/**
 * Way 2: scan an ID card / NFC tag. The readable NFC signal (raw NDEF bytes, and for
 * MIFARE cards the first blocks with default keys) plus the tag UID are captured and
 * saved; the tile then duplicates (re-emits) that exact signal to any other phone.
 */
public class ScanActivity extends Activity implements NfcAdapter.ReaderCallback {
    private NfcAdapter nfc;
    private TextView status;
    private TextView detail;
    private Button btnSave;
    private Button btnAgain;
    private NfcItem captured;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        nfc = NfcAdapter.getDefaultAdapter(this);
        status = findViewById(R.id.scanStatus);
        detail = findViewById(R.id.scanDetail);
        btnSave = findViewById(R.id.btnSave);
        btnAgain = findViewById(R.id.btnAgain);
        EditText openLink = findViewById(R.id.inputOpenLink);

        btnSave.setOnClickListener(v -> {
            if (captured == null) return;
            captured.openLink = openLink.getText().toString().trim();
            new VaultStore(this).save(captured);
            HceService.setArmed(this, captured.id);
            Toast.makeText(this, "Saved & duplicating now", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, TransmitActivity.class));
            finish();
        });
        btnAgain.setOnClickListener(v -> clearCapture());

        if (nfc == null) {
            status.setText("NFC is not available on this device");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfc != null) {
            nfc.enableReaderMode(this, this,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                            | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V
                            | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                    null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfc != null) nfc.disableReaderMode(this);
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        final NfcItem it = readTag(tag);
        runOnUiThread(() -> showCapture(it));
    }

    private NfcItem readTag(Tag tag) {
        NfcItem it = new NfcItem();
        it.id = UUID.randomUUID().toString();
        it.kind = NfcItem.KIND_SCAN;
        it.createdAt = System.currentTimeMillis();
        it.tagIdHex = NfcUtils.bytesToHex(tag.getId());
        it.tech = TextUtils.join(", ", tag.getTechList());

        StringBuilder preview = new StringBuilder();
        String rawHex = null;

        // 1) Read the NDEF message (the actual "signal" most cards/links carry)
        try {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                ndef.connect();
                NdefMessage msg = ndef.getNdefMessage();
                if (msg != null) {
                    rawHex = NfcUtils.bytesToHex(msg.toByteArray());
                    preview.append(NfcUtils.describe(msg));
                }
                ndef.close();
            }
        } catch (Exception ignored) {
        }

        // 2) Best effort: MIFARE Classic — read sector 0 with well-known default keys
        if (rawHex == null) {
            try {
                MifareClassic mc = MifareClassic.get(tag);
                if (mc != null) {
                    mc.connect();
                    byte[][] keys = {
                            NfcUtils.hexToBytes("FFFFFFFFFFFF"),
                            NfcUtils.hexToBytes("A0A1A2A3A4A5"),
                            NfcUtils.hexToBytes("B0B1B2B3B4B5"),
                            NfcUtils.hexToBytes("D3F7D3F7D3F7"),
                            NfcUtils.hexToBytes("000000000000")
                    };
                    StringBuilder sb = new StringBuilder();
                    for (int b = 0; b < 4; b++) {
                        boolean ok = false;
                        for (byte[] k : keys) {
                            try {
                                if (mc.authenticateSectorWithKeyA(0, k)
                                        || mc.authenticateSectorWithKeyB(0, k)) {
                                    sb.append(NfcUtils.bytesToHex(mc.readBlock(b)));
                                    ok = true;
                                    break;
                                }
                            } catch (Exception ignored2) {
                            }
                        }
                        if (!ok) break;
                    }
                    mc.close();
                    if (sb.length() > 0) {
                        rawHex = sb.toString();
                        preview.append("MIFARE blocks 0-3: ")
                                .append(sb.substring(0, Math.min(32, sb.length())));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        it.rawNdefHex = rawHex;
        it.payload = preview.length() > 0
                ? preview.toString()
                : "Tag ID " + it.tagIdHex + " (" + it.tech + ")";
        return it;
    }

    private void showCapture(NfcItem it) {
        captured = it;
        status.setText("✔ Tag captured — duplicate is ready");
        detail.setVisibility(View.VISIBLE);
        detail.setText("Tech: " + it.tech
                + "\nID: " + it.tagIdHex
                + "\nData: " + it.payload
                + (it.rawNdefHex != null ? "\nRaw: " + it.rawNdefHex : ""));
        btnSave.setVisibility(View.VISIBLE);
        btnAgain.setVisibility(View.VISIBLE);
    }

    private void clearCapture() {
        captured = null;
        status.setText("Hold your ID card / NFC tag near the back of the phone");
        detail.setVisibility(View.GONE);
        btnSave.setVisibility(View.GONE);
        btnAgain.setVisibility(View.GONE);
    }
}
