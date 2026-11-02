package com.psdnfcapp;

import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;

/**
 * Receiver screen: opened automatically when another phone taps this phone's NFC
 * and discovers our external-type NDEF record. Decrypts the link with the shared
 * secret key and offers to open it in the browser.
 */
public class ReceiverActivity extends AppCompatActivity {
    private TextView tvLink;
    private TextView tvError;
    private Button btnDecrypt;
    private Button btnOpen;
    private String decryptedLink = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiver);

        tvLink = findViewById(R.id.tv_link);
        tvError = findViewById(R.id.tv_error);
        btnDecrypt = findViewById(R.id.btn_decrypt);
        btnOpen = findViewById(R.id.btn_open);
        btnOpen.setEnabled(false);

        String secret = PrefHelper.getSecret(this);
        if (secret == null) {
            tvError.setText(R.string.err_secret);
            btnDecrypt.setEnabled(false);
        }

        Parcelable[] raw = getIntent().getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        if (raw != null) {
            for (Parcelable p : raw) {
                if (!(p instanceof NdefMessage)) {
                    continue;
                }
                NdefMessage msg = (NdefMessage) p;
                for (NdefRecord rec : msg.getRecords()) {
                    if (rec.getTnf() != NdefRecord.TNF_EXTERNAL) {
                        continue;
                    }
                    String type;
                    try {
                        type = new String(rec.getType(), StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        continue;
                    }
                    if (!type.equals(NdefBuilder.EXTERNAL_DOMAIN + ":" + NdefBuilder.EXTERNAL_TYPE)) {
                        continue;
                    }
                    String b64;
                    try {
                        b64 = new String(rec.getPayload(), StandardCharsets.UTF_8).trim();
                    } catch (Exception e) {
                        continue;
                    }
                    String curSecret = PrefHelper.getSecret(this);
                    if (curSecret == null) {
                        tvError.setText(R.string.err_secret);
                        return;
                    }
                    try {
                        SecretKey key = CryptoUtil.deriveKey(curSecret);
                        decryptedLink = CryptoUtil.decrypt(b64, key);
                        tvLink.setText(decryptedLink);
                        tvError.setText("");
                        btnOpen.setEnabled(true);
                        return;
                    } catch (Exception e) {
                        tvError.setText(R.string.err_wrong_key);
                    }
                }
            }
        }
    }

    public void onDecrypt(View view) {
        // Decryption already runs on create; this button is a retry hook.
        finish();
        startActivity(getIntent());
    }

    public void onOpen(View view) {
        if (decryptedLink != null) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse(decryptedLink)));
        } else {
            Toast.makeText(this, R.string.err_wrong_key, Toast.LENGTH_SHORT).show();
        }
    }
}
