package com.psd.nfcapp;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class NfcUtils {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    // Standard NDEF URI prefix codes
    private static final String[] URI_PREFIXES = {
            "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
            "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://",
            "nfs://", "ftp://", "dav://", "news:", "telnet://", "imap:", "rtsp://",
            "urn:", "pop:", "sip:", "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://",
            "tcpobex://", "irdaobex://", "file://", "urn:epc:id:", "urn:epc:tag:",
            "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:"
    };

    public static String bytesToHex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(HEX[(x >> 4) & 0xF]).append(HEX[x & 0xF]);
        }
        return sb.toString();
    }

    public static byte[] hexToBytes(String s) {
        if (s == null) return new byte[0];
        String clean = s.replaceAll("[^0-9A-Fa-f]", "");
        if (clean.length() % 2 == 1) clean = "0" + clean;
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    /** Force links to https:// when the user typed a bare domain. */
    public static String normalizeUrl(String in) {
        String s = in == null ? "" : in.trim();
        if (s.isEmpty()) return s;
        String low = s.toLowerCase(Locale.US);
        if (low.startsWith("http://") || low.startsWith("https://")) return s;
        if (!s.contains(" ") && s.contains(".")) return "https://" + s;
        return s;
    }

    public static boolean looksLikeLink(String s) {
        return normalizeUrl(s).toLowerCase(Locale.US).startsWith("http");
    }

    /**
     * Build the exact NDEF message that gets transmitted for a tile.
     *
     * WORKAROUND for "No supported application to open the NFC signal":
     * the FIRST record is ALWAYS a URI record, so any phone (no app needed,
     * just a browser — every phone has one) opens it instead of showing that
     * error. Scanned raw records are appended after it, and an Android App
     * Record (AAR) is added last so phones with this app installed open the
     * app directly.
     */
    public static NdefMessage buildMessage(NfcItem item) {
        try {
            java.util.List<NdefRecord> recs = new java.util.ArrayList<>();

            // 1) URI record first
            String link = item.openLink == null ? "" : item.openLink.trim();
            if (link.isEmpty()) {
                if (NfcItem.KIND_LINK.equals(item.kind) || looksLikeLink(item.payload)) {
                    link = normalizeUrl(item.payload);
                } else if (NfcItem.KIND_SCAN.equals(item.kind)) {
                    // default fallback for scanned cards: search page showing the tag ID
                    link = "https://www.google.com/search?q=NFC-TAG-"
                            + (item.tagIdHex == null || item.tagIdHex.isEmpty() ? "unknown" : item.tagIdHex);
                } else if (NfcItem.KIND_PUNCH.equals(item.kind)) {
                    // Aakash branch punch: create a specific link for attendance verification
                    link = "https://aakash-branch.psd-nfc-app/punch?id="
                            + (item.tagIdHex == null ? "unknown" : item.tagIdHex)
                            + "&time=" + item.createdAt;
                }
            }
            if (!link.isEmpty()) {
                try {
                    recs.add(NdefRecord.createUri(normalizeUrl(link)));
                } catch (Exception e) {
                    try {
                        recs.add(NdefRecord.createUri("https://www.google.com/search?q="
                                + java.net.URLEncoder.encode(link, "UTF-8")));
                    } catch (Exception ignored) {
                    }
                }
            }

            // 2) replay the captured raw NDEF records of a scanned tag (after the URI)
            if (item.rawNdefHex != null && item.rawNdefHex.length() > 0) {
                try {
                    for (NdefRecord r : new NdefMessage(hexToBytes(item.rawNdefHex)).getRecords()) {
                        recs.add(r);
                    }
                } catch (Exception ignored) {
                }
            }

            // 3) plain payload if nothing else was added
            if (recs.isEmpty()) {
                if (NfcItem.KIND_SCAN.equals(item.kind)) {
                    String data = (item.tagIdHex == null ? "" : item.tagIdHex)
                            + "|" + (item.tech == null ? "" : item.tech);
                    recs.add(NdefRecord.createMime("application/x-psd-tagid",
                            data.getBytes(StandardCharsets.UTF_8)));
                } else if (NfcItem.KIND_PUNCH.equals(item.kind)) {
                    // Aakash branch punch payload
                    String data = "AAKASH_BRANCH_PUNCH|" + item.payload + "|" + item.tagIdHex;
                    recs.add(NdefRecord.createMime("application/x-psd-aakash-punch",
                            data.getBytes(StandardCharsets.UTF_8)));
                } else {
                    recs.add(NdefRecord.createTextRecord("en", item.payload == null ? "" : item.payload));
                }
            }

            // 4) Android App Record (must be last) — opens this app on phones that have it
            recs.add(NdefRecord.createApplicationRecord("com.psd.nfcapp"));

            return new NdefMessage(recs.toArray(new NdefRecord[0]));
        } catch (Exception e) {
            try {
                return new NdefMessage(new NdefRecord[]{
                        NdefRecord.createUri("https://www.google.com/"),
                        NdefRecord.createApplicationRecord("com.psd.nfcapp")});
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    /** First URI record of a received NDEF message, or null. */
    public static String firstUri(NdefMessage msg) {
        if (msg == null) return null;
        for (NdefRecord r : msg.getRecords()) {
            if (r.getTnf() == NdefRecord.TNF_WELL_KNOWN
                    && "U".equals(new String(r.getType(), StandardCharsets.UTF_8))) {
                byte[] p = r.getPayload();
                if (p.length > 0) {
                    int prefix = p[0] & 0xFF;
                    String pre = prefix < URI_PREFIXES.length ? URI_PREFIXES[prefix] : "";
                    return pre + new String(p, 1, p.length - 1, StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    /** Human-readable description of a received NDEF message. */
    public static String describe(NdefMessage msg) {
        if (msg == null) return "";
        StringBuilder sb = new StringBuilder();
        for (NdefRecord r : msg.getRecords()) {
            if (sb.length() > 0) sb.append(" • ");
            short tnf = r.getTnf();
            String type = new String(r.getType(), StandardCharsets.UTF_8);
            byte[] p = r.getPayload();
            if (tnf == NdefRecord.TNF_WELL_KNOWN && "U".equals(type) && p.length > 0) {
                int prefix = p[0] & 0xFF;
                String pre = prefix < URI_PREFIXES.length ? URI_PREFIXES[prefix] : "";
                sb.append(pre).append(new String(p, 1, p.length - 1, StandardCharsets.UTF_8));
            } else if (tnf == NdefRecord.TNF_WELL_KNOWN && "T".equals(type) && p.length > 1) {
                int langLen = p[0] & 0x3F;
                sb.append(new String(p, 1 + langLen, p.length - 1 - langLen, StandardCharsets.UTF_8));
            } else if (tnf == NdefRecord.TNF_MIME_MEDIA) {
                sb.append(type).append(": ").append(new String(p, StandardCharsets.UTF_8));
            } else {
                sb.append(type.isEmpty() ? "record" : type);
            }
        }
        return sb.toString();
    }

    private static final int[] TAG_COLORS = {
            0xFF6EE7FF, 0xFFA5F3A5, 0xFFFFD166, 0xFFFF8FAB,
            0xFFC4A7FF, 0xFF8AD8FF, 0xFFF6A6A6, 0xFFA6F4C5
    };

    public static int colorForTag(String tag) {
        int h = tag == null ? 0 : tag.hashCode();
        return TAG_COLORS[Math.abs(h) % TAG_COLORS.length];
    }
}