package com.psdnfcapp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the NDEF file that HceLinkService serves over the air.
 * Record: NFC Forum External Type "psdnfc-app:link" whose payload is the
 * Base64(AES-256-GCM encrypted link). A random phone can read the bytes but
 * cannot make sense of them without the shared secret key.
 */
public final class NdefBuilder {
    public static final String EXTERNAL_DOMAIN = "psdnfc-app";
    public static final String EXTERNAL_TYPE = "link";

    private NdefBuilder() {
    }

    /**
     * @param encryptedBase64 Base64(IV || ciphertext) of the link
     * @return NDEF file bytes (Message TLV + terminator TLV)
     */
    public static byte[] buildNdefFile(String encryptedBase64) {
        byte[] type = (EXTERNAL_DOMAIN + ":" + EXTERNAL_TYPE).getBytes(StandardCharsets.UTF_8);
        byte[] payload = encryptedBase64.getBytes(StandardCharsets.UTF_8);

        // NDEF message record: MB=1, ME=1, SR=1, IL=0, TNF=External(4) => 0xC4
        byte[] message = new byte[1 + 1 + 1 + type.length + payload.length];
        int i = 0;
        message[i++] = (byte) 0xC4;
        message[i++] = (byte) type.length;
        message[i++] = (byte) payload.length;
        System.arraycopy(type, 0, message, i, type.length);
        i += type.length;
        System.arraycopy(payload, 0, message, i, payload.length);

        // NDEF file: '03 <len> <message>' TLV followed by FE terminator
        List<Byte> file = new ArrayList<>();
        file.add((byte) 0x03);
        file.add((byte) message.length);
        for (byte b : message) {
            file.add(b);
        }
        file.add((byte) 0xFE);

        byte[] out = new byte[file.size()];
        for (int j = 0; j < out.length; j++) {
            out[j] = file.get(j);
        }
        return out;
    }
}
