package com.psd.nfcapp;

import java.util.Arrays;

/**
 * Turns a reader's C-APDU into an R-APDU.
 *
 * Two modes:
 *  - Type 4 / NDEF: implement the NFC Forum Type 4 Tag application. The NDEF file is
 *    NLEN (2 bytes) + the exact captured NDEF message, and the CC advertises that file.
 *  - ISO-DEP / APDU: replay captured command/response rules for registered AIDs.
 *
 * Explicit rules are checked first so a reader-specific trace can override generic handling.
 * This class has no Android dependencies, which keeps the protocol logic testable.
 */
public final class ApduRouter {
    public static final byte[] SW_OK = NfcUtils.hexToBytes("9000");
    public static final byte[] SW_FILE_NOT_FOUND = NfcUtils.hexToBytes("6A82");
    public static final byte[] SW_WRONG_LENGTH = NfcUtils.hexToBytes("6700");
    public static final byte[] SW_WRONG_OFFSET = NfcUtils.hexToBytes("6B00");
    public static final byte[] SW_INS_NOT_SUPPORTED = NfcUtils.hexToBytes("6D00");
    public static final byte[] SW_CLASS_NOT_SUPPORTED = NfcUtils.hexToBytes("6E00");
    public static final byte[] SW_SECURITY = NfcUtils.hexToBytes("6982");
    public static final byte[] SW_CONDITIONS_NOT_SATISFIED = NfcUtils.hexToBytes("6985");
    public static final byte[] SW_UNKNOWN = NfcUtils.hexToBytes("6F00");
    public static final byte[] SW_WRONG_P1P2 = NfcUtils.hexToBytes("6A86");

    private static final byte[] AID_NDEF = NfcUtils.hexToBytes(HceRegistrar.AID_NDEF);
    private static final byte[] AID_T4T = NfcUtils.hexToBytes(HceRegistrar.AID_T4T);
    private static final byte[] FILE_CC = NfcUtils.hexToBytes("E103");
    private static final byte[] FILE_NDEF = NfcUtils.hexToBytes("E104");
    private static final byte[] MF = NfcUtils.hexToBytes("3F00");

    private ApduRouter() {
    }

    public static class Session {
        /** CC, NDEF, AID or MF. Defaulting to CC tolerates readers that skip SELECT CC. */
        public String selected = "CC";
        public byte[] lastFci;

        public void reset() {
            selected = "CC";
            lastFci = null;
        }
    }

    public static class Result {
        public final byte[] response;
        public final String note;

        public Result(byte[] response, String note) {
            this.response = response == null ? SW_UNKNOWN : response;
            this.note = note == null ? "" : note.replace('\n', ' ').replace('\r', ' ');
        }
    }

    public static Result route(CardProfile profile, byte[] apdu, Session session) {
        if (apdu == null || apdu.length < 4) {
            return new Result(SW_WRONG_LENGTH, "short C-APDU");
        }
        Session s = session == null ? new Session() : session;
        String cmdHex = NfcUtils.bytesToHex(apdu);

        // 1) Reader-specific static rules always win. They may contain wildcards/prefixes.
        if (profile != null && profile.rules != null) {
            for (int i = 0; i < profile.rules.size(); i++) {
                CardProfile.ApduRule rule = profile.rules.get(i);
                if (rule == null || !NfcUtils.ruleMatches(rule.commandHex, apdu)) continue;
                byte[] response = NfcUtils.hexToBytesStrict(rule.responseHex);
                if (response == null || response.length < 2) {
                    return new Result(SW_UNKNOWN, "rule " + (i + 1) + " has an invalid R-APDU");
                }
                String label = rule.note == null || rule.note.isEmpty()
                        ? "rule " + (i + 1) : rule.note;
                return new Result(response, "matched " + label);
            }
        }

        int cla = apdu[0] & 0xFF;
        int ins = apdu[1] & 0xFF;
        int p1 = apdu[2] & 0xFF;
        int p2 = apdu[3] & 0xFF;

        // 0x90 is the wrapped DESFire CLA; 0x80/0x84 appear in some ISO command sets.
        if (cla != 0x00 && cla != 0x80 && cla != 0x84 && cla != 0x90) {
            return new Result(SW_CLASS_NOT_SUPPORTED, "unsupported CLA " + NfcUtils.byteToHex(cla));
        }

        Parsed parsed = parseApdu(apdu);
        if (!parsed.valid) {
            return new Result(SW_WRONG_LENGTH, "malformed C-APDU " + cmdHex);
        }

        if (ins == 0xA4) return handleSelect(profile, parsed, s, p1, p2);
        if (ins == 0xC0) return handleGetResponse(parsed, s);
        if (ins == 0xB0 || ins == 0xB1 || ins == 0xB2) {
            return handleRead(profile, parsed, s, ins, p1, p2);
        }
        if (ins == 0xD6 || ins == 0xD7 || ins == 0xDC || ins == 0xD2) {
            return new Result(SW_SECURITY, "emulated card is read-only; write/update rejected");
        }
        if (profile == null || !profile.canEmulate()) {
            return new Result(SW_INS_NOT_SUPPORTED, "no emulatable card profile");
        }
        return new Result(SW_INS_NOT_SUPPORTED,
                "unhandled INS " + NfcUtils.byteToHex(ins) + " (" + cmdHex + ")");
    }

    private static Result handleSelect(CardProfile profile, Parsed parsed, Session s,
                                       int p1, int p2) {
        byte[] data = parsed.data;
        if (data.length == 0) {
            s.selected = "CC";
            return new Result(SW_OK, "SELECT current DF; default file = CC");
        }

        String name = NfcUtils.bytesToHex(data);
        boolean knownAid = Arrays.equals(data, AID_NDEF)
                || Arrays.equals(data, AID_T4T)
                || containsAid(profile, name);

        if (p1 == 0x04 || knownAid) {
            if (!knownAid) {
                s.selected = "CC";
                return new Result(SW_FILE_NOT_FOUND, "unregistered AID " + name);
            }
            if (data.length < 5 || data.length > 16) {
                return new Result(SW_WRONG_LENGTH, "invalid AID length " + data.length);
            }
            byte[] fci = fci(data);
            s.lastFci = fci;
            s.selected = "CC";
            return new Result(NfcUtils.concat(fci, SW_OK), "SELECT AID " + name);
        }

        if (Arrays.equals(data, FILE_CC)) {
            s.selected = "CC";
            return new Result(SW_OK, "SELECT CC file E103");
        }
        if (Arrays.equals(data, FILE_NDEF)) {
            s.selected = "NDEF";
            return new Result(SW_OK, "SELECT NDEF file E104");
        }
        if (Arrays.equals(data, MF)) {
            s.selected = "CC";
            return new Result(SW_OK, "SELECT MF 3F00; default file = CC");
        }
        if (data.length == 2) {
            // Tolerate a non-standard short file ID: treat E103 as CC, anything else as NDEF.
            if (Arrays.equals(data, FILE_CC)) {
                s.selected = "CC";
                return new Result(SW_OK, "SELECT short file " + name);
            }
            s.selected = "NDEF";
            return new Result(SW_OK, "SELECT short file " + name + " as NDEF");
        }
        s.selected = "CC";
        return new Result(SW_FILE_NOT_FOUND, "SELECT unknown " + name);
    }

    private static Result handleGetResponse(Parsed parsed, Session s) {
        if (s.lastFci == null || s.lastFci.length == 0) {
            return new Result(SW_FILE_NOT_FOUND, "GET RESPONSE with no pending FCI");
        }
        int le = clampLe(parsed);
        int end = Math.min(s.lastFci.length, Math.max(0, le));
        return new Result(NfcUtils.concat(Arrays.copyOfRange(s.lastFci, 0, end), SW_OK),
                "GET RESPONSE returned " + end + " FCI bytes");
    }

    private static Result handleRead(CardProfile profile, Parsed parsed, Session s,
                                     int ins, int p1, int p2) {
        byte[] cc = ccFor(profile);
        byte[] ndefFile = ndefFileFor(profile);
        boolean sfi = (p1 & 0x80) != 0;
        int offset = sfi ? p2 : (((p1 & 0x7F) << 8) | p2);
        if (ins == 0xB2) offset = p2; // READ RECORD: P1 is the record number, P2 the byte offset.

        byte[] data;
        if (sfi) {
            data = ndefFile;
        } else if ("CC".equals(s.selected)) {
            data = cc;
        } else if ("NDEF".equals(s.selected)) {
            data = ndefFile;
        } else {
            // Tolerant fallback for readers that omit SELECT CC / SELECT NDEF.
            data = (offset == 0 && parsed.le <= cc.length) ? cc : ndefFile;
        }

        if (data == null || data.length == 0) {
            return new Result(SW_FILE_NOT_FOUND,
                    (sfi ? "SFI" : s.selected) + " has no data for this profile");
        }
        if (offset < 0 || offset >= data.length) {
            return new Result(SW_WRONG_OFFSET,
                    "offset " + offset + " outside " + data.length + " byte file");
        }
        int le = clampLe(parsed);
        int end = Math.min(data.length, offset + le);
        byte[] out = Arrays.copyOfRange(data, offset, end);
        return new Result(NfcUtils.concat(out, SW_OK),
                "READ " + (sfi ? "SFI" : s.selected) + " off=" + offset + " len=" + out.length);
    }

    private static int clampLe(Parsed parsed) {
        int le = parsed.lePresent ? parsed.le : 256;
        if (le <= 0) le = 256;
        return Math.min(le, 256);
    }

    private static boolean containsAid(CardProfile profile, String aidHex) {
        if (profile == null || aidHex == null) return false;
        for (String aid : profile.aids) {
            String normalized = NfcUtils.normalizeAid(aid);
            if (normalized != null && normalized.equalsIgnoreCase(aidHex)) return true;
        }
        return false;
    }

    private static byte[] fci(byte[] aid) {
        // 6F <len> 84 <len> <AID> A5 00
        return NfcUtils.concat(
                new byte[]{0x6F, (byte) (aid.length + 4), (byte) 0x84, (byte) aid.length},
                aid,
                new byte[]{(byte) 0xA5, 0x00});
    }

    /** Exact CC from the card when available; otherwise a valid 15-byte read-only Type 4 CC. */
    private static byte[] ccFor(CardProfile profile) {
        if (profile != null) {
            byte[] exact = NfcUtils.hexToBytesStrict(profile.ccHex);
            if (exact != null && exact.length >= 15) return exact;
        }
        int fileLen = ndefFileFor(profile).length;
        int max = Math.max(0x0100, Math.min(0xFFFE, fileLen));
        return new byte[]{
                0x00, 0x0F,                         // CCLEN
                0x20,                               // mapping version 2.0
                0x00, (byte) 0xFF,                  // MLE
                0x00, (byte) 0xFF,                  // MLU
                0x04, 0x06,                         // NDEF File Control TLV
                (byte) 0xE1, 0x04,                  // NDEF file ID
                (byte) (max >> 8), (byte) (max & 0xFF),
                0x00, 0x00                          // read / write access: free, read-only here
        };
    }

    /** Exact captured NDEF file when valid; otherwise NLEN + exact NDEF bytes. */
    private static byte[] ndefFileFor(CardProfile profile) {
        if (profile == null) return new byte[0];
        byte[] exact = NfcUtils.hexToBytesStrict(profile.ndefFileHex);
        if (exact != null && exact.length >= 2) {
            int nlen = ((exact[0] & 0xFF) << 8) | (exact[1] & 0xFF);
            if (nlen == exact.length - 2) return exact;
        }
        byte[] ndef = NfcUtils.hexToBytesStrict(profile.ndefHex);
        if (ndef == null || ndef.length == 0) return new byte[0];
        if (ndef.length > 0xFFFE) ndef = Arrays.copyOf(ndef, 0xFFFE);
        byte[] out = new byte[ndef.length + 2];
        out[0] = (byte) ((ndef.length >> 8) & 0xFF);
        out[1] = (byte) (ndef.length & 0xFF);
        System.arraycopy(ndef, 0, out, 2, ndef.length);
        return out;
    }

    private static final class Parsed {
        byte[] data = new byte[0];
        int le = 256;
        boolean lePresent = false;
        boolean valid = true;
    }

    private static Parsed parseApdu(byte[] apdu) {
        Parsed p = new Parsed();
        int len = apdu.length;
        if (len == 4) return p;                 // case 1: no data, no Le
        int b4 = apdu[4] & 0xFF;
        if (len == 5) {                         // case 2S
            p.lePresent = true;
            p.le = b4 == 0 ? 256 : b4;
            return p;
        }
        if (b4 != 0) {                          // short Lc
            int lc = b4;
            int expected = 5 + lc;
            if (len == expected) {              // case 3S
                p.data = Arrays.copyOfRange(apdu, 5, 5 + lc);
                return p;
            }
            if (len == expected + 1) {          // case 4S
                p.data = Arrays.copyOfRange(apdu, 5, 5 + lc);
                p.lePresent = true;
                int le = apdu[len - 1] & 0xFF;
                p.le = le == 0 ? 256 : le;
                return p;
            }
            p.valid = false;
            return p;
        }
        // Extended Lc/Le.
        if (len == 7) {                         // case 2E
            p.lePresent = true;
            p.le = ((apdu[5] & 0xFF) << 8) | (apdu[6] & 0xFF);
            if (p.le == 0) p.le = 65536;
            return p;
        }
        if (len < 7) {
            p.valid = false;
            return p;
        }
        int lc = ((apdu[5] & 0xFF) << 8) | (apdu[6] & 0xFF);
        int expected = 7 + lc;
        if (len == expected) {                  // case 3E
            p.data = Arrays.copyOfRange(apdu, 7, 7 + lc);
            return p;
        }
        if (len == expected + 2) {              // case 4E
            p.data = Arrays.copyOfRange(apdu, 7, 7 + lc);
            p.lePresent = true;
            p.le = ((apdu[len - 2] & 0xFF) << 8) | (apdu[len - 1] & 0xFF);
            if (p.le == 0) p.le = 65536;
            return p;
        }
        if (len == expected + 1) {              // non-standard extended case with 1-byte Le
            p.data = Arrays.copyOfRange(apdu, 7, 7 + lc);
            p.lePresent = true;
            int le = apdu[len - 1] & 0xFF;
            p.le = le == 0 ? 256 : le;
            return p;
        }
        p.valid = false;
        return p;
    }
}
