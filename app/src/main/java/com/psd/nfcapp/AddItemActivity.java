package com.psd.nfcapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.UUID;

/** Way 1: user types a link or text manually. Saved as a tile and armed for transmit. */
public class AddItemActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add);

        EditText payload = findViewById(R.id.inputPayload);
        EditText tags = findViewById(R.id.inputTags);
        EditText openLink = findViewById(R.id.inputOpenLink);
        Button save = findViewById(R.id.btnSave);

        save.setOnClickListener(v -> {
            String p = payload.getText().toString().trim();
            if (p.isEmpty()) {
                Toast.makeText(this, "Enter a link or text first", Toast.LENGTH_SHORT).show();
                return;
            }
            NfcItem it = new NfcItem();
            it.id = UUID.randomUUID().toString();
            it.kind = NfcUtils.looksLikeLink(p) ? NfcItem.KIND_LINK : NfcItem.KIND_TEXT;
            it.payload = p;
            it.openLink = openLink.getText().toString().trim();
            it.createdAt = System.currentTimeMillis();
            for (String s : tags.getText().toString().split(",")) {
                String t = s.trim();
                if (!t.isEmpty() && !it.tags.contains(t)) it.tags.add(t);
            }
            new VaultStore(this).save(it);
            HceService.setArmed(this, it.id);
            Toast.makeText(this, "Saved & armed — transmitting now", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, TransmitActivity.class));
            finish();
        });
    }
}
