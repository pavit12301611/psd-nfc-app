package com.psdnfcapp;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public final class PrefHelper {
    private static final String PREFS = "psdnfc_prefs";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_SECRET = "secret_key";
    private static final String KEY_WHITELIST = "whitelist";
    private static final String KEY_NFC_ALWAYS = "nfc_always_on";

    private PrefHelper() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getPinHash(Context c) {
        return prefs(c).getString(KEY_PIN_HASH, null);
    }

    public static void setPinHash(Context c, String hash) {
        prefs(c).edit().putString(KEY_PIN_HASH, hash).apply();
    }

    public static String getSecret(Context c) {
        return prefs(c).getString(KEY_SECRET, null);
    }

    public static void setSecret(Context c, String s) {
        prefs(c).edit().putString(KEY_SECRET, s).apply();
    }

    public static Set<String> getWhitelist(Context c) {
        return new HashSet<>(prefs(c).getStringSet(KEY_WHITELIST, new HashSet<String>()));
    }

    public static void setWhitelist(Context c, Set<String> s) {
        prefs(c).edit().putStringSet(KEY_WHITELIST, s).apply();
    }

    public static boolean getNfcAlways(Context c) {
        return prefs(c).getBoolean(KEY_NFC_ALWAYS, false);
    }

    public static void setNfcAlways(Context c, boolean b) {
        prefs(c).edit().putBoolean(KEY_NFC_ALWAYS, b).apply();
    }

    public static void clear(Context c) {
        prefs(c).edit().clear().apply();
    }
}
