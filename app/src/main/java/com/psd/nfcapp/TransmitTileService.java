package com.psd.nfcapp;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings toggle for punch mode. It never transmits links or NDEF pushes. */
public class TransmitTileService extends TileService {
    @Override
    public void onStartListening() {
        refreshTile();
    }

    @Override
    public void onClick() {
        CardStore store = new CardStore(getApplicationContext());
        CardProfile p = store.loadProfile();
        if (p == null) {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapse(i);
            refreshTile();
            return;
        }
        boolean next = !store.isArmed();
        store.setArmed(next);
        HceRegistrar.sync(getApplicationContext(), p);
        refreshTile();
    }

    private void refreshTile() {
        CardStore store = new CardStore(getApplicationContext());
        CardProfile p = store.loadProfile();
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = p != null && store.isArmed();
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel(on ? "Aakash Punch ON" : "Aakash Punch");
        t.updateTile();
    }
}
