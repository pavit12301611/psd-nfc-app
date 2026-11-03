package com.psd.nfcapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

import java.util.Arrays;

/**
 * Host Card Emulation service — makes THIS phone behave like a Type 4 NDEF tag so any
 * other NFC phone reads the armed link / duplicated signal natively (browser opens it).
 *
 * Implements the NFC Forum Type 4 Tag NDEF application (AID D2760000850101) with a
 * canonical 15-byte Capability Container, plus tolerant handling of the SELECT/READ
 * variants different phone vendors send (MF select, READ RECORD, offsets, Type 4 Tag AID).
 */
public class HceService extends HostApduService {
    private static final byte[] AID_NDEF = NfcUtils.hexToBytes("D2760000850101"); // NDEF application
    private static final byte[] AID_T4T = NfcUtils.hexToBytes("D2760000850100");   // Type 4 Tag AID (some readers probe it)
    private static final byte[] FILE_CC = NfcUtils.hexToBytes("E103");
    private static final byte[] FILE_NDEF = NfcUtils.hexToBytes("E104");
    private static final byte[] MF = NfcUtils.hexToBytes("3F00");
    private static final byte[] F2 = NfcUtils.hexToBytes("2F00");
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
                return fci(AID_NDEF);
            }
            if (contains(apdu, AID_T4T)) {
                currentFile = "AID";
                return fci(AID_T4T);
            }
            if (contains(apdu, FILE_CC)) {
                currentFile = "CC";
                return SW_OK;
            }
            if (contains(apdu, FILE_NDEF)) {
                currentFile = "NDEF";
                return SW_OK;
            }
            if (contains(apdu, MF) || contains(apdu, F2)) {
                currentFile = "MF";
                return SW_OK;
            }
            return SW_NOT_FOUND;
        } else if (ins == 0xB0 || ins == 0xB2) { // READ BINARY / READ RECORD (handle offset + Le)
            byte[] data = "CC".equals(currentFile) ? ccFor(ndefBytes.length) : ndefBytes;
            int p1 = apdu[2] & 0xFF, p2 = apdu[3] & 0xFF;
            int off = ((p1 & 0x7F) << 8) | p2;
            int le = apdu.length >= 5 ? (apdu[4] & 0xFF) : 256;
            if (le == 0) le = 256;
            if (off >= data.length) return SW_NOT_FOUND;
            int end = Math.min(data.length, off + le);
            return concat(Arrays.copyOfRange(data, off, end), SW_OK);
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

    /** Canonical 15-byte Type 4 Tag Capability Container (CCLEN = 0x000F). */
    private static byte[] ccFor(int ndefLen) {
        int len = Math.max(0, Math.min(ndefLen, 0xFFFE));
        return new byte[]{
                0x00, 0x0F,             // CCLEN
                0x20,                   // mapping version 2.0
                0x00, (byte) 0xFF,      // MLE
                0x00, (byte) 0xFF,      // MLU
                0x04, 0x06,             // NDEF File Control TLV
                (byte) 0xE1, 0x04,      // NDEF file ID
                (byte) (len >> 8), (byte) (len & 0xFF), // max NDEF size
                0x00, 0x00              // read access, write access
        };
    }

    /** FCI template for a successful SELECT by AID: 6F len 84 aidLen aid 9000. */
    private static byte[] fci(byte[] aid) {
        byte[] out = new byte[4 + aid.length + 2];
        out[0] = 0x6F;
        out[1] = (byte) (aid.length + 4);
        out[2] = (byte) 0x84;
        out[3] = (byte) aid.length;
        System.arraycopy(aid, 0, out, 4, aid.length);
        out[out.length - 2] = (byte) 0x90;
        out[out.length - 1] = 0x00;
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
