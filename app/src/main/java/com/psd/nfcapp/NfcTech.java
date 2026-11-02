package com.psd.nfcapp;

import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;

public class NfcTech {
    public static String[][] techLists() {
        return new String[][]{{NfcA.class.getName(), NfcB.class.getName(), NfcF.class.getName(),
                NfcV.class.getName(), IsoDep.class.getName(), MifareClassic.class.getName(),
                MifareUltralight.class.getName(), Ndef.class.getName(), NdefFormatable.class.getName()}};
    }
}
