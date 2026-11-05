package com.psd.nfcapp;

/**
 * Saharanpur-specific context gathered from public Aakash sources.
 *
 * These are hints for the UI only. The exact branch identifier is read from the
 * physical card's NDEF text record; we deliberately do NOT invent a full code like
 * "AAKASH-SAHARANPUR-001" because no public source confirms the card encoding.
 */
public final class BranchInfo {
    public static final String CITY = "Saharanpur";
    public static final String STATE = "Uttar Pradesh";
    public static final String PIN = "247001";
    public static final String CENTRE_NAME = "Aakash Institute, Court Road, Saharanpur";
    public static final String ADDRESS =
            "No 8, 2nd Floor, Parsvnath Plaza, Court Road, Saharanpur, Uttar Pradesh 247001";
    public static final String PHONE = "+91 8800013106";
    public static final String EMAIL = "care@aesl.in";

    /** Official centre listing id; not the NFC branch identifier. */
    public static final String CENTRE_PAGE_ID = "99263";

    /** From the public AIATS centre-code list: exam-centre code, not confirmed NFC ID. */
    public static final String AIATS_CENTRE_CODE = "CC-028";

    /** Aakash's official login guide describes PSID as an 11-digit centre-issued ID. */
    public static final int PSID_DIGITS = 11;

    /** Only this prefix is safe to assume; the rest must come from the card. */
    public static final String EXPECTED_NDEF_PREFIX = "AAKASH";

    public static String centreSummary() {
        return CENTRE_NAME + "\n" + ADDRESS + "\nPhone: " + PHONE
                + "\nPublic AIATS exam-centre code: " + AIATS_CENTRE_CODE
                + " (not confirmed as the NFC branch ID)";
    }

    private BranchInfo() {
    }
}
