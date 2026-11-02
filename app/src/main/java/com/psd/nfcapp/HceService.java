package com.psd.nfcapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

/**
 * Host Card Emulation service — this is what makes the phone "always active" as a
 * transmitter: whenever the armed tile exists and the screen is on (or unlocked,
 * requireDeviceUnlock=false), any other NFC phone tapped back-to-back reads the
 * armed link / duplicated signal from this phone, exactly like a real NFC tag.
 *
 * Implements the NDEF Type-4 Tag application (AID D2760000850101) so standard
 * Android NFC readers (any phone) can read the payload without any app installed.
 */
public class HceService extends HostApduService {
    private static final byte[] AID_NDEF = NfcUtils.hexToBytes("D2760000850101");
    private static final byte[] FILE_CC = NfcUtils.hexToBytes("E103");
    private static final byte[] FILE_NDEF = NfcUtils.hexToBytes("E104");
    private static final byte[] SW_OK = NfcUtils.hexToBytes("9000");
    private static final byte[] SW_NOT_FOUND = NfcUtils.hexToBytes("6A82");

    private static volatile boolean loaded = false;
    private static volatile boolean enabled = false;
    private static volatile String armedId = null;
    private static volatile byte[] ndefBytes = null;

    private String currentFile = null;

    public static synchronized void refresh(Context ctx) {
        if (loaded) return;
        loaded = true;
        SharedPreferences p = ctx.getApplicationContext()
                .getSharedPreferences("psd_nfc_vault", Context.MODE_PRIVATE);
        enabled = p.getBoolean("hce_enabled", false);
        armedId = p.getString("armed_id", null);
        rebuild(ctx);
    }

    private static void rebuild(Context ctx) {
        ndefBytes = null;
        if (armedId != null) {
            NfcItem it = new VaultStore(ctx).get(armedId);
            if (it != null) {
                NdefMessage m = NfcUtils.buildMessage(it);
                if (m != null) ndefBytes = m.toByteArray();
            }
        }
    }

    /** Arm (id != null) or disarm (id == null) transmission. Persisted across reboots. */
    public static synchronized void setArmed(Context ctx, String id) {
        SharedPreferences p = ctx.getApplicationContext()
                .getSharedPreferences("psd_nfc_vault", Context.MODE_PRIVATE);
        p.edit()
                .putString("armed_id", id)
                .putBoolean("hce_enabled", id != null)
                .apply();
        armedId = id;
        enabled = id != null;
        loaded = true;
        rebuild(ctx);
    }

    public static boolean isActive() {
        return enabled && ndefBytes != null && ndefBytes.length > 0;
    }

    public static String getArmedId() {
        return armedId;
    }

    public static byte[] getNdefBytes() {
        return ndefBytes;
    }

    @Override
    public byte[] processCommandApdu(byte[] apdu, Bundle extras) {
        refresh(getApplicationContext());
        if (!isActive() || apdu == null || apdu.length < 4) return SW_NOT_FOUND;
        int ins = apdu[1] & 0xFF;
        if (ins == 0xA4) { // SELECT
            if (contains(apdu, AID_NDEF)) {
                currentFile = "AID";
                return NfcUtils.hexToBytes("6F0B8407D27600008501019000");
            }
            if (contains(apdu, FILE_CC)) {
                currentFile = "CC";
                return SW_OK;
            }
            if (contains(apdu, FILE_NDEF)) {
                currentFile = "NDEF";
                return SW_OK;
            }
            return SW_NOT_FOUND;
        } else if (ins == 0xB0) { // READ BINARY (handle P1P2 offset + Le so larger payloads read fully)
            byte[] data = "CC".equals(currentFile) ? ccFor(ndefBytes.length) : ndefBytes;
            int p1 = apdu[2] & 0xFF, p2 = apdu[3] & 0xFF;
            int off = ((p1 & 0x7F) << 8) | p2;
            int le = apdu.length >= 5 ? (apdu[4] & 0xFF) : 256;
            if (le == 0) le = 256;
            if (off >= data.length) return SW_NOT_FOUND;
            int end = Math.min(data.length, off + le);
            return concat(java.util.Arrays.copyOfRange(data, off, end), SW_OK);
        }
        return SW_NOT_FOUND;
    }

    @Override
    public void onDeactivated(int reason) {
        currentFile = null;
    }

    private static boolean contains(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    /** Type-4 Tag Capability Container for the current NDEF payload. */
    private static byte[] ccFor(int ndefLen) {
        int len = Math.max(0, Math.min(ndefLen, 0xFFFE));
        return new byte[]{
                0x00, 0x0F, 0x20, 0x00, (byte) 0xFF, 0x00, (byte) 0xFF,
                0x04, 0x06, (byte) 0xE1, 0x04,
                (byte) (len >> 8), (byte) (len & 0xFF),
                0x00, 0x00, 0x00, 0x00
        };
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
