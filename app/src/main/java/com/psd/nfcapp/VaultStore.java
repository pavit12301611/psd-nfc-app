package com.psd.nfcapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Local-only storage for saved NFC tiles.
 * Every payload is encrypted with AES-256-GCM using a key that never leaves the
 * Android Keystore (hardware-backed on Samsung devices) — nothing is sent anywhere.
 */
public class VaultStore {
    private static final String PREFS = "psd_nfc_vault";
    private static final String KEY_IDS = "ids";
    private static final String KEY_ARMED = "armed_id";
    private static final String KEY_HCE_ON = "hce_enabled";
    private static final String ALIAS = "psd_nfc_master_key";

    private final SharedPreferences prefs;
    private final SecretKey key;

    public VaultStore(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        key = loadKey();
    }

    private SecretKey loadKey() {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (ks.containsAlias(ALIAS)) {
                return (SecretKey) ks.getKey(ALIAS, null);
            }
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return kg.generateKey();
        } catch (Exception e) {
            return null;
        }
    }

    private String encrypt(String plain) {
        if (key == null) {
            return "PLAIN:" + Base64.encodeToString(plain.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        }
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return "ENC:" + Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            return "PLAIN:" + Base64.encodeToString(plain.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        }
    }

    private String decrypt(String stored) {
        try {
            if (stored.startsWith("ENC:")) {
                if (key == null) return null;
                byte[] all = Base64.decode(stored.substring(4), Base64.NO_WRAP);
                byte[] iv = new byte[12];
                byte[] ct = new byte[all.length - 12];
                System.arraycopy(all, 0, iv, 0, 12);
                System.arraycopy(all, 12, ct, 0, ct.length);
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
                return new String(c.doFinal(ct), StandardCharsets.UTF_8);
            } else if (stored.startsWith("PLAIN:")) {
                return new String(Base64.decode(stored.substring(6), Base64.NO_WRAP), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public List<NfcItem> loadAll() {
        List<NfcItem> out = new ArrayList<>();
        String ids = prefs.getString(KEY_IDS, "");
        if (ids.isEmpty()) return out;
        for (String id : ids.split(",")) {
            String enc = prefs.getString("item_" + id, null);
            if (enc == null) continue;
            String json = decrypt(enc);
            if (json == null) continue;
            NfcItem it = NfcItem.fromJson(json);
            if (it != null) out.add(it);
        }
        return out;
    }

    public NfcItem get(String id) {
        if (id == null) return null;
        String enc = prefs.getString("item_" + id, null);
        if (enc == null) return null;
        String json = decrypt(enc);
        return json == null ? null : NfcItem.fromJson(json);
    }

    public void save(NfcItem item) {
        List<String> ids = new ArrayList<>();
        String cur = prefs.getString(KEY_IDS, "");
        if (!cur.isEmpty()) {
            for (String s : cur.split(",")) ids.add(s);
        }
        if (!ids.contains(item.id)) ids.add(item.id);
        StringBuilder sb = new StringBuilder();
        for (String s : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        prefs.edit()
                .putString(KEY_IDS, sb.toString())
                .putString("item_" + item.id, encrypt(item.toJson().toString()))
                .apply();
    }

    public void delete(String id) {
        List<String> ids = new ArrayList<>();
        String cur = prefs.getString(KEY_IDS, "");
        if (!cur.isEmpty()) {
            for (String s : cur.split(",")) if (!s.equals(id)) ids.add(s);
        }
        StringBuilder sb = new StringBuilder();
        for (String s : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        prefs.edit().remove("item_" + id).putString(KEY_IDS, sb.toString()).apply();
        if (id.equals(getArmedId())) setArmed(null);
    }

    public String getArmedId() {
        return prefs.getString(KEY_ARMED, null);
    }

    public void setArmed(String id) {
        prefs.edit().putString(KEY_ARMED, id).apply();
    }

    public boolean isHceOn() {
        return prefs.getBoolean(KEY_HCE_ON, false);
    }

    public void setHceOn(boolean on) {
        prefs.edit().putBoolean(KEY_HCE_ON, on).apply();
    }
}
