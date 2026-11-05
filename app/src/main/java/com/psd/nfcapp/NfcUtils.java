package com.psd.nfcapp;

import java.util.Locale;

/** Small, dependency-free helpers for hex, AIDs and APDU rule matching. */
public final class NfcUtils {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

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
