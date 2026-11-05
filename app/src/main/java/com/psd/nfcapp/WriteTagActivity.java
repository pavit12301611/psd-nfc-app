package com.psd.nfcapp;

import android.app.Activity;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.nfc.tech.TagTechnology;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The actual working path for a Type 2 / NTAG Aakash card: write the exact NDEF bytes
 * from the registered card onto a real NTAG213/215/216 sticker and read them back.
 *
 * This is NOT HCE. A phone still cannot emulate Type 2; the sticker itself is the
 * credential presented to the reader.
 */
public class WriteTagActivity extends Activity {
    private static final int FLAGS = NfcAdapter.FLAG_READER_NFC_A
            | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS;

    private NfcAdapter adapter;
    private CardStore store;
    private CardProfile profile;
    private volatile boolean lockAfterWrite;
    private final AtomicReference<String> pendingAction = new AtomicReference<>(null);

    private TextView statusText;
    private TextView detailText;
    private TextView cardInfoText;
    private TextView ndefPreviewText;
    private CheckBox lockCheck;
    private Button btnWrite;
    private Button btnVerify;
    private Button btnClose;

    private final NfcAdapter.ReaderCallback readerCallback = new NfcAdapter.ReaderCallback() {
        @Override
        public void onTagDiscovered(Tag tag) {
            WriteTagActivity.this.onTagDiscovered(tag);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_write_tag);
        store = new CardStore(getApplicationContext());
        adapter = NfcAdapter.getDefaultAdapter(this);

        statusText = findViewById(R.id.writeStatus);
        detailText = findViewById(R.id.writeDetail);
        cardInfoText = findViewById(R.id.writeCardInfo);
        ndefPreviewText = findViewById(R.id.writeNdefPreview);
        lockCheck = findViewById(R.id.checkLockTag);
        btnWrite = findViewById(R.id.btnWriteTag);
        btnVerify = findViewById(R.id.btnVerifyTag);
        btnClose = findViewById(R.id.btnCloseWrite);

        lockCheck.setOnCheckedChangeListener((button, checked) -> lockAfterWrite = checked);
        btnWrite.setOnClickListener(v -> arm("WRITE"));
        btnVerify.setOnClickListener(v -> arm("VERIFY"));
        btnClose.setOnClickListener(v -> finish());

        profile = store.loadProfile();
        if (profile == null || !profile.canWriteSticker()) {
            statusText.setText("No Type 2 / NTAG NDEF card is registered. Register the physical "
                    + "Aakash ID card first, then return here.");
            statusText.setTextColor(0xFFB3261E);
            btnWrite.setEnabled(false);
            btnVerify.setEnabled(false);
            return;
        }

        Expected expected = buildExpected(profile);
        detailText.setText(profile.compatibilityText());
        cardInfoText.setText(cardInfo(profile));
        ndefPreviewText.setText(ndefPreview(profile, expected));
        if (adapter == null) {
            statusText.setText("This phone has no NFC adapter.");
            statusText.setTextColor(0xFFB3261E);
        } else if (!adapter.isEnabled()) {
            statusText.setText("NFC is OFF. Turn it on in quick settings, then tap WRITE or VERIFY.");
            statusText.setTextColor(0xFFFFB74D);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableReaderMode();
    }

    @Override
    protected void onPause() {
        disableReaderMode();
        super.onPause();
    }

    private void enableReaderMode() {
        if (adapter == null) return;
        Bundle options = new Bundle();
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 5000);
        adapter.enableReaderMode(this, readerCallback, FLAGS, options);
    }

    private void disableReaderMode() {
        if (adapter != null) adapter.disableReaderMode(this);
    }

    private void arm(String action) {
        if (adapter == null || !adapter.isEnabled()) {
            setStatus("NFC is OFF or unavailable. Turn it on first.", false);
            return;
        }
        pendingAction.set(action);
        setButtonsEnabled(false);
        if ("WRITE".equals(action)) {
            setStatus("Hold a BLANK NTAG213 / NTAG215 / NTAG216 sticker flat against the phone. "
                    + "Keep it still until the result appears.", true);
        } else {
            setStatus("Hold the written sticker to the phone to compare its NDEF with the "
                    + "registered Aakash card.", true);
        }
    }

    private void onTagDiscovered(Tag tag) {
        String action = pendingAction.getAndSet(null);
        if (action == null) return;
        CardProfile p = store.loadProfile();
        if (p == null || !p.canWriteSticker()) {
            setStatus("No registered Type 2 / NTAG NDEF card found.", false);
            setButtonsEnabled(true);
            return;
        }
        Expected expected = buildExpected(p);
        try {
            if (!isType2Tag(tag)) {
                setStatus("This is not an NFC-A / Type 2 / NTAG tag. Use a blank NTAG213/215/216 sticker.",
                        false);
                store.appendLog("TAG_REJECT | tech=" + Arrays.toString(tag.getTechList()));
            } else if ("WRITE".equals(action)) {
                writeSticker(tag, p, expected);
            } else {
                verifySticker(tag, p, expected, false);
            }
        } catch (Exception e) {
            setStatus("Tag operation failed: " + simpleError(e), false);
            store.appendLog("TAG_ERROR | " + simpleError(e));
        } finally {
            setButtonsEnabled(true);
        }
    }

    private void writeSticker(Tag tag, CardProfile p, Expected expected) {
        Ndef ndef = Ndef.get(tag);
        NdefFormatable formatable = NdefFormatable.get(tag);
        if (ndef == null && formatable == null) {
            setStatus("Tag exposes neither NDEF nor NdefFormatable. Use a blank, writable NTAG sticker.",
                    false);
            return;
        }
        byte[] want = expected.message.toByteArray();
        String uid = NfcUtils.bytesToHex(tag.getId());
        try {
            if (ndef != null) {
                ndef.connect();
                if (!ndef.isWritable()) {
                    setStatus("This tag is already read-only. Use a blank writable NTAG sticker.", false);
                    closeQuietly(ndef);
                    return;
                }
                if (ndef.getMaxSize() < want.length) {
                    setStatus("Tag is too small: " + ndef.getMaxSize() + " bytes available, "
                            + want.length + " bytes needed. Use NTAG215 or NTAG216.", false);
                    closeQuietly(ndef);
                    return;
                }
                ndef.writeNdefMessage(expected.message);
                if (lockAfterWrite) {
                    if (ndef.canMakeReadOnly()) {
                        boolean locked = ndef.makeReadOnly();
                        store.appendLog("TAG_LOCK | " + (locked ? "read-only set" : "lock refused")
                                + " | UID " + uid);
                        if (!locked) setStatus("Write succeeded but the tag could not be locked.", false);
                    } else {
                        setStatus("Write succeeded but this tag cannot be made read-only.", false);
                    }
                }
                closeQuietly(ndef);
            } else {
                formatable.connect();
                formatable.format(expected.message);
                closeQuietly(formatable);
            }
            store.appendLog("TAG_WRITE | " + expected.note + " | " + want.length
                    + " NDEF bytes | UID " + uid);
            verifySticker(tag, p, expected, true);
        } catch (Exception e) {
            closeQuietly(ndef);
            closeQuietly(formatable);
            setStatus("Write failed: " + simpleError(e) + ". Keep the tag still and try again.", false);
            store.appendLog("TAG_WRITE_ERROR | " + simpleError(e) + " | UID " + uid);
        }
    }

    private void verifySticker(Tag tag, CardProfile p, Expected expected, boolean afterWrite) {
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) {
            setStatus("Blank tag formatted. Tap it once more to verify (or press VERIFY).", true);
            pendingAction.set("VERIFY");
            return;
        }
        byte[] want = expected.message.toByteArray();
        String uid = NfcUtils.bytesToHex(tag.getId());
        try {
            ndef.connect();
            NdefMessage read = ndef.getNdefMessage();
            byte[] got = read == null ? new byte[0] : read.toByteArray();
            closeQuietly(ndef);
            boolean match = Arrays.equals(want, got);
            String prefix = afterWrite ? "WROTE AND " : "";
            String msg;
            if (match) {
                msg = prefix + "VERIFIED: sticker contains the exact " + want.length
                        + "-byte NDEF from your Aakash card. This does NOT prove the institute "
                        + "reader accepted it; test at the reader.";
                store.appendLog("TAG_VERIFY_OK | exact " + want.length
                        + " bytes | UID " + uid);
            } else {
                msg = prefix + "READBACK MISMATCH: expected " + want.length + " bytes, got "
                        + got.length + ". The sticker may be a different NDEF or partially written.";
                store.appendLog("TAG_VERIFY_MISMATCH | want=" + want.length + " got=" + got.length
                        + " | UID " + uid);
            }
            if (p.type2Detected && !TextUtils.isEmpty(p.uidHex) && !p.uidHex.equals(uid)) {
                msg += "\nNote: sticker UID " + uid + " differs from original card UID "
                        + p.uidHex + ". If the reader checks UID/signature, this sticker will be rejected.";
            }
            setStatus(msg, match);
        } catch (Exception e) {
            closeQuietly(ndef);
            setStatus("Verify failed: " + simpleError(e), false);
            store.appendLog("TAG_VERIFY_ERROR | " + simpleError(e) + " | UID " + uid);
        }
    }

    private Expected buildExpected(CardProfile p) {
        Expected e = new Expected();
        byte[] exact = NfcUtils.hexToBytesStrict(p.ndefHex);
        if (exact != null && exact.length > 0) {
            try {
                e.message = new NdefMessage(exact);
                e.exact = true;
                e.note = "exact captured NDEF bytes";
                return e;
            } catch (FormatException ignored) {
                // fall through to a reconstructed text record
            } catch (Exception ignored) {
                // fall through to a reconstructed text record
            }
        }
        String text = !TextUtils.isEmpty(p.aakashId) ? p.aakashId
                : (!TextUtils.isEmpty(p.studentId) ? p.studentId : BranchInfo.EXPECTED_NDEF_PREFIX);
        e.message = new NdefMessage(new NdefRecord[]{NdefRecord.createTextRecord("en", text)});
        e.exact = false;
        e.note = "reconstructed text record \"" + text + "\" (no exact NDEF bytes captured)";
        return e;
    }

    private boolean isType2Tag(Tag tag) {
        if (tag == null) return false;
        for (String t : tag.getTechList()) {
            if (t.endsWith("MifareUltralight") || t.endsWith("NfcA")) return true;
        }
        Ndef ndef = Ndef.get(tag);
        return ndef != null && Ndef.NFC_FORUM_TYPE_2.equals(ndef.getType());
    }

    private String cardInfo(CardProfile p) {
        return "Card: " + p.displayName()
                + "\nMode: " + p.modeLabel()
                + "\nUID: " + (TextUtils.isEmpty(p.uidHex) ? "unknown" : p.uidHex)
                + "\nNDEF text: " + p.ndefTextListText()
                + "\nAakash identifier: " + (TextUtils.isEmpty(p.aakashId) ? "not found" : p.aakashId)
                + "\nPossible PSID: " + (TextUtils.isEmpty(p.psidGuess) ? "not found" : p.psidGuess)
                + "\nSaharanpur centre: " + BranchInfo.CENTRE_NAME
                + " / public AIATS code " + BranchInfo.AIATS_CENTRE_CODE;
    }

    private String ndefPreview(CardProfile p, Expected expected) {
        byte[] bytes = expected.message.toByteArray();
        String hex = NfcUtils.bytesToHex(bytes);
        if (hex.length() > 400) hex = hex.substring(0, 400) + "…";
        return "Payload: " + expected.note
                + "\nExact bytes: " + (expected.exact ? "YES" : "NO")
                + "\nNDEF hex (" + bytes.length + " bytes):\n" + hex
                + (TextUtils.isEmpty(p.ndefHex) ? "" : "\nCaptured NDEF hex:\n" + p.ndefHex);
    }

    private void setStatus(String msg, boolean good) {
        runOnUiThread(() -> {
            statusText.setText(msg);
            statusText.setTextColor(good ? 0xFF1B8A2E : 0xFFB3261E);
        });
    }

    private void setButtonsEnabled(boolean on) {
        runOnUiThread(() -> {
            btnWrite.setEnabled(on);
            btnVerify.setEnabled(on);
        });
    }

    private void closeQuietly(TagTechnology t) {
        if (t == null) return;
        try {
            t.close();
        } catch (Exception ignored) {
        }
    }

    private String simpleError(Exception e) {
        String msg = e.getMessage();
        return e.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }

    private static final class Expected {
        NdefMessage message;
        boolean exact;
        String note;
    }
}
