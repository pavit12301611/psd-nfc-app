package com.psd.nfcapp;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NfcUtilsTest {

    @Test
    public void hexRoundTripAndStrictParsing() {
        byte[] bytes = NfcUtils.hexToBytesStrict("00 A4-04:00 07 D2 76 00 00 85 01 01 00");
        assertEquals(13, bytes.length);
        assertEquals("00A4040007D276000085010100", NfcUtils.bytesToHex(bytes));
        assertNull(NfcUtils.hexToBytesStrict("ABC"));
        assertEquals(0, NfcUtils.hexToBytes("not hex").length);
    }

    @Test
    public void parsesShortTextRecord() {
        byte[] ndef = textRecord("AAKASH-SAHARANPUR-001");
        List<String> texts = NfcUtils.ndefTexts(ndef);
        assertEquals(1, texts.size());
        assertEquals("AAKASH-SAHARANPUR-001", texts.get(0));
        assertEquals("AAKASH-SAHARANPUR-001", NfcUtils.findAakashIdentifier(ndef));
    }

    @Test
    public void parsesNonShortTextRecord() {
        byte[] ndef = nonShortTextRecord("AAKASH");
        List<String> texts = NfcUtils.ndefTexts(ndef);
        assertEquals(1, texts.size());
        assertEquals("AAKASH", texts.get(0));
    }

    @Test
    public void parsesUtf16TextRecord() {
        byte[] ndef = utf16TextRecord("AAKASH-UTF16");
        assertEquals("AAKASH-UTF16", NfcUtils.ndefTexts(ndef).get(0));
    }

    @Test
    public void findsAakashTokenAndElevenDigitPsid() {
        byte[] ndef = textRecord("Student PSID 12345678901 branch AAKASH-DELHI-001");
        assertEquals("AAKASH-DELHI-001", NfcUtils.findAakashIdentifier(ndef));
        assertEquals("12345678901", NfcUtils.findPsid(ndef));
        // A 12-digit run must not be sliced into an 11-digit match.
        assertEquals("", NfcUtils.findPsid(textRecord("123456789012")));
    }

    @Test
    public void wildcardAndPrefixRules() {
        byte[] cmd = NfcUtils.hexToBytes("00A4040007D276000085010100");
        assertTrue(NfcUtils.ruleMatches("00A4??00*", cmd));
        assertTrue(NfcUtils.ruleMatches("00A4040007D276000085010100", cmd));
        assertFalse(NfcUtils.ruleMatches("00A4??00", cmd)); // exact length, no prefix
        assertFalse(NfcUtils.ruleMatches("00A4?100*", cmd)); // half-byte wildcard invalid
        assertFalse(NfcUtils.ruleMatches("00A4ZZ00*", cmd));
    }

    @Test
    public void statusWords() {
        assertEquals("OK", NfcUtils.statusWordName(NfcUtils.hexToBytes("9000")));
        assertEquals("file/app not found", NfcUtils.statusWordName(NfcUtils.hexToBytes("6A82")));
        assertEquals("no SW", NfcUtils.statusWordName(null));
    }

    private static byte[] textRecord(String text) {
        byte[] lang = "en".getBytes(StandardCharsets.US_ASCII);
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[1 + lang.length + body.length];
        payload[0] = (byte) lang.length;
        System.arraycopy(lang, 0, payload, 1, lang.length);
        System.arraycopy(body, 0, payload, 1 + lang.length, body.length);
        return NfcUtils.concat(
                new byte[]{(byte) 0xD1, 0x01, (byte) payload.length},
                new byte[]{'T'},
                payload);
    }

    private static byte[] nonShortTextRecord(String text) {
        byte[] lang = "en".getBytes(StandardCharsets.US_ASCII);
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[1 + lang.length + body.length];
        payload[0] = (byte) lang.length;
        System.arraycopy(lang, 0, payload, 1, lang.length);
        System.arraycopy(body, 0, payload, 1 + lang.length, body.length);
        byte[] header = new byte[]{
                (byte) 0xC1, 0x01,
                (byte) ((payload.length >> 24) & 0xFF),
                (byte) ((payload.length >> 16) & 0xFF),
                (byte) ((payload.length >> 8) & 0xFF),
                (byte) (payload.length & 0xFF)
        };
        return NfcUtils.concat(header, new byte[]{'T'}, payload);
    }

    private static byte[] utf16TextRecord(String text) {
        byte[] lang = "en".getBytes(StandardCharsets.US_ASCII);
        byte[] body = text.getBytes(StandardCharsets.UTF_16BE);
        byte[] payload = new byte[1 + lang.length + body.length];
        payload[0] = (byte) (0x80 | lang.length);
        System.arraycopy(lang, 0, payload, 1, lang.length);
        System.arraycopy(body, 0, payload, 1 + lang.length, body.length);
        return NfcUtils.concat(
                new byte[]{(byte) 0xD1, 0x01, (byte) payload.length},
                new byte[]{'T'},
                payload);
    }
}
