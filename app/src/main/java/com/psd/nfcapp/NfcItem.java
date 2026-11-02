package com.psd.nfcapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** One saved NFC tile: a typed link/text or a scanned (duplicated) tag. */
public class NfcItem {
    public static final String KIND_LINK = "link";
    public static final String KIND_TEXT = "text";
    public static final String KIND_SCAN = "scan";

    public String id;
    public String kind;        // link | text | scan
    public String payload;     // link / text / human-readable summary for scans
    public String rawNdefHex;  // raw NDEF message bytes (scans), may be null/empty
    public String tagIdHex;    // physical tag UID (scans), may be null/empty
    public String tech;        // tech list of scanned tag, may be null/empty
    public List<String> tags = new ArrayList<>();
    public long createdAt;

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("kind", kind);
            o.put("payload", payload == null ? "" : payload);
            o.put("rawNdefHex", rawNdefHex == null ? "" : rawNdefHex);
            o.put("tagIdHex", tagIdHex == null ? "" : tagIdHex);
            o.put("tech", tech == null ? "" : tech);
            o.put("createdAt", createdAt);
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
            it.rawNdefHex = o.optString("rawNdefHex");
            it.tagIdHex = o.optString("tagIdHex");
            it.tech = o.optString("tech");
            it.createdAt = o.optLong("createdAt");
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
