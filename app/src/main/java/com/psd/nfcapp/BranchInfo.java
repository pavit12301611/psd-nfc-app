package com.psd.nfcapp;

/**
 * Saharanpur-specific context gathered from public Aakash sources.
 *
 * These are hints for the UI only. A branch identifier is shown only when it is
 * actually present in a captured NDEF text record; for MIFARE Classic cards with
 * no NDEF text we deliberately do NOT invent a full code like
 * "AAKASH-SAHARANPUR-001", "CC-028" or a Delhi code.
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

    /**
     * No branch prefix is assumed by default. Leave this empty unless an exact NDEF
     * text record was captured; never invent "AAKASH", "CC-028" or a Delhi code.
     */
    public static final String NDEF_PREFIX_UNSET = "";

    public static String centreSummary() {
        return CENTRE_NAME + "\n" + ADDRESS + "\nPhone: " + PHONE
                + "\nPublic AIATS exam-centre code: " + AIATS_CENTRE_CODE
                + " (not confirmed as the NFC branch ID)";
    }

    private BranchInfo() {
    }
}
