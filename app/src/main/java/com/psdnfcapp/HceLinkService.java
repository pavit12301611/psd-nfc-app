package com.psdnfcapp;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;
import java.util.Arrays;

/**
 * HCE (Host Card Emulation) service: emulates an NFC tag whose NDEF content is
 * your encrypted link. Another phone tapping this phone reads the tag; only a
 * phone running this app with the same secret key can decrypt it.
 */
public class HceLinkService extends HostApduService {
    private static final String TAG = "HceLinkService";
    public static final byte[] AID = hexToBytes("F001020304050607");
    private static final byte[] SW_SUCCESS = {(byte) 0x90, 0x00};
    private static final byte[] SW_ERROR = {(byte) 0x6A, (byte) 0x82};
    private static final byte[] SW_NOT_INIT = {(byte) 0x6A, (byte) 0x88};

    private boolean selected = false;
    private byte[] ndefFile = null;

    /** Published by MainActivity before the user taps; served to the reader. */
    public static void setNdefFile(byte[] file) {
        currentFile = file;
    }

    private static volatile byte[] currentFile = null;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "HceLinkService created");
    }

    @Override
    public byte[] processCommandApdu(byte[] apdu, Bundle extras) {
        if (apdu == null || apdu.length < 4) {
            return SW_ERROR;
        }
        int cla = apdu[0] & 0xFF;
        int ins = apdu[1] & 0xFF;

        // SELECT AID: 00 A4 04 00 <lc> <AID>
        if (cla == 0x00 && ins == 0xA4 && apdu.length >= 5) {
            int p1 = apdu[2] & 0xFF;
            int lc = apdu[4] & 0xFF;
            if (p1 == 0x04 && apdu.length >= 5 + lc) {
                byte[] aid = Arrays.copyOfRange(apdu, 5, 5 + lc);
                if (Arrays.equals(aid, AID)) {
                    selected = true;
                    ndefFile = null;
                    return concat(selectFci(AID), SW_SUCCESS);
                }
            }
            return SW_ERROR;
        }
        if (!selected) {
            return SW_NOT_INIT;
        }

        // SELECT NDEF file by EF id: 00 A4 00 0C 02 E1 03
        if (cla == 0x00 && ins == 0xA4 && apdu.length >= 7) {
            int p1 = apdu[2] & 0xFF;
            int p2 = apdu[3] & 0xFF;
            int lc = apdu[4] & 0xFF;
            if (p1 == 0x00 && p2 == 0x0C && lc == 2 && apdu[5] == (byte) 0xE1 && apdu[6] == 0x03) {
                ndefFile = currentFile != null ? currentFile : new byte[]{(byte) 0x03, 0x01, (byte) 0xFE};
                return concat(fileFci(ndefFile.length), SW_SUCCESS);
            }
            return SW_ERROR;
        }

        // READ BINARY: 00 B0 <offset-high> <offset-low> <length>
        if (cla == 0x00 && ins == 0xB0 && ndefFile != null) {
            int off = ((apdu[2] & 0xFF) << 8) | (apdu[3] & 0xFF);
            int maxLen = (apdu.length >= 5) ? (apdu[4] & 0xFF) : 256;
            if (off >= ndefFile.length || maxLen <= 0) {
                return new byte[]{(byte) 0x6A, (byte) 0x83};
            }
            int len = Math.min(maxLen, ndefFile.length - off);
            byte[] chunk = Arrays.copyOfRange(ndefFile, off, off + len);
            return concat(chunk, SW_SUCCESS);
        }

        return SW_ERROR;
    }

    @Override
    public void onDeactivated(int reason) {
        Log.d(TAG, "HCE deactivated reason=" + reason);
        selected = false;
        ndefFile = null;
    }

    private static byte[] selectFci(byte[] aid) {
        int len = 2 + 1 + aid.length + 2 + 1 + 1 + 1;
        byte[] fci = new byte[2 + len];
        fci[0] = (byte) 0x6F;
        fci[1] = (byte) len;
        int i = 2;
        fci[i++] = (byte) 0x84;
        fci[i++] = (byte) aid.length;
        System.arraycopy(aid, 0, fci, i, aid.length);
        i += aid.length;
        fci[i++] = (byte) 0xA5;
        fci[i++] = 0x03;
        fci[i++] = 0x88;
        fci[i++] = 0x01;
        fci[i++] = 0x01;
        return fci;
    }

    private static byte[] fileFci(int fileSize) {
        byte[] fci = new byte[2 + 8];
        fci[0] = (byte) 0x6F;
        fci[1] = 0x08;
        fci[2] = (byte) 0x80;
        fci[3] = 0x04;
        fci[4] = (byte) ((fileSize >>> 8) & 0xFF);
        fci[5] = (byte) (fileSize & 0xFF);
        fci[6] = (byte) 0x8F;
        fci[7] = 0x01;
        fci[8] = 0x01;
        return fci;
    }

    private static byte[] concat(byte[] a, byte... b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] hexToBytes(String hex) {
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return b;
    }
}
