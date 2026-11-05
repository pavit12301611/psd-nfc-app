package com.psd.nfcapp;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

/**
 * The HCE output of this app: a narrow ISO-DEP / Type 4 card for the attendance reader.
 * For a Type 2 / NTAG card this is only a fallback for readers that also speak Type 4;
 * the real Type 2 credential is presented with a physical NTAG sticker or the original card.
 * It never pushes URLs, never claims a punch succeeded, and logs every APDU.
 */
public class HceService extends HostApduService {
    private CardStore store;
    private final ApduRouter.Session session = new ApduRouter.Session();
    private long cachedVersion = -1L;
    private CardProfile cachedProfile;
    private boolean fallbackLogged;

    @Override
    public void onCreate() {
        super.onCreate();
        store = new CardStore(getApplicationContext());
    }

    @Override
    public synchronized byte[] processCommandApdu(byte[] apdu, Bundle extras) {
        if (store == null) store = new CardStore(getApplicationContext());
        long version = store.getVersion();
        if (cachedProfile == null || cachedVersion != version) {
            cachedProfile = store.loadProfile();
            cachedVersion = version;
            session.reset();
            fallbackLogged = false;
        }
        if (!fallbackLogged && cachedProfile != null
                && CardProfile.MODE_TYPE2_NDEF.equals(cachedProfile.mode)) {
            store.appendLog("TYPE2_FALLBACK | registered card is NFC Forum Type 2 / NTAG; "
                    + "this service only serves Type 4/ISO-DEP. A Type-2-only reader will "
                    + "never reach this service.");
            fallbackLogged = true;
        }

        String cmd = NfcUtils.bytesToHex(apdu);
        if (!store.isArmed()) {
            byte[] response = ApduRouter.SW_CONDITIONS_NOT_SATISFIED;
            store.appendLog("C-APDU " + cmd + " -> " + NfcUtils.bytesToHex(response)
                    + " | punch mode is OFF");
            return response;
        }
        if (cachedProfile == null) {
            byte[] response = ApduRouter.SW_FILE_NOT_FOUND;
            store.appendLog("C-APDU " + cmd + " -> " + NfcUtils.bytesToHex(response)
                    + " | no card registered");
            return response;
        }

        ApduRouter.Result result = ApduRouter.route(cachedProfile, apdu, session);
        String sw = NfcUtils.bytesToHex(new byte[]{
                result.response[result.response.length - 2],
                result.response[result.response.length - 1]});
        store.appendLog("C-APDU " + cmd + " -> " + NfcUtils.bytesToHex(result.response)
                + " | " + result.note + " | SW " + NfcUtils.statusWordName(result.response)
                + " (" + sw + ")");
        return result.response;
    }

    @Override
    public void onDeactivated(int reason) {
        session.reset();
        if (store != null) store.appendLog("DEACTIVATED reason=" + reason
                + " | reader left the field");
    }
}
