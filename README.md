# Aakash ID Punch

A single-purpose Android app for registering **your own Aakash Institute ID card** and trying to present it to the institute's NFC attendance reader with Android Host Card Emulation (HCE).

This is **not** a phone-to-phone link transmitter anymore. It does not push URLs, does not use Android Beam, does not write physical tags, and never displays a fake "attendance punched" popup. Only the institute's reader can record attendance.

> Use it only for your own card and only where the institute permits phone-based attendance.

---

## Why the old app could not punch attendance

The previous version was an NDEF **link transmitter** for two Android phones. It wrapped a fake `https://aakash-branch.psd-nfc-app/punch?...` URL and an Android App Record into NDEF. An attendance reader is not an Android phone and will never open a URL or an app record.

Android HCE has hard limits (see the [Android HCE overview](https://developer.android.com/develop/connectivity/nfc/hce) and [CardEmulation API](https://developer.android.com/reference/android/nfc/cardemulation/CardEmulation)):

- It emulates **ISO-DEP (ISO/IEC 14443-4) + ISO/IEC 7816-4 APDUs**, selected by an **AID**.
- The HCE UID is **random on every tap**; an app cannot set a fixed UID, ATQA, SAK or ATS.
- It cannot emulate **MIFARE Classic (Crypto1)**, **MIFARE Ultralight / Type 2**, **125 kHz proximity cards**, **barcodes** or **QR codes**.
- Even DESFire-style cards only work when the reader starts with an ISO `SELECT AID` and does not use native-only commands or live cryptographic challenge-response.

The old code also had concrete bugs:

- It checked `tagIdHex.startsWith("AAKASH")` for "Aakash detection" — impossible, because a UID hex string contains only `0-9A-F`.
- Its Type 4 NDEF emulation omitted the mandatory 2-byte `NLEN` prefix, used the message length as the CC max size, returned `6A82` for normal read errors, and could return NDEF data when the reader asked for the Capability Container. See the [Type 4 Tag NDEF file format](https://nrfconnectdocs.nordicsemi.com/ncs/3.0.2/nrfxlib/nfc/doc/type_4_tag.html).
- It only read NDEF or MIFARE Classic sector 0 with default keys; it never captured AIDs or APDU responses.
- Its manifest declared all NFC techs inside one `<tech-list>`, which means "all at once" and matches nothing.
- Its APK workflows targeted old Arena branches, not the current one.

---

## What v2.0 actually does

1. **Register one card.** Hold the physical card to the phone. The app captures:
   - UID, tech list, ATQA/SAK, ATS/historical bytes, NDEF bytes,
   - MIFARE Classic sector 0 / Ultralight pages 0-3 (diagnostics only),
   - ISO-DEP probe responses for NDEF AID, Type 4/DESFire AID, CC, NDEF file, DESFire version/application commands,
   - the exact Type 4 Capability Container and NDEF file (`NLEN + NDEF`) when the card supports the standard flow.
2. **Classify compatibility honestly.**
   - `Type 4 / NDEF` or `ISO-DEP / APDU` = **conditional** emulation.
   - `MIFARE Classic`, `MIFARE Ultralight`, `UID only`, `unsupported` = **not emulatable** on stock Android. The app says so instead of pretending.
3. **Register AIDs.** The two NFC Forum AIDs are in `apduservice.xml`; discovered and user-added AIDs are registered at runtime with `CardEmulation.registerAidsForService(..., CATEGORY_OTHER, ...)`.
4. **Serve a correct Type 4 tag.**
   - `6F <len> 84 <len> <AID> A5 00 9000` FCI on SELECT AID,
   - valid 15-byte Capability Container,
   - `SELECT E103` / `SELECT E104`,
   - `READ BINARY` / `READ RECORD` with offsets and short/extended Le,
   - NDEF file = `NLEN` + exact captured NDEF bytes,
   - proper status words: `9000`, `6A82`, `6B00`, `6700`, `6D00`, `6E00`, `6982`, `6985`.
5. **Replay reader-specific APDUs.** Captured or manually added command/response rules are checked before generic handling. Rules support `??` wildcard bytes and a trailing `*` prefix match.
6. **Log every tap.** `PunchActivity` shows the newest 40 C-APDU/R-APDU exchanges, the NFC/HCE state, AID registration result, and a copyable diagnostics report.
7. **Arm / disarm punch mode** from the home screen or the **Aakash Punch** Quick Settings tile.

The card profile is encrypted locally with AES-256-GCM using an Android Keystore key. Nothing is uploaded.

---

## Install / use

1. Install the APK built by the GitHub Actions workflow on branch `arena/87e17c3d-psd-nfc-app` (release tag `v2.0.0-aakash`, asset `app-release.apk`).
2. Open **Aakash ID Punch** → **REGISTER / REPLACE CARD** → hold the physical card to the back of the phone.
3. Read the **compatibility** line.
   - If it says `NOT EMULATABLE`, stop here: no app-only fix exists for that card/reader combination. Use the physical card or ask the institute to enroll a phone-compatible credential.
   - If it says `CONDITIONAL`, continue.
4. **START PUNCH MODE**, keep NFC ON and the **screen ON**, then hold the phone's NFC antenna (usually upper-middle of the back) to the reader for 2-3 seconds.
5. Open **PUNCH DIAGNOSTICS / READER LOG**:
   - **Log stays empty** → the reader never opened an HCE/ISO-DEP session. It is UID/MIFARE/125 kHz/barcode/QR, or uses an AID this app did not register.
   - **`6A82`** → the reader selected an AID/file that is not registered; add the exact AID from a reader trace.
   - **`6D00`** → the app received the APDU but has no rule for that instruction; add a command/response rule.
   - **`9000` responses but reader still rejects** → the reader needs different data, a fixed UID, or cryptographic state that HCE cannot reproduce.
6. Use **EDIT CARD / ADVANCED APDU RULES** to add AIDs or rules from a reader trace / card dump supplied by the institute.

Example rule:

```text
C-APDU:  00A4040007D276000085010100
R-APDU:  6F0B8407D2760000850101A5009000
```

Wildcards: `00B0????02` matches any 4-byte READ BINARY command ending in `02`; `90*` matches all wrapped DESFire commands. The R-APDU must include its status word.

---

## Hard limits — read this before debugging

- **Fixed UID reader:** impossible on stock Android HCE. The phone presents a random UID every tap.
- **MIFARE Classic / Ultralight reader:** impossible with HCE; those protocols sit below ISO-DEP and are not routed to `HostApduService`.
- **125 kHz RFID / barcode / QR reader:** Android NFC is 13.56 MHz and cannot read or emulate those at all.
- **Crypto challenge-response:** static APDU replay will fail. The card's secret keys are not extractable by phone NFC.
- **Screen off:** Android blocks NFC HCE while the screen is off on most devices. Keep the screen on.
- **AID conflicts:** if another HCE app claims the same AID, keep this app in the foreground or set it as the preferred service when Android asks.
- **Institute policy:** enrollment of a phone credential may need to be done by Aakash admin. This app cannot change the reader's database.

---

## Build

Pushing to `arena/87e17c3d-psd-nfc-app` runs `.github/workflows/build-apk.yml`:

- JDK 17 + Android SDK 34 + Gradle 8.7,
- `./gradlew assembleRelease`,
- signed APK uploaded as a workflow artifact and attached to release `v2.0.0-aakash`.

Local build:

```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

`minSdk 24`, `targetSdk 34`, pure Android framework code, no third-party libraries.
