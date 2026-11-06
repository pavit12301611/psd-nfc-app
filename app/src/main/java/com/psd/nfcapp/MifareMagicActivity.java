package com.psd.nfcapp;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Working path for a MIFARE Classic Aakash ID card: write the exact full dump
 * (including block 0 UID) to a Gen2/CUID UID-changeable magic card and verify it.
 *
 * Normal MIFARE Classic cards have a read-only block 0, so they cannot reproduce
 * the original UID. Stock Android HCE cannot emulate MIFARE Classic at all.
 */
public class MifareMagicActivity extends Activity {
    private static final int FLAGS = NfcAdapter.FLAG_READER_NFC_A
            | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
            | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;

    private NfcAdapter adapter;
    private CardStore store;
    private CardProfile profile;
    private volatile boolean skipBlock0;
    private final AtomicReference<String> pendingAction = new AtomicReference<>(null);

    private TextView statusText;
    private TextView detailText;
    private TextView cardInfoText;
    private TextView dumpPreviewText;
    private CheckBox skipBlock0Check;
    private Button btnWrite;
    private Button btnVerify;
    private Button btnCopy;
    private Button btnShare;
    private Button btnClose;

    private final NfcAdapter.ReaderCallback readerCallback = new NfcAdapter.ReaderCallback() {
        @Override
        public void onTagDiscovered(Tag tag) {
            MifareMagicActivity.this.onTagDiscovered(tag);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mifare_magic);
        store = new CardStore(getApplicationContext());
        adapter = NfcAdapter.getDefaultAdapter(this);

        statusText = findViewById(R.id.magicStatus);
        detailText = findViewById(R.id.magicDetail);
        cardInfoText = findViewById(R.id.magicCardInfo);
        dumpPreviewText = findViewById(R.id.magicDumpPreview);
        skipBlock0Check = findViewById(R.id.checkSkipBlock0);
        btnWrite = findViewById(R.id.btnWriteMagic);
        btnVerify = findViewById(R.id.btnVerifyMagic);
        btnCopy = findViewById(R.id.btnCopyDump);
        btnShare = findViewById(R.id.btnShareDump);
        btnClose = findViewById(R.id.btnCloseMagic);

        skipBlock0Check.setOnCheckedChangeListener((button, checked) -> skipBlock0 = checked);
        btnWrite.setOnClickListener(v -> arm("WRITE"));
        btnVerify.setOnClickListener(v -> arm("VERIFY"));
        btnCopy.setOnClickListener(v -> copyDump());
        btnShare.setOnClickListener(v -> shareDump());
        btnClose.setOnClickListener(v -> finish());

        profile = store.loadProfile();
        if (profile != null) {
            detailText.setText(profile.compatibilityText());
            cardInfoText.setText(cardInfo(profile));
            dumpPreviewText.setText(dumpPreview(profile));
        }
        boolean hasDump = profile != null && !TextUtils.isEmpty(profile.mifareBlocksHex);
        btnCopy.setEnabled(hasDump);
        btnShare.setEnabled(hasDump);
        if (profile == null || !profile.canWriteMagicCard()) {
            statusText.setText("No complete MIFARE Classic dump is registered. Register the physical "
                    + "card again; if sectors are locked, recover keys with MIFARE Classic Tool / Proxmark.");
            statusText.setTextColor(0xFFB3261E);
            btnWrite.setEnabled(false);
            btnVerify.setEnabled(false);
            return;
        }
        if (adapter == null) {
            statusText.setText("This phone has no NFC adapter.");
            statusText.setTextColor(0xFFB3261E);
        } else if (!adapter.isEnabled()) {
            statusText.setText("NFC is OFF. Turn it on, then tap WRITE or VERIFY.");
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
            setStatus("Hold a BLANK Gen2/CUID MIFARE Classic 1K magic card flat against the phone. "
                    + "Keep it still until the result appears.", true);
        } else {
            setStatus("Remove and re-tap the written magic card to compare its UID and full dump.",
                    true);
        }
    }

    private void onTagDiscovered(Tag tag) {
        String action = pendingAction.getAndSet(null);
        if (action == null) return;
        CardProfile p = store.loadProfile();
        if (p == null || !p.canWriteMagicCard()) {
            setStatus("No complete MIFARE Classic dump registered.", false);
            setButtonsEnabled(true);
            return;
        }
        try {
            if ("WRITE".equals(action)) {
                writeMagic(tag, p);
            } else {
                verifyMagic(tag, p);
            }
        } catch (Exception e) {
            setStatus("Tag operation failed: " + simpleError(e), false);
            store.appendLog("MIFARE_ERROR | " + simpleError(e));
        } finally {
            setButtonsEnabled(true);
        }
    }

    private void writeMagic(Tag tag, CardProfile p) {
        MifareClassic mc = MifareClassic.get(tag);
        if (mc == null) {
            setStatus("Target is not a MIFARE Classic card.", false);
            return;
        }
        int targetUidLen = tag.getId().length;
        int sourceUidLen = p.uidHex == null ? 4 : p.uidHex.length() / 2;
        if (targetUidLen != sourceUidLen) {
            setStatus("Target UID length " + targetUidLen + " bytes does not match source "
                    + sourceUidLen + " bytes. Use a 4-byte UID Gen2/CUID card.", false);
            return;
        }
        byte[] dump = buildDump(p);
        if (dump == null) {
            setStatus("Registered dump is missing or incomplete.", false);
            return;
        }
        byte[] block0 = block(dump, 0);
        if (!NfcUtils.isValidMifareBlock0(NfcUtils.bytesToHex(block0), sourceUidLen)) {
            setStatus("Block 0 BCC/UID check failed; refusing to write (would brick the magic card).",
                    false);
            return;
        }
        SectorKeys[] keys = buildKeyMap(p);
        String targetUid = NfcUtils.bytesToHex(tag.getId());
        try {
            mc.connect();
            if (mc.getSize() != p.mifareSize
                    || mc.getSectorCount() != p.mifareSectorCount
                    || mc.getBlockCount() != p.mifareBlockCount) {
                setStatus("Target size mismatch: need " + p.mifareSize
                        + " bytes / " + p.mifareSectorCount + " sectors.", false);
                return;
            }
            for (int sector = 0; sector < p.mifareSectorCount; sector++) {
                int blocks = blockCountInSector(p, sector);
                int first = sectorToBlock(p, sector);
                int trailer = first + blocks - 1;
                List<String> candidates = candidateKeys(p, sector, keys);
                for (int b = first; b < trailer; b++) {
                    if (b == 0) continue; // block 0 is written last, after UID is stable
                    if (!writeBlockWithAuth(mc, sector, b, block(dump, b), candidates)) {
                        setStatus("Write failed at sector " + sector + " block " + b
                                + ". Target keys may be locked or not Gen2/CUID.", false);
                        store.appendLog("MIFARE_WRITE_FAIL | sector=" + sector + " block=" + b
                                + " | target UID " + targetUid);
                        return;
                    }
                }
                // Sector trailer last for this sector: after this, original keys apply.
                if (!writeBlockWithAuth(mc, sector, trailer, block(dump, trailer), candidates)) {
                    setStatus("Write failed at sector " + sector + " trailer.", false);
                    store.appendLog("MIFARE_WRITE_FAIL | sector=" + sector + " trailer | UID "
                            + targetUid);
                    return;
                }
            }
            if (!skipBlock0) {
                List<String> sector0Keys = candidateKeys(p, 0, keys);
                if (!writeBlockWithAuth(mc, 0, 0, block0, sector0Keys)) {
                    setStatus("Block 0 (UID) write failed. The target is probably a normal "
                            + "MIFARE Classic card or Gen1a, not Gen2/CUID. Use Proxmark / "
                            + "ChameleonUltra for Gen1a, or buy a Gen2/CUID card.", false);
                    store.appendLog("MIFARE_BLOCK0_FAIL | target UID " + targetUid);
                    return;
                }
            }
            store.appendLog("MIFARE_WRITE_OK | " + dump.length + " bytes | sectors "
                    + p.mifareSectorCount + " | block0 " + (skipBlock0 ? "SKIPPED" : "written")
                    + " | target UID " + targetUid);
            pendingAction.set("VERIFY");
            setStatus("Dump written. Remove and tap the magic card again to verify its new UID "
                    + "and full " + dump.length + "-byte dump.", true);
        } catch (Exception e) {
            setStatus("Write failed: " + simpleError(e), false);
            store.appendLog("MIFARE_WRITE_ERROR | " + simpleError(e));
        } finally {
            closeQuietly(mc);
        }
    }

    private void verifyMagic(Tag tag, CardProfile p) {
        MifareClassic mc = MifareClassic.get(tag);
        if (mc == null) {
            setStatus("Target is not a MIFARE Classic card.", false);
            return;
        }
        String uid = NfcUtils.bytesToHex(tag.getId());
        if (p.uidHex == null || !uid.equalsIgnoreCase(p.uidHex)) {
            setStatus("UID MISMATCH: magic card UID " + uid + " != original " + p.uidHex
                    + ". The reader may reject it.", false);
            store.appendLog("MIFARE_VERIFY_UID_MISMATCH | got=" + uid + " want=" + p.uidHex);
            return;
        }
        byte[] dump = buildDump(p);
        if (dump == null) {
            setStatus("Registered dump is missing or incomplete.", false);
            return;
        }
        SectorKeys[] keys = buildKeyMap(p);
        int mismatches = 0;
        try {
            mc.connect();
            if (mc.getSize() != p.mifareSize
                    || mc.getSectorCount() != p.mifareSectorCount
                    || mc.getBlockCount() != p.mifareBlockCount) {
                setStatus("Target size mismatch.", false);
                return;
            }
            for (int sector = 0; sector < p.mifareSectorCount; sector++) {
                int blocks = blockCountInSector(p, sector);
                int first = sectorToBlock(p, sector);
                int trailer = first + blocks - 1;
                List<String> candidates = candidateKeys(p, sector, keys);
                for (int b = first; b <= trailer; b++) {
                    byte[] got = readBlockWithAuth(mc, sector, b, candidates);
                    if (got == null) {
                        mismatches++;
                        continue;
                    }
                    byte[] want = block(dump, b);
                    if (b == trailer) {
                        // Key A is masked as 00 on read; compare access bits / GPB only.
                        for (int i = 6; i <= 9; i++) {
                            if (got[i] != want[i]) mismatches++;
                        }
                    } else if (!Arrays.equals(got, want)) {
                        mismatches++;
                    }
                }
            }
            if (mismatches == 0) {
                setStatus("VERIFIED: UID " + uid + " and all data/access bytes match the original "
                        + dump.length + "-byte dump. This does NOT prove the institute reader "
                        + "accepted the punch. A reader beep alone is also not success; check the "
                        + "attendance record / SMS / backend. Test at the reader.", true);
                store.appendLog("MIFARE_VERIFY_OK | UID " + uid + " | " + dump.length + " bytes");
            } else {
                setStatus("VERIFY MISMATCH: " + mismatches + " byte/block mismatch(es). "
                        + "Re-write or use Proxmark / MIFARE Classic Tool.", false);
                store.appendLog("MIFARE_VERIFY_MISMATCH | " + mismatches + " | UID " + uid);
            }
        } catch (Exception e) {
            setStatus("Verify failed: " + simpleError(e), false);
            store.appendLog("MIFARE_VERIFY_ERROR | " + simpleError(e));
        } finally {
            closeQuietly(mc);
        }
    }

    private boolean writeBlockWithAuth(MifareClassic mc, int sector, int absBlock,
                                       byte[] data, List<String> candidates) {
        for (String keyHex : candidates) {
            byte[] key = NfcUtils.hexToBytesStrict(keyHex);
            if (key == null || key.length != 6) continue;
            for (int which = 0; which < 2; which++) {
                try {
                    if (!mc.isConnected()) mc.connect();
                    boolean ok = which == 0
                            ? mc.authenticateSectorWithKeyA(sector, key)
                            : mc.authenticateSectorWithKeyB(sector, key);
                    if (ok) {
                        mc.writeBlock(absBlock, data);
                        return true;
                    }
                } catch (Exception ignored) {
                }
                resetConnection(mc);
            }
        }
        return false;
    }

    private byte[] readBlockWithAuth(MifareClassic mc, int sector, int absBlock,
                                     List<String> candidates) {
        for (String keyHex : candidates) {
            byte[] key = NfcUtils.hexToBytesStrict(keyHex);
            if (key == null || key.length != 6) continue;
            for (int which = 0; which < 2; which++) {
                try {
                    if (!mc.isConnected()) mc.connect();
                    boolean ok = which == 0
                            ? mc.authenticateSectorWithKeyA(sector, key)
                            : mc.authenticateSectorWithKeyB(sector, key);
                    if (ok) {
                        byte[] data = mc.readBlock(absBlock);
                        if (data != null && data.length >= 16) return Arrays.copyOf(data, 16);
                    }
                } catch (Exception ignored) {
                }
                resetConnection(mc);
            }
        }
        return null;
    }

    private void resetConnection(MifareClassic mc) {
        try {
            if (mc.isConnected()) mc.close();
        } catch (Exception ignored) {
        }
        try {
            mc.connect();
        } catch (Exception ignored) {
        }
    }

    private List<String> candidateKeys(CardProfile p, int sector, SectorKeys[] keys) {
        Set<String> set = new LinkedHashSet<>();
        set.addAll(CardReader.commonKeys());
        for (SectorKeys k : keys) {
            if (!TextUtils.isEmpty(k.keyA)) set.add(k.keyA);
            if (!TextUtils.isEmpty(k.keyB)) set.add(k.keyB);
        }
        if (sector >= 0 && sector < keys.length) {
            set.add(keys[sector].keyA);
            set.add(keys[sector].keyB);
        }
        return new ArrayList<>(set);
    }

    private SectorKeys[] buildKeyMap(CardProfile p) {
        SectorKeys[] keys = new SectorKeys[Math.max(1, p.mifareSectorCount)];
        for (int i = 0; i < keys.length; i++) keys[i] = new SectorKeys();
        for (String entry : p.mifareSectorKeys) {
            String[] parts = entry.split(":");
            if (parts.length != 3 || parts[2].length() != 12) continue;
            int sector;
            try {
                sector = Integer.parseInt(parts[0]);
            } catch (NumberFormatException e) {
                continue;
            }
            if (sector < 0 || sector >= keys.length) continue;
            if ("A".equalsIgnoreCase(parts[1])) keys[sector].keyA = parts[2];
            if ("B".equalsIgnoreCase(parts[1])) keys[sector].keyB = parts[2];
        }
        return keys;
    }

    private byte[] buildDump(CardProfile p) {
        byte[] dump = NfcUtils.hexToBytesStrict(p.mifareBlocksHex);
        if (dump == null || p.mifareBlockCount <= 0
                || dump.length < p.mifareBlockCount * 16) return null;
        return Arrays.copyOf(dump, p.mifareBlockCount * 16);
    }

    private byte[] block(byte[] dump, int index) {
        return Arrays.copyOfRange(dump, index * 16, index * 16 + 16);
    }

    private int blockCountInSector(CardProfile p, int sector) {
        if (p.mifareSize == MifareClassic.SIZE_4K && sector > 31) return 16;
        return 4;
    }

    private int sectorToBlock(CardProfile p, int sector) {
        if (p.mifareSize == MifareClassic.SIZE_4K && sector > 31) {
            return 128 + (sector - 32) * 16;
        }
        return sector * 4;
    }

    private String cardInfo(CardProfile p) {
        return "UID: " + (TextUtils.isEmpty(p.uidHex) ? "unknown" : p.uidHex)
                + "\nATQA: " + (TextUtils.isEmpty(p.atqaHex) ? "unknown" : p.atqaHex)
                + "   SAK: " + (TextUtils.isEmpty(p.sakHex) ? "unknown" : p.sakHex)
                + "\nMode: " + p.modeLabel()
                + "\nMemory: " + p.mifareSize + " bytes, " + p.mifareSectorCount
                + " sectors, " + p.mifareBlockCount + " blocks"
                + "\nDump: " + (p.mifareDumpComplete ? "COMPLETE" : "PARTIAL " + p.mifareReadableSectors
                + "/" + p.mifareSectorCount + " sectors")
                + "\nBlock 0: " + p.mifareBlock0Hex
                + "\nKey entries: " + p.mifareSectorKeys.size()
                + "\nSector keys:\n" + p.mifareKeyMapText();
    }

    private String dumpPreview(CardProfile p) {
        String formatted = p.mifareDumpFormatted();
        if (formatted.length() > 1800) {
            formatted = formatted.substring(0, 1800) + "\n… (use COPY / SHARE for the full dump)";
        }
        return "Dump (" + p.mifareBlockCount + " blocks, 16 bytes each):\n" + formatted;
    }

    private String dumpReport(CardProfile p) {
        StringBuilder sb = new StringBuilder();
        sb.append("Aakash Saharanpur MIFARE Classic dump\n");
        sb.append("UID: ").append(p.uidHex).append('\n');
        sb.append("ATQA: ").append(p.atqaHex).append("  SAK: ").append(p.sakHex).append('\n');
        sb.append("Size: ").append(p.mifareSize).append(" bytes, sectors ")
                .append(p.mifareSectorCount).append(", blocks ").append(p.mifareBlockCount)
                .append('\n');
        sb.append("Dump complete: ").append(p.mifareDumpComplete ? "YES" : "NO").append('\n');
        sb.append("Block 0: ").append(p.mifareBlock0Hex).append('\n');
        sb.append("\nKey map (sector:type:key):\n").append(p.mifareKeyMapText()).append('\n');
        sb.append("\nDump (16 bytes per block):\n").append(p.mifareDumpFormatted()).append('\n');
        sb.append("\nUse only for your own card and with institute permission. "
                + "This is not proof of reader acceptance.\n");
        return sb.toString();
    }

    private void copyDump() {
        CardProfile p = store.loadProfile();
        if (p == null || TextUtils.isEmpty(p.mifareBlocksHex)) {
            Toast.makeText(this, "No MIFARE dump registered", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Aakash MIFARE dump", dumpReport(p)));
            Toast.makeText(this, "Full dump + key map copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareDump() {
        CardProfile p = store.loadProfile();
        if (p == null || TextUtils.isEmpty(p.mifareBlocksHex)) {
            Toast.makeText(this, "No MIFARE dump registered", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "Aakash MIFARE Classic dump");
        i.putExtra(Intent.EXTRA_TEXT, dumpReport(p));
        try {
            startActivity(Intent.createChooser(i, "Share dump"));
        } catch (Exception e) {
            Toast.makeText(this, "No app available to share text", Toast.LENGTH_SHORT).show();
        }
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

    private void closeQuietly(MifareClassic mc) {
        if (mc == null) return;
        try {
            mc.close();
        } catch (Exception ignored) {
        }
    }

    private String simpleError(Exception e) {
        String msg = e.getMessage();
        return e.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }

    private static final class SectorKeys {
        String keyA = "FFFFFFFFFFFF";
        String keyB = "FFFFFFFFFFFF";
    }
}
