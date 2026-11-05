package com.psd.nfcapp;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

/**
 * The only NFC output of this app: an ISO-DEP card for the attendance reader.
 * It never pushes URLs, never writes physical tags, and never claims a punch succeeded.
 * Every reader APDU and our response is logged so failures are visible.
 */
public class HceService extends HostApduService {
    private CardStore store;
    private final ApduRouter.Session session = new ApduRouter.Session();
    private long cachedVersion = -1L;
    private CardProfile cachedProfile;

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
