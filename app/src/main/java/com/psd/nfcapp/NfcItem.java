package com.psd.nfcapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** One saved NFC tile: an ID card (scanned + duplicated) or a legacy typed link/text. */
public class NfcItem {
    public static final String KIND_LINK = "link";
    public static final String KIND_TEXT = "text";
    public static final String KIND_SCAN = "scan";
    public static final String KIND_PUNCH = "punch"; // Aakash branch ID card punch

    /** A custom label/value pair on an ID card, e.g. "Blood group" = "B+". */
    public static class Field {
        public String label;
        public String value;

        public Field() {
        }

        public Field(String label, String value) {
            this.label = label == null ? "" : label;
            this.value = value == null ? "" : value;
        }
    }

    public String id;
    public String kind;        // scan (ID card) | link | text (legacy) | punch (Aakash branch)
    public String payload;     // link / text / human-readable summary for scans / punch timestamp
    public String openLink;    // optional: link that should open on the receiving phone
    public String rawNdefHex;  // raw NDEF message bytes (scans/punches), may be null/empty
    public String tagIdHex;    // physical tag UID (scans/punches), may be null/empty
    public String tech;        // tech list of scanned tag, may be null/empty
    public String cardName;    // user-given ID card name, may be null/empty
    public String photoBase64; // JPEG photo of the card holder (base64), may be null/empty
    public List<Field> fields = new ArrayList<>();
    public List<String> tags = new ArrayList<>();
    public long createdAt;

    /** Name shown on the tile: the card name if set, otherwise the payload summary. */
    public String displayName() {
        if (kind != null && KIND_PUNCH.equals(kind)) {
            // Aakash branch punch: show branch + timestamp
            return "Aakash Branch Punch: " + (payload == null ? "" : payload);
        }
        if (cardName != null && !cardName.trim().isEmpty()) return cardName.trim();
        return payload == null ? "" : payload;
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("kind", kind);
            o.put("payload", payload == null ? "" : payload);
            o.put("openLink", openLink == null ? "" : openLink);
            o.put("rawNdefHex", rawNdefHex == null ? "" : rawNdefHex);
            o.put("tagIdHex", tagIdHex == null ? "" : tagIdHex);
            o.put("tech", tech == null ? "" : tech);
            o.put("cardName", cardName == null ? "" : cardName);
            o.put("photo", photoBase64 == null ? "" : photoBase64);
            o.put("createdAt", createdAt);
            JSONArray f = new JSONArray();
            for (Field fd : fields) {
                JSONObject fo = new JSONObject();
                fo.put("label", fd.label == null ? "" : fd.label);
                fo.put("value", fd.value == null ? "" : fd.value);
                f.put(fo);
            }
            o.put("fields", f);
            JSONArray a = new JSONArray();
            for (String t : tags) a.put(t);
            o.put("tags", a);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static NfcItem fromJson(String s) {
        try {
            JSONObject o = new JSONObject(s);
            NfcItem it = new NfcItem();
            it.id = o.optString("id");
            it.kind = o.optString("kind", KIND_TEXT);
            it.payload = o.optString("payload");
            it.openLink = o.optString("openLink");
            it.rawNdefHex = o.optString("rawNdefHex");
            it.tagIdHex = o.optString("tagIdHex");
            it.tech = o.optString("tech");
            it.cardName = o.optString("cardName");
            it.photoBase64 = o.optString("photo");
            it.createdAt = o.optLong("createdAt");
            JSONArray f = o.optJSONArray("fields");
            if (f != null) {
                for (int i = 0; i < f.length(); i++) {
                    JSONObject fo = f.optJSONObject(i);
                    if (fo != null) {
                        it.fields.add(new Field(fo.optString("label"), fo.optString("value")));
                    }
                }
            }
            JSONArray a = o.optJSONArray("tags");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) it.tags.add(a.getString(i));
            }
            return it;
        } catch (Exception e) {
            return null;
        }
    }
}
