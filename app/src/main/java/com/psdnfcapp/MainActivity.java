package com.psdnfcapp;

import android.content.Intent;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.HashSet;
import java.util.Set;
import javax.crypto.SecretKey;

public class MainActivity extends AppCompatActivity {
    private EditText etLink;
    private EditText etSecret;
    private Button btnPrepare;
    private Button btnPin;
    private Switch swAlways;
    private TextView tvStatus;
    private LinearLayout llWhitelist;
    private boolean sessionUnlocked = false;

    private final ActivityResultLauncher<Intent> pinLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK
                                && result.getData() != null
                                && Boolean.TRUE.equals(result.getData().getBooleanExtra("unlocked", false))) {
                            sessionUnlocked = true;
                            updateUi();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etLink = findViewById(R.id.et_link);
        etSecret = findViewById(R.id.et_secret);
        btnPrepare = findViewById(R.id.btn_prepare);
        btnPin = findViewById(R.id.btn_pin);
        swAlways = findViewById(R.id.sw_always);
        tvStatus = findViewById(R.id.tv_status);
        llWhitelist = findViewById(R.id.ll_whitelist);

        boolean pinSet = PrefHelper.getPinHash(this) != null;
        btnPin.setText(pinSet ? R.string.btn_change_pin : R.string.btn_set_pin);
        swAlways.setChecked(PrefHelper.getNfcAlways(this));

        refreshWhitelistView();
        updateUi();

        btnPin.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, PinActivity.class);
            i.putExtra("mode", pinSet ? "verify" : "set");
            pinLauncher.launch(i);
        });

        btnPrepare.setOnClickListener(v -> onPrepare());

        swAlways.setOnCheckedChangeListener((buttonView, checked) -> {
            PrefHelper.setNfcAlways(this, checked);
            if (checked) {
                NfcForegroundService.startIfNeeded(this);
            }
        });

        if (PrefHelper.getNfcAlways(this)) {
            NfcForegroundService.startIfNeeded(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUi();
    }

    private void updateUi() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
        if (adapter == null) {
            tvStatus.setText("NFC supported nahi hai is phone par");
        } else if (!adapter.isEnabled()) {
            tvStatus.setText("NFC band hai — quick settings se ON karein");
        } else {
            tvStatus.setText(R.string.status_ready);
        }
        boolean canTransmit = PrefHelper.getPinHash(this) != null && sessionUnlocked;
        btnPrepare.setEnabled(canTransmit);
    }

    private void refreshWhitelistView() {
        llWhitelist.removeAllViews();
        Set<String> wl = PrefHelper.getWhitelist(this);
        for (String link : wl) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            android.widget.TextView linkTv = new android.widget.TextView(this);
            linkTv.setText(link);
            linkTv.setLayoutParams(new LinearLayout.LayoutParams(
                    0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            android.widget.Button delBtn = new android.widget.Button(this);
            delBtn.setText("x");
            delBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            final String toRemove = link;
            delBtn.setOnClickListener(v -> {
                Set<String> cur = PrefHelper.getWhitelist(MainActivity.this);
                cur.remove(toRemove);
                PrefHelper.setWhitelist(MainActivity.this, cur);
                refreshWhitelistView();
            });
            row.addView(linkTv);
            row.addView(delBtn);
            llWhitelist.addView(row);
        }
    }

    private void onPrepare() {
        String link = etLink.getText().toString().trim();
        String secret = etSecret.getText().toString().trim();

        if (link.isEmpty() || !(link.startsWith("http://") || link.startsWith("https://"))) {
            Toast.makeText(this, R.string.err_invalid_link, Toast.LENGTH_LONG).show();
            return;
        }
        if (secret.isEmpty()) {
            Toast.makeText(this, R.string.err_secret, Toast.LENGTH_LONG).show();
            return;
        }
        if (PrefHelper.getPinHash(this) == null) {
            Toast.makeText(this, "Pehle PIN set karein", Toast.LENGTH_LONG).show();
            return;
        }
        if (!sessionUnlocked) {
            Toast.makeText(this, "PIN se unlock karein", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            PrefHelper.setSecret(this, secret);
            Set<String> wl = PrefHelper.getWhitelist(this);
            wl.add(link);
            PrefHelper.setWhitelist(this, wl);
            refreshWhitelistView();

            SecretKey key = CryptoUtil.deriveKey(secret);
            String encrypted = CryptoUtil.encrypt(link, key);
            byte[] ndefFile = NdefBuilder.buildNdefFile(encrypted);
            HceLinkService.setNdefFile(ndefFile);

            NfcForegroundService.startIfNeeded(this);
            tvStatus.setText(R.string.status_active);
            Toast.makeText(this, "NFC active — receiver phone se peechhe se peechhe tap karein",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Encrypt error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
