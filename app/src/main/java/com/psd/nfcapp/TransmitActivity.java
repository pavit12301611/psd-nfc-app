package com.psd.nfcapp;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Transmit screen: the armed tile is emitted over NFC via HCE (readable by ANY phone,
 * no app needed on the receiving side). If a physical blank tag is tapped while this
 * screen is open, the payload is also written onto that tag.
 */
public class TransmitActivity extends Activity {
    private NfcAdapter nfc;
    private NfcItem armed;
    private Switch writeTag;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transmit);
        nfc = NfcAdapter.getDefaultAdapter(this);
        HceService.refresh(this);
        armed = new VaultStore(this).get(HceService.getArmedId());
        if (armed == null) {
            Toast.makeText(this, "Nothing armed — pick a tile first", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        TextView payload = findViewById(R.id.txPayload);
        TextView tags = findViewById(R.id.txTags);
        status = findViewById(R.id.txStatus);
        writeTag = findViewById(R.id.switchWriteTag);
        Button stop = findViewById(R.id.btnStop);

        payload.setText(armed.payload);
        StringBuilder tb = new StringBuilder();
        for (String t : armed.tags) {
            if (tb.length() > 0) tb.append("  ");
            tb.append("#").append(t);
        }
        tags.setText(tb.toString());
        tags.setVisibility(tb.length() == 0 ? View.GONE : View.VISIBLE);

        status.setText("Transmitting via NFC — bring another phone back-to-back (keep screens on).");
        stop.setOnClickListener(v -> {
            HceService.setArmed(this, null);
            Toast.makeText(this, "Transmission stopped", Toast.LENGTH_SHORT).show();
            finish();
        });

        if (nfc == null) {
            Toast.makeText(this, "NFC not available on this device", Toast.LENGTH_LONG).show();
        }
        handleIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfc != null) {
            Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            nfc.enableForegroundDispatch(this, pi,
                    new IntentFilter[]{
                            new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
                            new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                            new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
                    },
                    NfcTech.techLists());
            // Android Beam (foreground NDEF push) — removed from API 34 SDK, so call via
            // reflection; on Samsung One UI it still works as a second transmit channel.
            try {
                NdefMessage push = NfcUtils.buildMessage(armed);
                if (push != null) {
                    java.lang.reflect.Method m = NfcAdapter.class.getMethod(
                            "enableForegroundNdefPush", Activity.class, NdefMessage.class);
                    m.invoke(nfc, this, push);
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfc != null) {
            nfc.disableForegroundDispatch(this);
            try {
                java.lang.reflect.Method m = NfcAdapter.class.getMethod(
                        "disableForegroundNdefPush", Activity.class);
                m.invoke(nfc, this);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                && !NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                && !NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) return;
        if (writeTag == null || !writeTag.isChecked()) return;
        Tag tag;
        if (Build.VERSION.SDK_INT >= 33) {
            tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag.class);
        } else {
            tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        }
        if (tag != null) writeToTag(tag);
    }

    private void writeToTag(Tag tag) {
        try {
            NdefMessage msg = NfcUtils.buildMessage(armed);
            if (msg == null) return;
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                ndef.connect();
                if (ndef.isWritable()) {
                    if (ndef.getMaxSize() >= msg.toByteArray().length) {
                        ndef.writeNdefMessage(msg);
                        status.setText("✔ Written to the NFC tag!");
                        Toast.makeText(this, "Written to NFC tag ✓", Toast.LENGTH_SHORT).show();
                    } else {
                        status.setText("Tag memory too small for this payload");
                    }
                } else {
                    status.setText("This tag is read-only");
                }
                ndef.close();
                return;
            }
            NdefFormatable fmt = NdefFormatable.get(tag);
            if (fmt != null) {
                fmt.connect();
                fmt.format(msg);
                fmt.close();
                status.setText("✔ Formatted & written to the NFC tag!");
                Toast.makeText(this, "Written to NFC tag ✓", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            status.setText("Write failed: " + e.getMessage());
        }
    }
}
