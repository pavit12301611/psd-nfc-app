package com.psd.nfcapp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small, dependency-free helpers for hex, AIDs, APDU rules and NDEF text parsing. */
public final class NfcUtils {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    private static final byte[] RTD_TEXT = new byte[]{'T'};
    private static final Pattern AAKASH_TOKEN =
            Pattern.compile("(?i)AAKASH[-_A-Z0-9]*");
    private static final Pattern PSID_TOKEN =
            Pattern.compile("(?<!\\d)\\d{11}(?!\\d)");

    private NfcUtils() {
    }

    public static String bytesToHex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(HEX[(x >> 4) & 0xF]).append(HEX[x & 0xF]);
        }
        return sb.toString();
    }

    public static String byteToHex(int b) {
        return new String(new char[]{HEX[(b >> 4) & 0xF], HEX[b & 0xF]});
    }

    /** Lenient parser: strips separators; returns empty bytes when the input is invalid. */
    public static byte[] hexToBytes(String s) {
        byte[] out = hexToBytesStrict(s);
        return out == null ? new byte[0] : out;
    }

    /** Strict parser: null when the cleaned string has an odd length or no usable hex. */
    public static byte[] hexToBytesStrict(String s) {
        if (s == null) return null;
        String clean = cleanHex(s);
        if (clean.length() % 2 != 0) return null;
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public static String cleanHex(String s) {
        if (s == null) return "";
        return s.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.US);
    }

    public static boolean isValidHex(String s) {
        return hexToBytesStrict(s) != null;
    }

    /** Returns an uppercase 5..16-byte AID hex string, or null when invalid. */
    public static String normalizeAid(String s) {
        String clean = cleanHex(s);
        if (clean.length() < 10 || clean.length() > 32) return null;
        return clean;
    }

    /**
     * Rules are hex bytes with optional "??" wildcard bytes and an optional trailing "*"
     * for prefix matching. Separators (space, colon, dash, comma, newline) are ignored.
     */
    public static boolean isValidRulePattern(String rule) {
        return parseRule(rule) != null;
    }

    public static boolean ruleMatches(String rule, byte[] command) {
        if (command == null) return false;
        RulePattern rp = parseRule(rule);
        if (rp == null) return false;
        if (rp.prefix) {
            if (command.length < rp.masks.length) return false;
        } else if (command.length != rp.masks.length) {
            return false;
        }
        for (int i = 0; i < rp.masks.length; i++) {
            int expected = rp.values[i];
            boolean wildcard = rp.masks[i];
            if (!wildcard && (command[i] & 0xFF) != expected) return false;
        }
        return true;
    }

    private static RulePattern parseRule(String rule) {
        if (rule == null) return null;
        String r = rule.trim().toUpperCase(Locale.US);
        boolean prefix = false;
        if (r.endsWith("*")) {
            prefix = true;
            r = r.substring(0, r.length() - 1);
        }
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < r.length(); i++) {
            char c = r.charAt(i);
            if (c == '?' || isHexChar(c)) {
                clean.append(c);
            } else if (c == ' ' || c == ':' || c == '-' || c == ',' || c == '_'
                    || c == '\n' || c == '\r' || c == '\t') {
                // separator
            } else {
                return null;
            }
        }
        String p = clean.toString();
        if (p.isEmpty() || p.length() % 2 != 0) return null;
        int count = p.length() / 2;
        int[] values = new int[count];
        boolean[] masks = new boolean[count];
        for (int i = 0; i < count; i++) {
            String pair = p.substring(i * 2, i * 2 + 2);
            if ("??".equals(pair)) {
                masks[i] = true;
                values[i] = 0;
            } else if (pair.charAt(0) == '?' || pair.charAt(1) == '?') {
                return null; // only whole-byte ?? wildcards are supported
            } else {
                masks[i] = false;
                values[i] = Integer.parseInt(pair, 16);
            }
        }
        return new RulePattern(values, masks, prefix);
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
    }

    private static final class RulePattern {
        final int[] values;
        final boolean[] masks;
        final boolean prefix;

        RulePattern(int[] values, boolean[] masks, boolean prefix) {
            this.values = values;
            this.masks = masks;
            this.prefix = prefix;
        }
    }

    public static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) total += p == null ? 0 : p.length;
        byte[] out = new byte[total];
        int off = 0;
        for (byte[] p : parts) {
            if (p == null) continue;
            System.arraycopy(p, 0, out, off, p.length);
            off += p.length;
        }
        return out;
    }

    /**
     * Minimal NDEF record parser that extracts human-readable text from:
     *  - NFC Forum RTD Text ("T") records (UTF-8 or UTF-16BE),
     *  - other records whose payload is printable UTF-8.
     * It deliberately does not depend on Android classes so it is unit-testable.
     */
    public static List<String> ndefTexts(byte[] ndef) {
        List<String> out = new ArrayList<>();
        if (ndef == null || ndef.length < 3) return out;
        int off = 0;
        while (off < ndef.length) {
            if (off + 3 > ndef.length) break;
            int header = ndef[off++] & 0xFF;
            boolean shortRecord = (header & 0x10) != 0;
            boolean idLengthPresent = (header & 0x08) != 0;
            boolean messageEnd = (header & 0x40) != 0;
            int tnf = header & 0x07;
            int typeLength = ndef[off++] & 0xFF;

            long payloadLength;
            if (shortRecord) {
                if (off + 1 > ndef.length) break;
                payloadLength = ndef[off++] & 0xFF;
            } else {
                if (off + 4 > ndef.length) break;
                payloadLength = ((long) (ndef[off] & 0xFF) << 24)
                        | ((long) (ndef[off + 1] & 0xFF) << 16)
                        | ((long) (ndef[off + 2] & 0xFF) << 8)
                        | (long) (ndef[off + 3] & 0xFF);
                off += 4;
            }
            int idLength = 0;
            if (idLengthPresent) {
                if (off + 1 > ndef.length) break;
                idLength = ndef[off++] & 0xFF;
            }
            if (payloadLength < 0 || payloadLength > Integer.MAX_VALUE) break;
            int pLen = (int) payloadLength;
            if (off + typeLength + idLength + pLen > ndef.length) break;

            byte[] type = Arrays.copyOfRange(ndef, off, off + typeLength);
            off += typeLength;
            off += idLength;
            byte[] payload = Arrays.copyOfRange(ndef, off, off + pLen);
            off += pLen;

            String text = null;
            if (tnf == 0x01 && Arrays.equals(type, RTD_TEXT)) {
                text = decodeTextRecord(payload);
            } else {
                String raw = new String(payload, StandardCharsets.UTF_8);
                if (isMostlyPrintable(raw)) text = raw;
            }
            if (text != null && !text.isEmpty()) out.add(text);
            if (messageEnd) break;
        }
        return out;
    }

    private static String decodeTextRecord(byte[] payload) {
        if (payload == null || payload.length < 1) return null;
        int status = payload[0] & 0xFF;
        int langLength = status & 0x3F;
        int start = 1 + langLength;
        if (start > payload.length) return null;
        boolean utf16 = (status & 0x80) != 0;
        return new String(payload, start, payload.length - start,
                utf16 ? StandardCharsets.UTF_16BE : StandardCharsets.UTF_8);
    }

    private static boolean isMostlyPrintable(String s) {
        if (s == null || s.isEmpty()) return false;
        int ok = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || (c >= 0x20 && c != 0x7F)) ok++;
        }
        return ok * 10 >= s.length() * 8 && !s.contains("\uFFFD");
    }

    public static String findAakashIdentifier(byte[] ndef) {
        return findAakashIdentifier(ndefTexts(ndef));
    }

    /**
     * Reports a literal AAKASH token only when it already exists in captured NDEF text.
     * It does NOT invent a branch ID or use "AAKASH" as a credential.
     */
    public static String findAakashIdentifier(List<String> texts) {
        if (texts == null) return "";
        for (String raw : texts) {
            String s = raw == null ? "" : raw.trim();
            if (s.toUpperCase(Locale.US).startsWith("AAKASH")) return s;
        }
        for (String raw : texts) {
            if (raw == null) continue;
            Matcher m = AAKASH_TOKEN.matcher(raw);
            if (m.find()) return m.group();
        }
        return "";
    }

    public static String findPsid(byte[] ndef) {
        return findPsid(ndefTexts(ndef));
    }

    public static String findPsid(List<String> texts) {
        if (texts == null) return "";
        for (String raw : texts) {
            if (raw == null) continue;
            Matcher m = PSID_TOKEN.matcher(raw);
            if (m.find()) return m.group();
        }
        return "";
    }

    public static String ndefTextSummary(byte[] ndef) {
        List<String> texts = ndefTexts(ndef);
        if (texts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String t : texts) {
            if (sb.length() > 0) sb.append(" | ");
            String s = t.length() > 80 ? t.substring(0, 80) + "…" : t;
            sb.append(s.replace('\n', ' ').replace('\r', ' '));
        }
        return sb.toString();
    }

    /**
     * Minimal MIFARE Classic block-0 sanity check copied from the common MCT rules:
     * for 4-byte UIDs the 5th byte must be the XOR/BCC of the UID and UID0 must not
     * be a cascade/reserved marker. This prevents bricking a Gen2/CUID magic card.
     */
    public static boolean isValidMifareBlock0(String block0Hex, int uidLength) {
        byte[] b = hexToBytesStrict(block0Hex);
        if (b == null || b.length != 16) return false;
        if (uidLength == 4) {
            byte bcc = (byte) (b[0] ^ b[1] ^ b[2] ^ b[3]);
            if ((b[4] & 0xFF) != (bcc & 0xFF)) return false;
            int first = b[0] & 0xFF;
            return first != 0x88 && first != 0xF8;
        }
        return uidLength == 7 || uidLength == 10;
    }

    public static String statusWordName(byte[] response) {
        if (response == null || response.length < 2) return "no SW";
        String sw = bytesToHex(new byte[]{response[response.length - 2], response[response.length - 1]});
        if ("9000".equals(sw)) return "OK";
        if ("6A82".equals(sw)) return "file/app not found";
        if ("6B00".equals(sw)) return "wrong offset";
        if ("6700".equals(sw)) return "wrong length";
        if ("6D00".equals(sw)) return "instruction not supported";
        if ("6E00".equals(sw)) return "class not supported";
        if ("6982".equals(sw)) return "security status not satisfied";
        if ("6985".equals(sw)) return "conditions not satisfied";
        if ("6F00".equals(sw)) return "unknown error";
        return sw;
    }
}
