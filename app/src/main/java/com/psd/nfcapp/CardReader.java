package com.psd.nfcapp;

import android.nfc.NdefMessage;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.nfc.tech.TagTechnology;
import android.text.TextUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Registers a physical card: captures everything Android HCE can reproduce, and records
 * diagnostics for everything it cannot (UID, MIFARE Classic sectors, low-level parameters).
 * Runs on the reader-mode callback thread, so tag I/O is allowed here.
 */
public final class CardReader {
    private static final String[] DEFAULT_KEYS = {
            "FFFFFFFFFFFF", "A0A1A2A3A4A5", "B0B1B2B3B4B5", "D3F7D3F7D3F7",
            "000000000000", "AABBCCDDEEFF", "123456789ABC", "A0B0C0D0E0F0"
    };

    private CardReader() {
    }

    public static CardProfile read(Tag tag) {
        CardProfile p = new CardProfile();
        p.uidHex = NfcUtils.bytesToHex(tag.getId());
        p.techList = friendlyTechList(tag.getTechList());

        captureLowLevel(tag, p);
        captureNdef(tag, p);
        captureMifareClassic(tag, p);
        captureUltralight(tag, p);
        captureIsoDep(tag, p);
        determineMode(p);
        addFinalNotes(p);
        return p;
    }

    private static void captureLowLevel(Tag tag, CardProfile p) {
        try {
            NfcA a = NfcA.get(tag);
            if (a != null) {
                p.atqaHex = NfcUtils.bytesToHex(a.getAtqa());
                p.sakHex = NfcUtils.byteToHex(a.getSak() & 0xFF);
            }
        } catch (Exception ignored) {
        }
        try {
            NfcB b = NfcB.get(tag);
            if (b != null) {
                p.appDataHex = NfcUtils.bytesToHex(b.getApplicationData());
                p.protocolInfoHex = NfcUtils.bytesToHex(b.getProtocolInfo());
            }
        } catch (Exception ignored) {
        }
        try {
            NfcF f = NfcF.get(tag);
            if (f != null) {
                p.manufacturerHex = NfcUtils.bytesToHex(f.getManufacturer());
                p.systemCodeHex = NfcUtils.bytesToHex(f.getSystemCode());
            }
        } catch (Exception ignored) {
        }
        try {
            NfcV v = NfcV.get(tag);
            if (v != null) p.dsfIdHex = NfcUtils.byteToHex(v.getDsfId() & 0xFF);
        } catch (Exception ignored) {
        }
        try {
            IsoDep iso = IsoDep.get(tag);
            if (iso != null) {
                p.historicalBytesHex = NfcUtils.bytesToHex(iso.getHistoricalBytes());
                p.isoDepMaxTransceive = iso.getMaxTransceiveLength();
            }
        } catch (Exception ignored) {
        }
    }

    private static void captureNdef(Tag tag, CardProfile p) {
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) return;
        try {
            ndef.connect();
            NdefMessage msg = ndef.getNdefMessage();
            if (msg != null) {
                byte[] raw = msg.toByteArray();
                p.ndefHex = NfcUtils.bytesToHex(raw);
                p.addNote("NDEF message captured: " + raw.length + " bytes, "
                        + msg.getRecords().length + " record(s)");
            } else {
                p.addNote("NDEF technology present but no NDEF message was stored on the card");
            }
            p.ndefType = ndef.getType();
            p.ndefMaxSize = ndef.getMaxSize();
            p.ndefWritable = ndef.isWritable();
        } catch (Exception e) {
            p.addNote("NDEF read: " + simpleError(e));
        } finally {
            closeQuietly(ndef);
        }
    }

    private static void captureMifareClassic(Tag tag, CardProfile p) {
        MifareClassic mc = MifareClassic.get(tag);
        if (mc == null) return;
        try {
            mc.connect();
            p.mifareType = classicType(mc.getType());
            p.mifareSectorCount = mc.getSectorCount();
            p.mifareBlockCount = mc.getBlockCount();
            p.mifareSize = mc.getSize();
            p.addNote("MIFARE Classic detected: " + p.mifareType + ", " + p.mifareSize + " bytes");

            outer:
            for (String keyHex : DEFAULT_KEYS) {
                byte[] key = NfcUtils.hexToBytes(keyHex);
                for (int which = 0; which < 2; which++) {
                    try {
                        boolean ok = which == 0
                                ? mc.authenticateSectorWithKeyA(0, key)
                                : mc.authenticateSectorWithKeyB(0, key);
                        if (!ok) continue;
                        StringBuilder sb = new StringBuilder();
                        int blocks = mc.getBlockCountInSector(0);
                        for (int b = 0; b < blocks; b++) {
                            sb.append(NfcUtils.bytesToHex(mc.readBlock(b)));
                        }
                        p.mifareBlocksHex = sb.toString();
                        p.addNote("Sector 0 read with " + (which == 0 ? "Key A" : "Key B")
                                + " " + keyHex + " (diagnostic only; HCE cannot replay it)");
                        break outer;
                    } catch (Exception ignored) {
                    }
                }
            }
            if (TextUtils.isEmpty(p.mifareBlocksHex)) {
                p.addNote("MIFARE Classic sector 0 could not be authenticated with common default keys");
            }
        } catch (Exception e) {
            p.addNote("MIFARE Classic read: " + simpleError(e));
        } finally {
            closeQuietly(mc);
        }
    }

    private static void captureUltralight(Tag tag, CardProfile p) {
        MifareUltralight mu = MifareUltralight.get(tag);
        if (mu == null) return;
        try {
            mu.connect();
            p.ultralightType = ultralightType(mu.getType());
            p.ultralightPagesHex = NfcUtils.bytesToHex(mu.readPages(0));
            p.addNote("MIFARE Ultralight / Type 2 pages 0-3 captured (diagnostic only)");
        } catch (Exception e) {
            p.addNote("MIFARE Ultralight read: " + simpleError(e));
        } finally {
            closeQuietly(mu);
        }
    }

    private static void captureIsoDep(Tag tag, CardProfile p) {
        if (!hasTech(p.techList, "IsoDep")) return;
        List<String[]> probes = probeCommands();
        int failures = 0;
        for (String[] probe : probes) {
            byte[] cmd = NfcUtils.hexToBytes(probe[0]);
            String note = probe[1];
            IsoDep iso = IsoDep.get(tag);
            if (iso == null) break;
            try {
                iso.connect();
                p.isoDepMaxTransceive = Math.max(p.isoDepMaxTransceive, iso.getMaxTransceiveLength());
                byte[] response = iso.transceive(cmd);
                if (response != null && response.length >= 2) {
                    p.addRule(probe[0], NfcUtils.bytesToHex(response), note);
                    if (isSelectAid(cmd)) {
                        byte[] aid = selectAidFromCommand(cmd);
                        if (aid != null) {
                            p.addAid(NfcUtils.bytesToHex(aid));
                            p.addNote("Card accepted AID " + NfcUtils.bytesToHex(aid));
                        }
                    }
                } else {
                    failures++;
                }
            } catch (Exception e) {
                failures++;
            } finally {
                closeQuietly(iso);
            }
        }
        if (failures > 0) p.addNote(failures + " ISO-DEP probe command(s) were rejected or timed out");
        readExactType4File(tag, p);
    }

    private static List<String[]> probeCommands() {
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{"00A4040007D276000085010100", "SELECT NDEF AID"});
        out.add(new String[]{"00A4040007D276000085010000", "SELECT Type 4 / DESFire AID"});
        out.add(new String[]{"00A4000C02E10300", "SELECT CC file E103"});
        out.add(new String[]{"00B000000F", "READ CC"});
        out.add(new String[]{"00A4000C02E10400", "SELECT NDEF file E104"});
        out.add(new String[]{"00B0000002", "READ NLEN"});
        out.add(new String[]{"00A40000023F0000", "SELECT MF 3F00"});
        out.add(new String[]{"9060000000", "DESFire GET VERSION"});
        out.add(new String[]{"906A000000", "DESFire GET APPLICATION IDS"});
        out.add(new String[]{"905A00000300000000", "DESFire SELECT PICC APPLICATION"});
        out.add(new String[]{"00C0000000", "ISO GET RESPONSE"});
        return out;
    }

    /** Reads the exact Type 4 NDEF file (NLEN + NDEF) when the card supports the standard flow. */
    private static void readExactType4File(Tag tag, CardProfile p) {
        IsoDep iso = IsoDep.get(tag);
        if (iso == null) return;
        try {
            iso.connect();
            byte[] r = loggedTransceive(iso, "00A4040007D276000085010100", p, "SELECT NDEF AID (exact read)");
            if (!swOk(r)) return;
            r = loggedTransceive(iso, "00A4000C02E10300", p, "SELECT CC (exact read)");
            if (!swOk(r)) return;
            byte[] ccResp = loggedTransceive(iso, "00B000000F", p, "READ CC (exact read)");
            if (!swOk(ccResp)) return;
            byte[] cc = stripSw(ccResp);
            if (cc.length >= 15) {
                p.ccHex = NfcUtils.bytesToHex(cc);
                p.addNote("Exact Type 4 Capability Container captured (" + cc.length + " bytes)");
            }
            String fileIdHex = parseNdefFileId(cc);
            byte[] fileId = NfcUtils.hexToBytes(fileIdHex);
            if (fileId.length != 2) return;
            byte[] selectFile = new byte[]{0x00, (byte) 0xA4, 0x00, 0x0C, 0x02,
                    fileId[0], fileId[1], 0x00};
            r = loggedTransceive(iso, selectFile, p, "SELECT NDEF file " + fileIdHex + " (exact read)");
            if (!swOk(r)) return;
            byte[] nlenResp = loggedTransceive(iso, "00B0000002", p, "READ NLEN (exact read)");
            if (!swOk(nlenResp)) return;
            byte[] nlenBytes = stripSw(nlenResp);
            if (nlenBytes.length < 2) return;
            int nlen = ((nlenBytes[0] & 0xFF) << 8) | (nlenBytes[1] & 0xFF);
            if (nlen < 0 || nlen > 8192) {
                p.addNote("NDEF length " + nlen + " too large; exact file capture skipped");
                return;
            }
            byte[] file = new byte[2 + nlen];
            file[0] = nlenBytes[0];
            file[1] = nlenBytes[1];
            int off = 2;
            int maxLe = Math.min(0xF0, Math.max(16, iso.getMaxTransceiveLength()));
            while (off < 2 + nlen) {
                int le = Math.min(maxLe, 2 + nlen - off);
                byte[] cmd = new byte[]{0x00, (byte) 0xB0,
                        (byte) ((off >> 8) & 0x7F), (byte) (off & 0xFF), (byte) le};
                byte[] resp = loggedTransceive(iso, cmd, p, "READ NDEF @" + off + " (exact read)");
                if (!swOk(resp)) return;
                byte[] chunk = stripSw(resp);
                if (chunk.length == 0) return;
                int take = Math.min(chunk.length, 2 + nlen - off);
                System.arraycopy(chunk, 0, file, off, take);
                off += take;
                if (chunk.length < le) break;
            }
            p.ndefFileHex = NfcUtils.bytesToHex(file);
            if (nlen > 0) p.ndefHex = NfcUtils.bytesToHex(Arrays.copyOfRange(file, 2, 2 + nlen));
            p.addNote("Exact Type 4 NDEF file captured (" + file.length + " bytes incl NLEN)");
        } catch (Exception e) {
            p.addNote("Exact Type 4 read: " + simpleError(e));
        } finally {
            closeQuietly(iso);
        }
    }

    private static byte[] loggedTransceive(IsoDep iso, String cmdHex, CardProfile p, String note)
            throws IOException {
        return loggedTransceive(iso, NfcUtils.hexToBytes(cmdHex), p, note);
    }

    private static byte[] loggedTransceive(IsoDep iso, byte[] cmd, CardProfile p, String note)
            throws IOException {
        byte[] response = iso.transceive(cmd);
        if (response != null && response.length >= 2) {
            p.addRule(NfcUtils.bytesToHex(cmd), NfcUtils.bytesToHex(response), note);
        }
        return response;
    }

    private static void determineMode(CardProfile p) {
        boolean isoDep = hasTech(p.techList, "IsoDep");
        boolean ndef = !TextUtils.isEmpty(p.ndefHex) || !TextUtils.isEmpty(p.ndefFileHex);
        boolean classic = hasTech(p.techList, "MifareClassic");
        boolean ultralight = hasTech(p.techList, "MifareUltralight");
        boolean otherLowLevel = hasTech(p.techList, "NfcB")
                || hasTech(p.techList, "NfcF") || hasTech(p.techList, "NfcV");

        if (ndef) {
            p.mode = CardProfile.MODE_NDEF;
        } else if (isoDep && !p.rules.isEmpty()) {
            p.mode = CardProfile.MODE_APDU;
        } else if (classic) {
            p.mode = CardProfile.MODE_MIFARE_CLASSIC;
        } else if (ultralight) {
            p.mode = CardProfile.MODE_MIFARE_ULTRALIGHT;
        } else if (otherLowLevel) {
            p.mode = CardProfile.MODE_UNSUPPORTED;
        } else {
            p.mode = CardProfile.MODE_UID_ONLY;
        }
    }

    private static void addFinalNotes(CardProfile p) {
        p.addNote("Android HCE always presents a random UID and fixed ATS; the physical card's "
                + "UID / ATQA / SAK / ATS cannot be reproduced.");
        if (hasTech(p.techList, "MifareClassic") || hasTech(p.techList, "MifareUltralight")) {
            p.addNote("NDEF was found on a low-level tag; only a Type 4/NDEF reader can consume "
                    + "the emulated version. A Classic/Ultralight-only reader will not.");
        }
        if (CardProfile.MODE_APDU.equals(p.mode)) {
            p.addNote("Captured APDU responses are static. Readers using random challenges, "
                    + "session keys, counters or additional-frame (91AF) crypto cannot be cloned.");
        }
        p.addNote("No attendance is recorded by this app; only the institute's reader can record it.");
    }

    private static String friendlyTechList(String[] techs) {
        List<String> out = new ArrayList<>();
        for (String t : techs) out.add(simpleName(t));
        return TextUtils.join(", ", out);
    }

    private static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
    }

    private static boolean hasTech(String techList, String simple) {
        if (techList == null) return false;
        for (String t : techList.split(",")) {
            if (t.trim().equalsIgnoreCase(simple)) return true;
        }
        return false;
    }

    private static String classicType(int type) {
        if (type == MifareClassic.TYPE_CLASSIC) return "MIFARE Classic 1K/4K";
        if (type == MifareClassic.TYPE_PLUS) return "MIFARE Plus";
        if (type == MifareClassic.TYPE_PRO) return "MIFARE Pro";
        return "unknown";
    }

    private static String ultralightType(int type) {
        if (type == MifareUltralight.TYPE_ULTRALIGHT) return "MIFARE Ultralight";
        if (type == MifareUltralight.TYPE_ULTRALIGHT_C) return "MIFARE Ultralight C";
        return "unknown";
    }

    private static String parseNdefFileId(byte[] cc) {
        if (cc == null || cc.length < 15) return "E104";
        int i = 7;
        while (i + 1 < cc.length) {
            int t = cc[i] & 0xFF;
            int l = cc[i + 1] & 0xFF;
            if (l == 0) break;
            if (t == 0x04 && l >= 6 && i + 2 + l <= cc.length) {
                return NfcUtils.bytesToHex(Arrays.copyOfRange(cc, i + 2, i + 4));
            }
            i += 2 + l;
        }
        return "E104";
    }

    private static boolean isSelectAid(byte[] cmd) {
        return cmd != null && cmd.length >= 5 && (cmd[1] & 0xFF) == 0xA4 && (cmd[2] & 0xFF) == 0x04;
    }

    private static byte[] selectAidFromCommand(byte[] cmd) {
        if (!isSelectAid(cmd)) return null;
        int lc = cmd[4] & 0xFF;
        if (lc == 0 || lc > 16 || cmd.length < 5 + lc) return null;
        return Arrays.copyOfRange(cmd, 5, 5 + lc);
    }

    private static boolean swOk(byte[] response) {
        return response != null && response.length >= 2
                && (response[response.length - 2] & 0xFF) == 0x90
                && (response[response.length - 1] & 0xFF) == 0x00;
    }

    private static byte[] stripSw(byte[] response) {
        if (response == null || response.length < 2) return new byte[0];
        return Arrays.copyOfRange(response, 0, response.length - 2);
    }

    private static void closeQuietly(TagTechnology t) {
        if (t == null) return;
        try {
            t.close();
        } catch (Exception ignored) {
        }
    }

    private static String simpleError(Exception e) {
        String msg = e.getMessage();
        return e.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }
}
