package com.psd.nfcapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ID Card screen — used both to create a card and to edit a saved one.
 *
 * Create: hold the ID card / NFC tag to the back of the phone. Its readable NFC signal and
 * tag UID are captured, then you add a card name, a photo and any number of custom fields
 * and save. The tile re-emits (duplicates) the captured signal to other phones.
 *
 * Edit: opened from a tile's long-press menu with EXTRA_ITEM_ID. The captured signal is kept;
 * only the name, photo, fields and open-link are changed.
 */
public class ScanActivity extends Activity implements NfcAdapter.ReaderCallback {
    public static final String EXTRA_ITEM_ID = "item_id";
    private static final int REQ_PICK_PHOTO = 41;

    private NfcAdapter nfc;
    private VaultStore store;
    private boolean editing;

    private TextView status;
    private TextView detail;
    private EditText inputName;
    private ImageView photoPreview;
    private Button btnPickPhoto;
    private Button btnRemovePhoto;
    private LinearLayout fieldsContainer;
    private EditText openLink;
    private Button btnSave;
    private Button btnAgain;

    /** The card being created (after a scan) or edited. */
    private NfcItem captured;
    /** Photo chosen on this screen (base64 JPEG), or null for none. */
    private String pendingPhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        nfc = NfcAdapter.getDefaultAdapter(this);
        store = new VaultStore(this);

        status = findViewById(R.id.scanStatus);
        detail = findViewById(R.id.scanDetail);
        inputName = findViewById(R.id.inputCardName);
        photoPreview = findViewById(R.id.photoPreview);
        btnPickPhoto = findViewById(R.id.btnPickPhoto);
        btnRemovePhoto = findViewById(R.id.btnRemovePhoto);
        fieldsContainer = findViewById(R.id.fieldsContainer);
        openLink = findViewById(R.id.inputOpenLink);
        btnSave = findViewById(R.id.btnSave);
        btnAgain = findViewById(R.id.btnAgain);

        btnPickPhoto.setOnClickListener(v -> pickPhoto());
        photoPreview.setOnClickListener(v -> PhotoUtil.showFullScreen(this, pendingPhoto));
        btnRemovePhoto.setOnClickListener(v -> {
            pendingPhoto = null;
            refreshPhoto();
        });
        findViewById(R.id.btnAddField).setOnClickListener(v -> addFieldRow("", ""));
        btnSave.setOnClickListener(v -> save());
        btnAgain.setOnClickListener(v -> clearCapture());

        String itemId = getIntent().getStringExtra(EXTRA_ITEM_ID);
        if (itemId != null) {
            NfcItem existing = store.get(itemId);
            if (existing == null) {
                Toast.makeText(this, "This ID card no longer exists", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            editing = true;
            captured = existing;
            inputName.setText(existing.cardName == null ? "" : existing.cardName);
            pendingPhoto = TextUtils.isEmpty(existing.photoBase64) ? null : existing.photoBase64;
            openLink.setText(existing.openLink == null ? "" : existing.openLink);
            for (NfcItem.Field f : existing.fields) addFieldRow(f.label, f.value);
            status.setText("Editing saved ID card — its NFC signal is kept as it was scanned.");
            showDetail(existing);
            btnAgain.setVisibility(View.GONE);
            btnSave.setText("SAVE CHANGES");
            btnSave.setVisibility(View.VISIBLE);
        } else {
            status.setText("Hold your ID card / NFC tag near the back of the phone");
        }
        refreshPhoto();

        if (nfc == null) {
            status.setText("NFC is not available on this device");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfc != null && !editing) {
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

    // ---------------------------------------------------------------- photo

    private void pickPhoto() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(i, REQ_PICK_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_PHOTO && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            String b64 = PhotoUtil.fromUri(this, data.getData());
            if (b64 == null) {
                Toast.makeText(this, "Could not read that photo", Toast.LENGTH_SHORT).show();
                return;
            }
            pendingPhoto = b64;
            refreshPhoto();
        }
    }

    private void refreshPhoto() {
        Bitmap bmp = PhotoUtil.decode(pendingPhoto, 800);
        if (bmp != null) {
            photoPreview.setImageBitmap(bmp);
            photoPreview.setVisibility(View.VISIBLE);
            btnPickPhoto.setText("CHANGE PHOTO");
            btnRemovePhoto.setVisibility(View.VISIBLE);
        } else {
            photoPreview.setImageDrawable(null);
            photoPreview.setVisibility(View.GONE);
            btnPickPhoto.setText("+ ADD PHOTO");
            btnRemovePhoto.setVisibility(View.GONE);
        }
    }

    // ---------------------------------------------------------------- custom fields

    private void addFieldRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        EditText l = makeInput("Label (e.g. Blood group)", label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(dp(6));
        row.addView(l, lp);

        EditText v = makeInput("Value", value);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        vp.setMarginEnd(dp(6));
        row.addView(v, vp);

        Button del = new Button(this);
        del.setText("✕");
        del.setAllCaps(false);
        del.setTextColor(0xFFFFFFFF);
        del.setBackgroundResource(R.drawable.btn_ghost);
        del.setOnClickListener(x -> fieldsContainer.removeView(row));
        row.addView(del, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(8);
        fieldsContainer.addView(row, rowLp);
    }

    /** Reads every field row; rows where both label and value are empty are skipped. */
    private List<NfcItem.Field> collectFields() {
        List<NfcItem.Field> out = new ArrayList<>();
        for (int i = 0; i < fieldsContainer.getChildCount(); i++) {
            View child = fieldsContainer.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) child;
            String label = ((EditText) row.getChildAt(0)).getText().toString().trim();
            String value = ((EditText) row.getChildAt(1)).getText().toString().trim();
            if (label.isEmpty() && value.isEmpty()) continue;
            out.add(new NfcItem.Field(label, value));
        }
        return out;
    }

    private EditText makeInput(String hint, String text) {
        EditText e = new EditText(this);
        e.setBackgroundResource(R.drawable.input_bg);
        e.setHint(hint);
        e.setText(text == null ? "" : text);
        e.setSingleLine(true);
        e.setTextColor(0xFFFFFFFF);
        e.setHintTextColor(0xFF666666);
        e.setTextSize(15);
        int pad = dp(12);
        e.setPadding(pad, pad, pad, pad);
        return e;
    }

    // ---------------------------------------------------------------- save

    private void save() {
        if (captured == null) return;
        String name = inputName.getText().toString().trim();
        captured.cardName = name.isEmpty() ? "ID Card" : name;
        captured.photoBase64 = pendingPhoto;
        captured.openLink = openLink.getText().toString().trim();
        captured.fields = collectFields();
        store.save(captured);

        if (editing) {
            HceService.refresh(this);
            Toast.makeText(this, "ID card updated", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            HceService.setArmed(this, captured.id);
            Toast.makeText(this, "Saved & duplicating now", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, TransmitActivity.class));
            finish();
        }
    }

    // ---------------------------------------------------------------- scan

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
        if (editing) return;
        captured = it;
        status.setText("✔ Tag captured — add the name, photo and fields, then save");
        showDetail(it);
        btnSave.setText("SAVE & DUPLICATE");
        btnSave.setVisibility(View.VISIBLE);
        btnAgain.setVisibility(View.VISIBLE);
    }

    private void showDetail(NfcItem it) {
        detail.setVisibility(View.VISIBLE);
        detail.setText("Tech: " + it.tech
                + "\nID: " + it.tagIdHex
                + "\nData: " + it.payload
                + (it.rawNdefHex != null && !it.rawNdefHex.isEmpty() ? "\nRaw: " + it.rawNdefHex : ""));
    }

    private void clearCapture() {
        captured = null;
        status.setText("Hold your ID card / NFC tag near the back of the phone");
        detail.setVisibility(View.GONE);
        btnSave.setVisibility(View.GONE);
        btnAgain.setVisibility(View.GONE);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
