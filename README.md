# Aakash Saharanpur ID — v3.0

A single-purpose Android app for **your own Aakash Institute, Saharanpur ID card** and the institute's NFC attendance reader. It is not a link/photo/phone-to-phone sharing app, it does not use Android Beam, and it never shows a fake "Attendance Punched" dialog. Only the institute's reader can record attendance.

> Use it only for your own card and only where the institute permits a phone/sticker credential. No real attendance punch has been tested with the Aakash reader by this project.

---

## 1. First: why the old attendance punching did not work

The original app was an NDEF **link transmitter** for two Android phones. It invented `https://aakash-branch.psd-nfc-app/punch?...` URLs, Android App Records, `AAKASH_BRANCH_PUNCH` payloads and a local success popup. An attendance reader is not an Android phone; it will not open a URL or an AAR. It also checked `uidHex.startsWith("AAKASH")`, which is impossible because a UID is hex `0-9A-F`.

The supplied card profile is **13.56 MHz HF, ISO/IEC 14443 Type A, passive NFC Forum Type 2 / NTAG213-216, NDEF text starting `AAKASH`**, no battery. That changes the fix: stock Android HCE **cannot present this credential**.

- Android HCE only emulates **ISO-DEP / ISO 7816-4 / NFC Forum Type 4**, selected by an AID. The HCE UID is **random every tap**; an app cannot set UID, ATQA, SAK or ATS. [Android HCE overview](https://developer.android.com/develop/connectivity/nfc/hce), [AOSP HCE docs](https://android.googlesource.com/platform/frameworks/base/+/c988ee6/docs/html/guide/topics/connectivity/nfc/hce.jd).
- A Type 2 / NTAG reader uses low-level **ISO 14443-3A READ / FAST_READ page commands**, not APDUs. HCE cannot emulate Type 2, MIFARE Ultralight, MIFARE Classic, 125 kHz, barcode or QR cards. [HCE cannot emulate Type 2](https://stackoverflow.com/questions/75878348/is-it-possible-to-emulate-an-nfc-tag-with-a-url-payload-on-an-omnikey-5127ck-reader-and-have-that-url-automatically-opened), [MIFARE Ultralight emulation on Android](https://stackoverflow.com/questions/40673529/mifare-ultralight-emulation-on-android/40680291), [JavaCard/NTAG Type 2 limits](https://stackoverflow.com/questions/44761119/can-a-javacard-emulate-a-mifare-ultralight-or-ntag-nfc-tag).
- **NDEF is only a data format**, not a transport. Android's `Ndef` API can read/write NDEF on Type 1-4 tags, but the physical reader must support the tag platform. [Android Ndef docs](https://docs.kony.com/NativeAPIs/Android/android.nfc.tech-Android-10.0/source/Ndef.html), [NFC Forum tag specifications](https://nfc-forum.org/build/specifications), [Type 2 vs Type 4](https://stackoverflow.com/questions/29596521/difference-between-nfc-forum-tag-2-and-nfc-forum-tag-4).
- NTAG213/215/216 have a **locked 7-byte UID**, an **ECC originality signature**, a **24-bit NFC read counter**, UID/counter ASCII mirrors and `FAST_READ`. Static NDEF is copyable; those chip features are not. [NXP NTAG21x datasheet](https://www.nxp.com/docs/en/data-sheet/NTAG213_215_216.pdf), [NXP product page](https://www.nxp.com/products/NTAG213_215_216).

v2.0 fixed the broken Type 4 NDEF emulator (`NLEN`, CC, status words, AID registration, APDU log), but a correct Type 4 emulator still cannot answer a Type-2-only reader. So v3.0 stops implying HCE can punch a Type 2 card.

---

## 2. Saharanpur-specific research (what is actually public)

- Official Aakash listings place the Saharanpur / Court Road centre at **No 8, 2nd Floor, Parsvnath Plaza, Court Road, Saharanpur, Uttar Pradesh 247001**, phone **+91 8800013106**, email **care@aesl.in**. [Official centre page](https://centers.aakash.ac.in/aakash-institute-court-road-coaching-center-court-road-saharanpur-99263/Home), [Aakash Saharanpur city page](https://www.aakash.ac.in/saharanpur-city).
- The public AIATS centre list contains **`S.No. 178 Saharanpur | CC-028`**. That is an **exam-centre code**, not confirmed attendance, branch or NDEF identifier. [AIATS centre PDF](https://dcx0p3on5z8dw.cloudfront.net/Aakash/s3fs-public/pdf_management_files/sm_sa/AIATS%20Center.pdf).
- Aakash's official myAakash guide describes **PSID as an 11-digit centre-issued ID**. That is a login/student identifier, not proof of an NFC payload. [myAakash login guide](https://www.aakash.ac.in/how-to-login-download-myaakash-app).
- Public reports conflict: one says students scan ID cards and parents get SMS; older answers mention barcode attendance cards. Reader behaviour may be branch-specific. [Parents' SMS claim](https://www.quora.com/Do-parents-get-notifications-when-students-are-absent-in-Aakash), [Allen/Aakash barcode claim](https://www.quora.com/How-do-ALLEN-or-Aakash-track-attendance-of-students).
- No public source confirms the **exact Aakash Saharanpur NDEF text**, reader model, UID allowlist, originality-signature check, counter check or backend API. `AAKASH-DELHI-001`, `CC-028`, `KIND_PUNCH` and `startsWith("AAKASH")` are **not** used as credential data. The app reads the exact bytes from the physical card.

**Conclusion:** for a Type-2-only reader, no ordinary Android HCE app fix makes the phone become that card. The closest actually working software path is to **capture the exact NDEF and write/verify it on a real NTAG sticker**, while keeping the Type 4 path only as a clearly labelled fallback.

---

## 3. What v3.0 changes

1. **Exact card capture.**
   - UID, tech list, ATQA/SAK, ATS, NDEF bytes, NDEF text records.
   - `AAKASH` branch identifier and possible 11-digit PSID parsed from the card's NDEF text, not guessed.
   - Type 2 / NTAG diagnostics: `GET_VERSION`, `READ_SIG` (ECC originality signature), `READ_CNT`, first Type 2 pages and Capability Container.
   - ISO-DEP / Type 4 AID, CC and exact `NLEN + NDEF` file capture when present.
2. **Honest classification.**
   - `Type 2 / NTAG / NDEF` → **NOT EMULATABLE BY HCE**. The UI says so, does not auto-arm HCE, and does not claim a punch.
   - `Type 4 / NDEF` or `ISO-DEP / APDU` → **conditional** HCE emulation.
   - MIFARE Classic / Ultralight / UID-only / barcode / 125 kHz → unsupported.
3. **Real NTAG sticker writer** (`WriteTagActivity`).
   - Builds the exact `NdefMessage` from the captured NDEF bytes.
   - Uses reader mode + `Ndef.writeNdefMessage()` / `NdefFormatable.format()` on a blank NTAG213/215/216 sticker. [Reliable NDEF writing](https://stackoverflow.com/questions/64920307/how-to-write-ndef-records-to-nfc-tag).
   - Reads the sticker back and compares byte-for-byte; logs `TAG_WRITE` / `TAG_VERIFY`.
   - Optional one-way `makeReadOnly()` lock, off by default.
   - Clearly warns that the sticker has a **different UID and originality signature**.
4. **Type 4 HCE fallback only.** The HCE service still serves the exact NDEF as a correct Type 4 tag for readers that also support Type 4 and ignore UID. It is labelled "TYPE-4 FALLBACK", not "punch". A Type-2-only reader will never reach `HostApduService`, so its diagnostics log stays empty.
5. **No invented branch credential.** Saharanpur address/phone and `CC-028` appear only as context. The expected NDEF prefix defaults to `AAKASH`; the exact identifier comes from the card. A warning appears if the user's expected prefix is not found in the NDEF text.
6. **Diagnostics and unit tests.** APDU/tag log is copyable; `NfcUtils` has JUnit tests for NDEF text parsing, `AAKASH`/PSID detection, APDU wildcard rules and hex/status words.

The single card profile is encrypted locally with AES-256-GCM using an Android Keystore key. Nothing is uploaded.

---

## 4. Install / use

APK: [release `v3.0.0-saharanpur` → `app-release.apk`](https://github.com/pavit12301611/psd-nfc-app/releases/download/v3.0.0-saharanpur/app-release.apk)

1. Open **Aakash Saharanpur ID** → **REGISTER / REPLACE CARD** → hold the physical Aakash ID card to the back of the phone.
2. Review the captured **NDEF text**, `AAKASH` identifier, UID, NTAG version and compatibility line.
3. If it says **Type 2 / NTAG / NDEF**:
   - Do **not** rely on HCE for the real reader.
   - Tap **WRITE EXACT NDEF TO NTAG STICKER**.
   - Hold a blank NTAG213 / NTAG215 / NTAG216 sticker to the phone; wait for **WROTE AND VERIFIED**.
   - Attach the sticker to the phone/card and test at the institute reader.
   - If the reader rejects it, it is probably checking UID, originality signature, counter or online state. Use the physical card, ask the institute to enroll the sticker UID, or use an authorised external Type-2 emulator. [ChameleonUltra NTAG21x support](https://github.com/RfidResearchGroup/ChameleonUltra/blob/main/CHANGELOG.md), [Chameleon Ultra product](https://lab401.com/products/chameleon-ultra), [NFC attendance sticker example](https://buy.serialio.com/products/nfc-sticker-tag-for-iphone-android-built-in-rfid-18mm-square).
4. If it says **Type 4 / NDEF** or **ISO-DEP / APDU**:
   - **START PUNCH MODE**, keep NFC ON and screen ON, hold the phone to the reader.
   - Open **PUNCH DIAGNOSTICS / READER LOG** and inspect APDUs.
5. **ARM TYPE-4 FALLBACK** is for mixed Type-2/Type-4 readers only. It is not a Type 2 clone.
6. **EDIT CARD / ADVANCED APDU RULES** lets you add AIDs or exact C-APDU/R-APDU rules from a reader trace supplied by the institute.

Example rule:

```text
C-APDU:  00A4040007D276000085010100
R-APDU:  6F0B8407D2760000850101A5009000
```

Wildcards: `00B0????02` matches any 4-byte READ BINARY ending in `02`; `90*` matches all wrapped DESFire commands. The R-APDU must include its status word.

---

## 5. Hard limits — read before debugging

- **Type-2-only reader:** impossible on stock Android HCE. The reader sends page commands; HCE sees APDUs only.
- **UID allowlist / originality signature / NFC counter validation:** a normal blank NTAG sticker has a different 7-byte UID and NXP signature and cannot match the counter. [NXP datasheet](https://www.nxp.com/docs/en/data-sheet/NTAG213_215_216.pdf).
- **Static NDEF is copyable:** if the reader only reads NDEF text, the sticker can work; if it uses crypto, counters, passwords, NTAG424 DNA or a backend challenge, it will not.
- **MIFARE Classic / Ultralight / 125 kHz / barcode / QR:** Android NFC cannot emulate these with HCE.
- **Crypto challenge-response:** static APDU replay fails; card secrets are not extractable by phone NFC.
- **Screen off:** Android blocks HCE while the screen is off on most devices. Keep the screen on.
- **Institute policy / reader database:** the app cannot add a phone or sticker UID to the institute's allowlist. Enrollment may require Aakash admin.
- **"Perfect" compatibility:** cannot be promised without the actual reader model, a full card dump and a reader trace. Green CI, `TAG_VERIFY_OK`, and `9000` APDUs are **not** proof of an attendance punch.
- **External hardware:** ChameleonUltra / Proxmark / Flipper-style devices can emulate Type 2, but they are separate hardware, need the real card data and permission, and do not turn stock Android HCE into Type 2. [ChameleonUltra changelog](https://github.com/RfidResearchGroup/ChameleonUltra/blob/main/CHANGELOG.md).

---

## 6. Build / test

Pushing to `arena/87e17c3d-psd-nfc-app` runs `.github/workflows/build-apk.yml`:

- JDK 17 + Android SDK 34 + Gradle 8.7,
- `./gradlew --no-daemon --stacktrace test assembleRelease`,
- signed APK uploaded as artifact `aakash-saharanpur-apk`,
- attached to release `v3.0.0-saharanpur`.

Local:

```bash
./gradlew test assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

`minSdk 24`, `targetSdk 34`, Java 17, Android framework APIs plus JUnit 4 for local unit tests only.

---

## 7. Sources

1. [Android HCE overview](https://developer.android.com/develop/connectivity/nfc/hce)
2. [AOSP HCE documentation](https://android.googlesource.com/platform/frameworks/base/+/c988ee6/docs/html/guide/topics/connectivity/nfc/hce.jd)
3. [HCE cannot emulate Type 2 / NDEF tag](https://stackoverflow.com/questions/75878348/is-it-possible-to-emulate-an-nfc-tag-with-a-url-payload-on-an-omnikey-5127ck-reader-and-have-that-url-automatically-opened)
4. [MIFARE Ultralight emulation on Android](https://stackoverflow.com/questions/40673529/mifare-ultralight-emulation-on-android/40680291)
5. [JavaCard cannot emulate NTAG / Type 2](https://stackoverflow.com/questions/44761119/can-a-javacard-emulate-a-mifare-ultralight-or-ntag-nfc-tag)
6. [Android Ndef API / Type 1-4](https://docs.kony.com/NativeAPIs/Android/android.nfc.tech-Android-10.0/source/Ndef.html)
7. [NFC Forum tag specifications](https://nfc-forum.org/build/specifications)
8. [NXP NTAG213/215/216 datasheet](https://www.nxp.com/docs/en/data-sheet/NTAG213_215_216.pdf)
9. [NXP NTAG21x product page](https://www.nxp.com/products/NTAG213_215_216)
10. [Official Aakash Saharanpur / Court Road centre](https://centers.aakash.ac.in/aakash-institute-court-road-coaching-center-court-road-saharanpur-99263/Home)
11. [Aakash Saharanpur city page](https://www.aakash.ac.in/saharanpur-city)
12. [AIATS centre list PDF (`CC-028`)](https://dcx0p3on5z8dw.cloudfront.net/Aakash/s3fs-public/pdf_management_files/sm_sa/AIATS%20Center.pdf)
13. [myAakash login / 11-digit PSID](https://www.aakash.ac.in/how-to-login-download-myaakash-app)
14. [Public report: ID-card scan / parent SMS](https://www.quora.com/Do-parents-get-notifications-when-students-are-absent-in-Aakash)
15. [Public report: barcode attendance cards](https://www.quora.com/How-do-ALLEN-or-Aakash-track-attendance-of-students)
16. [Reliable NDEF writes with reader mode](https://stackoverflow.com/questions/64920307/how-to-write-ndef-records-to-nfc-tag)
17. [NFC sticker for attendance](https://buy.serialio.com/products/nfc-sticker-tag-for-iphone-android-built-in-rfid-18mm-square)
18. [ChameleonUltra NTAG21x emulation changelog](https://github.com/RfidResearchGroup/ChameleonUltra/blob/main/CHANGELOG.md)
19. [Chameleon Ultra product / NTAG support](https://lab401.com/products/chameleon-ultra)
20. [Generic NDEF-text attendance reader example](https://github.com/Pratheesh-555/nfc-attendance)
21. [Type 2 vs Type 4 NDEF transport](https://stackoverflow.com/questions/29596521/difference-between-nfc-forum-tag-2-and-nfc-forum-tag-4)
