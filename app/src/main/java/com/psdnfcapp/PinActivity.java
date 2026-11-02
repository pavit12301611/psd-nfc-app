package com.psdnfcapp;

import android.content.Intent;
import android.os.Bundle;
import android.security.KeyStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.security.MessageDigest;

/**
 * PIN lock: setup (first time) or verify (every subsequent open).
 * The PIN hash (SHA-256) is stored in SharedPreferences; the PIN itself is never stored.
 */
public class PinActivity extends AppCompatActivity {
    private EditText etPin;
    private EditText etConfirm;
    private Button btnAction;
    private TextView tvMode;
    private String mode = "verify";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);

        etPin = findViewById(R.id.et_pin);
        etConfirm = findViewById(R.id.et_confirm);
        btnAction = findViewById(R.id.btn_action);
        tvMode = findViewById(R.id.tv_mode);

        mode = getIntent().getStringExtra("mode");
        if ("set".equals(mode)) {
            tvMode.setText(R.string.pin_set);
            etConfirm.setVisibility(View.VISIBLE);
            btnAction.setText(R.string.pin_confirm);
        } else {
            tvMode.setText(R.string.pin_verify);
            etConfirm.setVisibility(View.GONE);
            btnAction.setText(R.string.btn_unlock);
        }

        btnAction.setOnClickListener(v -> onAction());
    }

    private void onAction() {
        String pin = etPin.getText().toString().trim();
        if (pin.length() < 4 || pin.length() > 6 || !pin.matches("\\d+")) {
            Toast.makeText(this, "4-6 ankka PIN daalein", Toast.LENGTH_SHORT).show();
            return;
        }

        if ("set".equals(mode)) {
            String confirm = etConfirm.getText().toString().trim();
            if (!pin.equals(confirm)) {
                Toast.makeText(this, "PIN match nahi kiya", Toast.LENGTH_SHORT).show();
                return;
            }
            PrefHelper.setPinHash(this, sha256(pin));
            Intent res = new Intent();
            res.putExtra("unlocked", true);
            setResult(RESULT_OK, res);
            finish();
        } else {
            String stored = PrefHelper.getPinHash(this);
            if (stored != null && stored.equals(sha256(pin))) {
                Intent res = new Intent();
                res.putExtra("unlocked", true);
                setResult(RESULT_OK, res);
                finish();
            } else {
                Toast.makeText(this, R.string.pin_wrong, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
