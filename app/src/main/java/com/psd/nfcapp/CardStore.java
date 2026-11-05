package com.psd.nfcapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Local-only encrypted storage for exactly one card profile, plus a small APDU diagnostic log. */
public class CardStore {
    private static final String PREFS = "aakash_punch_v2";
    private static final String KEY_PROFILE = "profile";
    private static final String KEY_ARMED = "armed";
    private static final String KEY_VERSION = "version";
    private static final String KEY_LOG = "apdu_log";
    private static final String KEY_LAST_CONTACT = "last_reader_contact";
    private static final String KEY_AID_OK = "aid_registration_ok";
    private static final String KEY_AID_NOTE = "aid_registration_note";
    private static final String KEY_ALIAS = "aakash_punch_key_v2";
    private static final int MAX_LOG = 40;

    private final SharedPreferences prefs;
    private final SecretKey key;

    public CardStore(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        key = loadKey();
    }

    private SecretKey loadKey() {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (ks.containsAlias(KEY_ALIAS)) {
                return (SecretKey) ks.getKey(KEY_ALIAS, null);
            }
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
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
            return "ENC:" + Base64.encodeToString(NfcUtils.concat(iv, ct), Base64.NO_WRAP);
        } catch (Exception e) {
            return "PLAIN:" + Base64.encodeToString(plain.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        }
    }

    private String decrypt(String stored) {
        try {
            if (stored == null) return null;
            if (stored.startsWith("ENC:")) {
                if (key == null) return null;
                byte[] all = Base64.decode(stored.substring(4), Base64.NO_WRAP);
                if (all.length < 13) return null;
                byte[] iv = new byte[12];
                byte[] ct = new byte[all.length - 12];
                System.arraycopy(all, 0, iv, 0, 12);
                System.arraycopy(all, 12, ct, 0, ct.length);
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
                return new String(c.doFinal(ct), StandardCharsets.UTF_8);
            }
            if (stored.startsWith("PLAIN:")) {
                return new String(Base64.decode(stored.substring(6), Base64.NO_WRAP), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public CardProfile loadProfile() {
        String json = decrypt(prefs.getString(KEY_PROFILE, null));
        if (json == null) return null;
        return CardProfile.fromJson(json);
    }

    public boolean hasProfile() {
        return loadProfile() != null;
    }

    public void saveProfile(CardProfile p) {
        if (p == null) {
            clearProfile();
            return;
        }
        p.id = CardProfile.SINGLE_CARD_ID;
        p.updatedAt = System.currentTimeMillis();
        prefs.edit().putString(KEY_PROFILE, encrypt(p.toJson().toString())).apply();
        bumpVersion();
    }

    public void clearProfile() {
        prefs.edit()
                .remove(KEY_PROFILE)
                .putBoolean(KEY_ARMED, false)
                .apply();
        bumpVersion();
    }

    public boolean isArmed() {
        return prefs.getBoolean(KEY_ARMED, false);
    }

    public void setArmed(boolean on) {
        prefs.edit().putBoolean(KEY_ARMED, on).apply();
        bumpVersion();
    }

    public long getVersion() {
        return prefs.getLong(KEY_VERSION, 0L);
    }

    private void bumpVersion() {
        long next = Math.max(System.currentTimeMillis(), getVersion() + 1L);
        prefs.edit().putLong(KEY_VERSION, next).apply();
    }

    public void setAidRegistrationOk(boolean ok, String note) {
        prefs.edit()
                .putBoolean(KEY_AID_OK, ok)
                .putString(KEY_AID_NOTE, note == null ? "" : note)
                .apply();
    }

    public boolean isAidRegistrationOk() {
        return prefs.getBoolean(KEY_AID_OK, false);
    }

    public String getAidRegistrationNote() {
        return prefs.getString(KEY_AID_NOTE, "not checked yet");
    }

    public void appendLog(String line) {
        String stamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        String entry = stamp + "  " + (line == null ? "" : line.replace('\n', ' ').replace('\r', ' '));
        String old = prefs.getString(KEY_LOG, "");
        StringBuilder sb = new StringBuilder(entry);
        if (!old.isEmpty()) {
            String[] lines = old.split("\n");
            for (int i = 0; i < lines.length && i < MAX_LOG - 1; i++) {
                if (lines[i].isEmpty()) continue;
                sb.append('\n').append(lines[i]);
            }
        }
        prefs.edit()
                .putString(KEY_LOG, sb.toString())
                .putLong(KEY_LAST_CONTACT, System.currentTimeMillis())
                .apply();
    }

    public List<String> getLog() {
        List<String> out = new ArrayList<>();
        String stored = prefs.getString(KEY_LOG, "");
        if (stored.isEmpty()) return out;
        for (String line : stored.split("\n")) {
            if (!line.isEmpty()) out.add(line);
        }
        return out;
    }

    public void clearLog() {
        prefs.edit().remove(KEY_LOG).remove(KEY_LAST_CONTACT).apply();
    }

    public long getLastReaderContact() {
        return prefs.getLong(KEY_LAST_CONTACT, 0L);
    }
}
