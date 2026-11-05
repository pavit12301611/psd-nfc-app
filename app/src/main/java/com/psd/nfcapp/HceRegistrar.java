package com.psd.nfcapp;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.CardEmulation;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Registers the AIDs the attendance reader may SELECT, and prefers this HCE service in foreground. */
public final class HceRegistrar {
    public static final String AID_NDEF = "D2760000850101";
    public static final String AID_T4T = "D2760000850100";

    private HceRegistrar() {
    }

    public static boolean supportsHce(Context ctx) {
        return ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);
    }

    public static List<String> buildAidList(CardProfile profile) {
        List<String> aids = new ArrayList<>(Arrays.asList(AID_NDEF, AID_T4T));
        if (profile != null) {
            for (String aid : profile.aids) {
                String normalized = NfcUtils.normalizeAid(aid);
                if (normalized != null && !aids.contains(normalized)) aids.add(normalized);
            }
        }
        return aids;
    }

    /** Replaces the dynamic CATEGORY_OTHER AID group with the current card's AIDs. */
    public static boolean sync(Context ctx, CardProfile profile) {
        CardStore store = new CardStore(ctx.getApplicationContext());
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(ctx);
        if (adapter == null || !supportsHce(ctx)) {
            store.setAidRegistrationOk(false, "NFC or Host Card Emulation is unavailable on this device");
            return false;
        }
        try {
            CardEmulation ce = CardEmulation.getInstance(adapter);
            if (ce == null) {
                store.setAidRegistrationOk(false, "CardEmulation service unavailable");
                return false;
            }
            ComponentName service = new ComponentName(ctx, HceService.class);
            List<String> aids = buildAidList(profile);
            boolean ok = ce.registerAidsForService(service, CardEmulation.CATEGORY_OTHER, aids);
            store.setAidRegistrationOk(ok, ok
                    ? aids.size() + " AIDs registered for " + service.getShortClassName()
                    : "Android rejected the dynamic AID group");
            return ok;
        } catch (Exception e) {
            store.setAidRegistrationOk(false,
                    e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "registration failed" : e.getMessage()));
            return false;
        }
    }

    public static boolean setPreferred(Activity activity, boolean on) {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(activity);
        if (adapter == null || !supportsHce(activity)) return false;
        try {
            CardEmulation ce = CardEmulation.getInstance(adapter);
            if (ce == null) return false;
            ComponentName service = new ComponentName(activity, HceService.class);
            return on
                    ? ce.setPreferredService(activity, service)
                    : ce.unsetPreferredService(activity);
        } catch (Exception e) {
            return false;
        }
    }

    public static String registeredAidsText(Context ctx) {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(ctx);
        if (adapter != null) {
            try {
                CardEmulation ce = CardEmulation.getInstance(adapter);
                if (ce != null) {
                    List<String> aids = ce.getAidsForService(
                            new ComponentName(ctx, HceService.class), CardEmulation.CATEGORY_OTHER);
                    if (aids != null && !aids.isEmpty()) return TextUtils.join(", ", aids);
                }
            } catch (Exception ignored) {
            }
        }
        CardProfile p = new CardStore(ctx).loadProfile();
        return TextUtils.join(", ", buildAidList(p));
    }

    public static String statusText(Context ctx, CardStore store) {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(ctx);
        boolean nfcOn = adapter != null && adapter.isEnabled();
        boolean hce = supportsHce(ctx);
        String aids = registeredAidsText(ctx);
        boolean aidOk = store.isAidRegistrationOk();
        CardProfile p = store.loadProfile();
        String path;
        if (p != null && CardProfile.MODE_MIFARE_CLASSIC.equals(p.mode)) {
            path = "Diagnostics only (MIFARE Classic / Crypto1 is not HCE-emulatable)";
        } else if (p != null && CardProfile.MODE_TYPE2_NDEF.equals(p.mode)) {
            path = "Type 4 fallback only (registered card is Type 2 / NTAG)";
        } else {
            path = "ISO-DEP / Type 4";
        }
        return "NFC: " + (nfcOn ? "ON" : "OFF")
                + "  |  HCE: " + (hce ? "supported" : "unsupported")
                + "\nHCE path: " + path
                + "\nAIDs: " + aids
                + "\nAID registration: " + (aidOk ? "OK" : "FAILED — " + store.getAidRegistrationNote());
    }
}
