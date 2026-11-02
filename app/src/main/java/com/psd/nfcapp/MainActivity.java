package com.psd.nfcapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Home screen: black tile grid of saved NFCs. Tap = arm & transmit, long-press = edit. */
public class MainActivity extends Activity {
    private NfcAdapter nfc;
    private VaultStore store;
    private List<NfcItem> items;
    private TileAdapter adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = new VaultStore(this);
        nfc = NfcAdapter.getDefaultAdapter(this);

        GridView grid = findViewById(R.id.grid);
        empty = findViewById(R.id.empty);
        adapter = new TileAdapter();
        grid.setAdapter(adapter);

        grid.setOnItemClickListener((parent, view, position, id) -> {
            NfcItem it = items.get(position);
            HceService.setArmed(this, it.id);
            Toast.makeText(this, "Armed & transmitting: " + shortText(it), Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, TransmitActivity.class));
        });
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            showItemDialog(items.get(position));
            return true;
        });

        Button add = findViewById(R.id.btnAdd);
        add.setOnClickListener(v -> showAddChooser());

        if (nfc == null) {
            Toast.makeText(this, "NFC is not available on this device", Toast.LENGTH_LONG).show();
        }
        handleIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        HceService.refresh(this);
        reload();
        if (nfc != null) enableDispatch();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfc != null) nfc.disableForegroundDispatch(this);
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
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Parcelable[] raw;
            if (Build.VERSION.SDK_INT >= 33) {
                raw = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, Parcelable.class);
            } else {
                raw = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            }
            if (raw != null && raw.length > 0 && raw[0] instanceof NdefMessage) {
                String desc = NfcUtils.describe((NdefMessage) raw[0]);
                if (!desc.isEmpty()) {
                    Toast.makeText(this, "Received via NFC: " + desc, Toast.LENGTH_LONG).show();
                }
            }
        } else if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            Toast.makeText(this, "NFC tag detected", Toast.LENGTH_SHORT).show();
        }
    }

    private void enableDispatch() {
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        try {
            ndef.addDataScheme("http");
            ndef.addDataScheme("https");
        } catch (Exception ignored) {
        }
        IntentFilter tech = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        IntentFilter tag = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        nfc.enableForegroundDispatch(this, pi, new IntentFilter[]{ndef, tech, tag}, NfcTech.techLists());
    }

    private void reload() {
        items = store.loadAll();
        adapter.notifyDataSetChanged();
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddChooser() {
        new AlertDialog.Builder(this)
                .setTitle("Add NFC — choose a way")
                .setItems(new CharSequence[]{
                        "✏️  Type link / text",
                        "📲  Scan & duplicate (ID card / tag)"
                }, (d, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(this, AddItemActivity.class));
                    } else {
                        startActivity(new Intent(this, ScanActivity.class));
                    }
                })
                .show();
    }

    private void showItemDialog(NfcItem it) {
        new AlertDialog.Builder(this)
                .setTitle(shortText(it))
                .setItems(new CharSequence[]{
                        "▶  Arm & transmit",
                        "🏷  Edit tags",
                        "🗑  Delete"
                }, (d, which) -> {
                    if (which == 0) {
                        HceService.setArmed(this, it.id);
                        startActivity(new Intent(this, TransmitActivity.class));
                    } else if (which == 1) {
                        showEditTags(it);
                    } else {
                        store.delete(it.id);
                        HceService.refresh(this);
                        reload();
                    }
                })
                .show();
    }

    private void showEditTags(NfcItem it) {
        final EditText input = new EditText(this);
        input.setBackgroundResource(R.drawable.input_bg);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF777777);
        input.setSingleLine(true);
        input.setText(TextUtils.join(", ", it.tags));
        new AlertDialog.Builder(this)
                .setTitle("Tags (comma separated)")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    it.tags.clear();
                    for (String s : input.getText().toString().split(",")) {
                        String t = s.trim();
                        if (!t.isEmpty() && !it.tags.contains(t)) it.tags.add(t);
                    }
                    store.save(it);
                    reload();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String shortText(NfcItem it) {
        String p = it.payload == null ? "" : it.payload;
        return p.length() > 40 ? p.substring(0, 40) + "…" : p;
    }

    private class TileAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items == null ? 0 : items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_tile, parent, false);
            }
            NfcItem it = items.get(position);
            TextView payload = v.findViewById(R.id.tilePayload);
            TextView tags = v.findViewById(R.id.tileTags);
            TextView armed = v.findViewById(R.id.tileArmed);
            payload.setText(shortText(it));

            SpannableStringBuilder sb = new SpannableStringBuilder();
            for (int i = 0; i < it.tags.size(); i++) {
                if (i > 0) sb.append("  ");
                int start = sb.length();
                sb.append("#").append(it.tags.get(i));
                sb.setSpan(new ForegroundColorSpan(NfcUtils.colorForTag(it.tags.get(i))),
                        start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            tags.setText(sb);
            tags.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);

            boolean isArmed = it.id != null && it.id.equals(HceService.getArmedId());
            armed.setVisibility(isArmed ? View.VISIBLE : View.GONE);
            return v;
        }
    }
}
