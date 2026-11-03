package com.psd.nfcapp;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import java.util.List;

/**
 * Quick-settings tile ("NFC Transmit") — one tap arms the last used NFC tile so
 * transmission stays active (visible even on the lock screen), tap again to stop.
 */
public class TransmitTileService extends TileService {

    @Override
    public void onStartListening() {
        refreshTile();
    }

    @Override
    public void onClick() {
        HceService.refresh(getApplicationContext());
        if (HceService.isActive()) {
            HceService.setArmed(getApplicationContext(), null);
        } else {
            List<NfcItem> all = new VaultStore(this).loadAll();
            if (all.isEmpty()) {
                Intent i = new Intent(this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityAndCollapse(i);
            } else {
                HceService.setArmed(getApplicationContext(), all.get(all.size() - 1).id);
            }
        }
        refreshTile();
    }

    private void refreshTile() {
        HceService.refresh(getApplicationContext());
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = HceService.isActive();
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel(on ? "NFC ON" : "NFC Transmit");
        t.updateTile();
    }
}
