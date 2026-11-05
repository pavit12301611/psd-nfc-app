package com.psd.nfcapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * One registered physical ID card. It stores what Android can actually reproduce:
 * exact NDEF bytes for a real Type 2 sticker or a Type 4 HCE fallback, plus
 * diagnostics about what it cannot reproduce (fixed UID, NTAG originality signature,
 * NFC counter, low-level Type 2 commands).
 */
public class CardProfile {
    public static final String SINGLE_CARD_ID = "aakash_primary_card";

    public static final String MODE_NDEF = "ndef";
    public static final String MODE_TYPE2_NDEF = "type2_ndef";
    public static final String MODE_APDU = "apdu";
    public static final String MODE_MIFARE_CLASSIC = "mifare_classic";
    public static final String MODE_MIFARE_ULTRALIGHT = "mifare_ultralight";
    public static final String MODE_UID_ONLY = "uid_only";
    public static final String MODE_UNSUPPORTED = "unsupported";

    public static class ApduRule {
        /** C-APDU hex. Supports "??" wildcard bytes and trailing "*" prefix match. */
        public String commandHex;
        /** Full R-APDU hex, including the two status-word bytes. */
        public String responseHex;
        public String note;

        public ApduRule() {
        }

        public ApduRule(String commandHex, String responseHex, String note) {
            this.commandHex = commandHex == null ? "" : commandHex;
            this.responseHex = responseHex == null ? "" : responseHex;
            this.note = note == null ? "" : note;
        }

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("command", commandHex == null ? "" : commandHex);
                o.put("response", responseHex == null ? "" : responseHex);
                o.put("note", note == null ? "" : note);
            } catch (Exception ignored) {
            }
            return o;
        }

        static ApduRule fromJson(JSONObject o) {
            if (o == null) return null;
            return new ApduRule(o.optString("command"), o.optString("response"), o.optString("note"));
        }
    }

    public String id = SINGLE_CARD_ID;
    public String cardLabel;
    public String studentId;
    public String uidHex;
    public String techList;
    public String atqaHex;
    public String sakHex;
    public String atsHex;
    public String historicalBytesHex;
    public String appDataHex;
    public String protocolInfoHex;
    public String manufacturerHex;
    public String systemCodeHex;
    public String dsfIdHex;

    public String ndefHex;
    public String ndefFileHex;
    public String ccHex;
    public String ndefType;
    public int ndefMaxSize;
    public int ndefPayloadLength;
    public boolean ndefWritable;

    /** Parsed text records from the exact NDEF message. */
    public List<String> ndefTexts = new ArrayList<>();
    public String aakashId;
    public String psidGuess;
    public String expectedBranchPrefix = BranchInfo.EXPECTED_NDEF_PREFIX;
    public String branchCity = BranchInfo.CITY;
    public String centreCode = BranchInfo.AIATS_CENTRE_CODE;
    public boolean aakashNdefDetected;
    public boolean type2Detected;

    public String mifareType;
    public int mifareSectorCount;
    public int mifareBlockCount;
    public int mifareSize;
    public String mifareBlocksHex;
    public String mifareBlock0Hex;
    public int mifareReadableSectors;
    public boolean mifareDumpComplete;
    /** Entries: "sector:keyType:keyHex", e.g. "0:A:FFFFFFFFFFFF". */
    public List<String> mifareSectorKeys = new ArrayList<>();

    public String ultralightType;
    public String ultralightPagesHex;
    public String ntagVersionHex;
    public String type2PagesHex;
    public String type2CcHex;
    public String originalitySignatureHex;
    public String nfcCounterHex;
    public int isoDepMaxTransceive;

    public List<String> aids = new ArrayList<>();
    public List<ApduRule> rules = new ArrayList<>();
    public List<String> notes = new ArrayList<>();

    public String mode = MODE_UNSUPPORTED;
    public long registeredAt = System.currentTimeMillis();
    public long updatedAt = registeredAt;

    /** Android HCE can only emulate ISO-DEP / Type 4. Type 2 / NTAG is never HCE-emulatable. */
    public boolean canEmulate() {
        return MODE_NDEF.equals(mode) || MODE_APDU.equals(mode);
    }

    /** A Type 4 NDEF fallback is possible only when exact NDEF bytes were captured. */
    public boolean canHceFallback() {
        if (ndefHex == null || ndefHex.isEmpty()) return false;
        if (MODE_TYPE2_NDEF.equals(mode) || MODE_NDEF.equals(mode)) return true;
        return ndefType != null && ndefType.contains("type4");
    }

    /** A real Type 2 sticker can carry the exact NDEF bytes. */
    public boolean canWriteSticker() {
        return MODE_TYPE2_NDEF.equals(mode) && ndefHex != null && !ndefHex.isEmpty();
    }

    /** A full MIFARE Classic dump can be written to a UID-changeable Gen2/CUID magic card. */
    public boolean canWriteMagicCard() {
        return MODE_MIFARE_CLASSIC.equals(mode)
                && mifareDumpComplete
                && mifareBlocksHex != null
                && mifareBlockCount > 0
                && mifareBlocksHex.length() >= mifareBlockCount * 32
                && mifareBlock0Hex != null
                && NfcUtils.isValidMifareBlock0(mifareBlock0Hex,
                        uidHex == null || uidHex.isEmpty() ? 4 : uidHex.length() / 2);
    }

    public String mifareKeyMapText() {
        if (mifareSectorKeys.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (String k : mifareSectorKeys) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(k);
        }
        return sb.toString();
    }

    public String sectorKey(int sector, String type) {
        for (String entry : mifareSectorKeys) {
            String[] parts = entry.split(":");
            if (parts.length == 3
                    && parts[0].equals(String.valueOf(sector))
                    && parts[1].equalsIgnoreCase(type)
                    && parts[2].length() == 12) {
                return parts[2];
            }
        }
        return "";
    }

    public String mifareDumpFormatted() {
        if (mifareBlocksHex == null || mifareBlockCount <= 0) return "";
        StringBuilder sb = new StringBuilder();
        int max = Math.min(mifareBlockCount, mifareBlocksHex.length() / 32);
        for (int b = 0; b < max; b++) {
            sb.append(String.format(Locale.US, "B%02d: %s", b,
                    mifareBlocksHex.substring(b * 32, b * 32 + 32)));
            if (b < max - 1) sb.append('\n');
        }
        return sb.toString();
    }

    public String modeLabel() {
        if (MODE_TYPE2_NDEF.equals(mode)) return "Type 2 / NTAG / NDEF";
        if (MODE_NDEF.equals(mode)) return "Type 4 / NDEF";
        if (MODE_APDU.equals(mode)) return "ISO-DEP / APDU";
        if (MODE_MIFARE_CLASSIC.equals(mode)) {
            String type = mifareType == null || mifareType.isEmpty() ? "MIFARE Classic" : mifareType;
            return type + " / Crypto1" + (mifareDumpComplete ? " (full dump)" : "");
        }
        if (MODE_MIFARE_ULTRALIGHT.equals(mode)) return "MIFARE Ultralight";
        if (MODE_UID_ONLY.equals(mode)) return "UID / ISO 14443-3 only";
        return "Unsupported protocol";
    }

    public String compatibilityText() {
        if (MODE_TYPE2_NDEF.equals(mode)) {
            return "NOT EMULATABLE BY ANDROID HCE. This is an NFC Forum Type 2 / NTAG card. "
                    + "Android can READ and WRITE it, but stock Android cannot EMULATE it: "
                    + "a Type-2 reader sends READ / FAST_READ page commands on ISO 14443-3A, "
                    + "while HostApduService only receives ISO-DEP / ISO 7816-4 APDUs. "
                    + "Workable paths: (1) use the physical card; (2) write the exact NDEF "
                    + "to a blank NTAG213/215/216 sticker attached to the phone — only if the "
                    + "reader checks NDEF text and NOT the 7-byte UID, ECC originality signature "
                    + "or NFC counter; (3) use an external Type-2 emulator such as ChameleonUltra; "
                    + "(4) ask the institute to enroll a Type-4 / HCE credential. "
                    + "The Type-4 HCE fallback in this app is for readers that also support Type 4 "
                    + "and ignore UID; a dedicated Type-2 attendance reader will not see it.";
        }
        if (MODE_NDEF.equals(mode)) {
            return "CONDITIONAL: the app can serve the captured NDEF bytes as a correct NFC Forum "
                    + "Type 4 tag. This works only if the attendance reader speaks Type 4/NDEF and "
                    + "does not require the card's fixed UID, MIFARE Classic sectors, or a live "
                    + "cryptographic challenge.";
        }
        if (MODE_APDU.equals(mode)) {
            return "CONDITIONAL: the app can replay captured ISO-DEP APDU responses for registered "
                    + "AIDs. This works only if the reader starts with SELECT AID, accepts the "
                    + "registered AID, and does not require a fixed UID, Crypto1, or a fresh "
                    + "challenge-response. Static APDU replay is not a cryptographic clone.";
        }
        if (MODE_MIFARE_CLASSIC.equals(mode)) {
            String dump = mifareDumpComplete
                    ? "Full " + (mifareBlocksHex == null ? 0 : mifareBlocksHex.length() / 2)
                    + "-byte dump and sector keys captured."
                    : "Only " + mifareReadableSectors + "/" + mifareSectorCount
                    + " sectors could be read with common default keys; recover the rest with "
                    + "MIFARE Classic Tool / Proxmark before cloning.";
            return "NOT EMULATABLE on stock Android HCE. MIFARE Classic uses ISO 14443-3 + Crypto1, "
                    + "a fixed " + (uidHex == null || uidHex.isEmpty() ? 4 : uidHex.length() / 2)
                    + "-byte UID, ATQA/SAK and per-sector keys, not ISO-DEP/ISO 7816-4 APDUs. "
                    + "Android cannot reproduce the UID or Crypto1 authentication. " + dump
                    + " Workable paths: write the full dump to a Gen2/CUID UID-changeable MIFARE "
                    + "Classic 1K magic card (block 0 writable), or use an authorised external "
                    + "emulator (ChameleonUltra / Proxmark / Flipper). A normal blank MIFARE Classic "
                    + "card has a read-only block 0, and an NTAG/Type-2 sticker is the wrong protocol. "
                    + "A reader may check only the UID, or authenticate sector data; both require the "
                    + "original UID/keys. HCE here is diagnostics only. Official Aakash pages say "
                    + "attendance can be pulled from Barcode / RFID / biometric machines, but no "
                    + "public source names this branch's reader; for your card NFC punching was "
                    + "confirmed, so the printed barcode is not used as a credential by this app.";
        }
        if (MODE_MIFARE_ULTRALIGHT.equals(mode)) {
            return "NOT EMULATABLE on stock Android: MIFARE Ultralight / Type 2 is a low-level "
                    + "ISO 14443-3 protocol. Android HCE cannot reproduce its UID or page commands.";
        }
        if (MODE_UID_ONLY.equals(mode)) {
            return "NOT EMULATABLE on stock Android: only a UID / anti-collision identity was seen. "
                    + "Android HCE presents a random UID on every tap and cannot set a fixed one.";
        }
        return "NOT EMULATABLE: no NFC Forum Type 4 / ISO-DEP application was detected. "
                + "If the card is a barcode, QR code or 125 kHz proximity card, Android NFC "
                + "(13.56 MHz) cannot read or emulate it at all. NDEF on Type 1/3/5 also cannot "
                + "be presented by Android HCE.";
    }

    public String recommendedActionText() {
        if (MODE_TYPE2_NDEF.equals(mode)) {
            return "Use WRITE NTAG STICKER, or the physical card. HCE fallback is only a "
                    + "diagnostic / Type-4-reader fallback.";
        }
        if (MODE_MIFARE_CLASSIC.equals(mode)) {
            return canWriteMagicCard()
                    ? "Use WRITE MIFARE MAGIC CARD (Gen2/CUID) or an authorised external emulator; HCE is diagnostics only."
                    : "Recover all sector keys first, then use a Gen2/CUID magic card or external emulator.";
        }
        if (canEmulate()) {
            return "Start HCE punch mode and check the APDU log.";
        }
        return "Use the physical card, or ask the institute for a phone-compatible credential.";
    }

    public String displayName() {
        if (cardLabel != null && !cardLabel.trim().isEmpty()) return cardLabel.trim();
        if (aakashId != null && !aakashId.trim().isEmpty()) return aakashId.trim();
        return "Aakash " + BranchInfo.CITY + " ID card";
    }

    public String aidListText() {
        if (aids.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (String aid : aids) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(aid);
        }
        return sb.toString();
    }

    public String ndefTextListText() {
        if (ndefTexts.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (String t : ndefTexts) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(t.length() > 60 ? t.substring(0, 60) + "…" : t);
        }
        return sb.toString();
    }

    public void addAid(String aid) {
        String normalized = NfcUtils.normalizeAid(aid);
        if (normalized != null && !aids.contains(normalized)) aids.add(normalized);
    }

    public void addRule(String commandHex, String responseHex, String note) {
        if (commandHex == null || responseHex == null) return;
        for (ApduRule r : rules) {
            if (r.commandHex != null && r.commandHex.equalsIgnoreCase(commandHex)
                    && r.responseHex != null && r.responseHex.equalsIgnoreCase(responseHex)) {
                return;
            }
        }
        rules.add(new ApduRule(commandHex, responseHex, note));
    }

    public void addNote(String note) {
        if (note != null && !note.isEmpty() && !notes.contains(note)) notes.add(note);
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id == null ? SINGLE_CARD_ID : id);
            o.put("cardLabel", str(cardLabel));
            o.put("studentId", str(studentId));
            o.put("uidHex", str(uidHex));
            o.put("techList", str(techList));
            o.put("atqaHex", str(atqaHex));
            o.put("sakHex", str(sakHex));
            o.put("atsHex", str(atsHex));
            o.put("historicalBytesHex", str(historicalBytesHex));
            o.put("appDataHex", str(appDataHex));
            o.put("protocolInfoHex", str(protocolInfoHex));
            o.put("manufacturerHex", str(manufacturerHex));
            o.put("systemCodeHex", str(systemCodeHex));
            o.put("dsfIdHex", str(dsfIdHex));
            o.put("ndefHex", str(ndefHex));
            o.put("ndefFileHex", str(ndefFileHex));
            o.put("ccHex", str(ccHex));
            o.put("ndefType", str(ndefType));
            o.put("ndefMaxSize", ndefMaxSize);
            o.put("ndefPayloadLength", ndefPayloadLength);
            o.put("ndefWritable", ndefWritable);
            o.put("aakashId", str(aakashId));
            o.put("psidGuess", str(psidGuess));
            o.put("expectedBranchPrefix", str(expectedBranchPrefix));
            o.put("branchCity", str(branchCity));
            o.put("centreCode", str(centreCode));
            o.put("aakashNdefDetected", aakashNdefDetected);
            o.put("type2Detected", type2Detected);
            o.put("mifareType", str(mifareType));
            o.put("mifareSectorCount", mifareSectorCount);
            o.put("mifareBlockCount", mifareBlockCount);
            o.put("mifareSize", mifareSize);
            o.put("mifareBlocksHex", str(mifareBlocksHex));
            o.put("mifareBlock0Hex", str(mifareBlock0Hex));
            o.put("mifareReadableSectors", mifareReadableSectors);
            o.put("mifareDumpComplete", mifareDumpComplete);
            o.put("ultralightType", str(ultralightType));
            o.put("ultralightPagesHex", str(ultralightPagesHex));
            o.put("ntagVersionHex", str(ntagVersionHex));
            o.put("type2PagesHex", str(type2PagesHex));
            o.put("type2CcHex", str(type2CcHex));
            o.put("originalitySignatureHex", str(originalitySignatureHex));
            o.put("nfcCounterHex", str(nfcCounterHex));
            o.put("isoDepMaxTransceive", isoDepMaxTransceive);
            o.put("mode", str(mode));
            o.put("registeredAt", registeredAt);
            o.put("updatedAt", updatedAt);

            JSONArray texts = new JSONArray();
            for (String t : ndefTexts) texts.put(t);
            o.put("ndefTexts", texts);

            JSONArray keys = new JSONArray();
            for (String k : mifareSectorKeys) keys.put(k);
            o.put("mifareSectorKeys", keys);

            JSONArray a = new JSONArray();
            for (String aid : aids) a.put(aid);
            o.put("aids", a);

            JSONArray r = new JSONArray();
            for (ApduRule rule : rules) r.put(rule.toJson());
            o.put("rules", r);

            JSONArray n = new JSONArray();
            for (String note : notes) n.put(note);
            o.put("notes", n);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static CardProfile fromJson(String json) {
        try {
            JSONObject o = new JSONObject(json);
            CardProfile p = new CardProfile();
            p.id = o.optString("id", SINGLE_CARD_ID);
            p.cardLabel = o.optString("cardLabel");
            p.studentId = o.optString("studentId");
            p.uidHex = o.optString("uidHex");
            p.techList = o.optString("techList");
            p.atqaHex = o.optString("atqaHex");
            p.sakHex = o.optString("sakHex");
            p.atsHex = o.optString("atsHex");
            p.historicalBytesHex = o.optString("historicalBytesHex");
            p.appDataHex = o.optString("appDataHex");
            p.protocolInfoHex = o.optString("protocolInfoHex");
            p.manufacturerHex = o.optString("manufacturerHex");
            p.systemCodeHex = o.optString("systemCodeHex");
            p.dsfIdHex = o.optString("dsfIdHex");
            p.ndefHex = o.optString("ndefHex");
            p.ndefFileHex = o.optString("ndefFileHex");
            p.ccHex = o.optString("ccHex");
            p.ndefType = o.optString("ndefType");
            p.ndefMaxSize = o.optInt("ndefMaxSize");
            p.ndefPayloadLength = o.optInt("ndefPayloadLength");
            p.ndefWritable = o.optBoolean("ndefWritable");
            p.aakashId = o.optString("aakashId");
            p.psidGuess = o.optString("psidGuess");
            p.expectedBranchPrefix = o.optString("expectedBranchPrefix", BranchInfo.EXPECTED_NDEF_PREFIX);
            p.branchCity = o.optString("branchCity", BranchInfo.CITY);
            p.centreCode = o.optString("centreCode", BranchInfo.AIATS_CENTRE_CODE);
            p.aakashNdefDetected = o.optBoolean("aakashNdefDetected");
            p.type2Detected = o.optBoolean("type2Detected");
            p.mifareType = o.optString("mifareType");
            p.mifareSectorCount = o.optInt("mifareSectorCount");
            p.mifareBlockCount = o.optInt("mifareBlockCount");
            p.mifareSize = o.optInt("mifareSize");
            p.mifareBlocksHex = o.optString("mifareBlocksHex");
            p.mifareBlock0Hex = o.optString("mifareBlock0Hex");
            p.mifareReadableSectors = o.optInt("mifareReadableSectors");
            p.mifareDumpComplete = o.optBoolean("mifareDumpComplete");
            p.ultralightType = o.optString("ultralightType");
            p.ultralightPagesHex = o.optString("ultralightPagesHex");
            p.ntagVersionHex = o.optString("ntagVersionHex");
            p.type2PagesHex = o.optString("type2PagesHex");
            p.type2CcHex = o.optString("type2CcHex");
            p.originalitySignatureHex = o.optString("originalitySignatureHex");
            p.nfcCounterHex = o.optString("nfcCounterHex");
            p.isoDepMaxTransceive = o.optInt("isoDepMaxTransceive");
            p.mode = o.optString("mode", MODE_UNSUPPORTED);
            p.registeredAt = o.optLong("registeredAt", System.currentTimeMillis());
            p.updatedAt = o.optLong("updatedAt", p.registeredAt);

            JSONArray texts = o.optJSONArray("ndefTexts");
            if (texts != null) {
                for (int i = 0; i < texts.length(); i++) p.ndefTexts.add(texts.optString(i));
            }
            JSONArray keys = o.optJSONArray("mifareSectorKeys");
            if (keys != null) {
                for (int i = 0; i < keys.length(); i++) p.mifareSectorKeys.add(keys.optString(i));
            }
            JSONArray a = o.optJSONArray("aids");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) p.addAid(a.optString(i));
            }
            JSONArray r = o.optJSONArray("rules");
            if (r != null) {
                for (int i = 0; i < r.length(); i++) {
                    ApduRule rule = ApduRule.fromJson(r.optJSONObject(i));
                    if (rule != null) p.rules.add(rule);
                }
            }
            JSONArray n = o.optJSONArray("notes");
            if (n != null) {
                for (int i = 0; i < n.length(); i++) p.addNote(n.optString(i));
            }
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }

    public static CardProfile empty() {
        return new CardProfile();
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
