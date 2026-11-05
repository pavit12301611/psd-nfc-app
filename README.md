# Aakash Saharanpur ID — v3.1.2 (MIFARE Classic)

A single-purpose Android app for **your own Aakash Institute, Saharanpur ID card** and the institute's NFC attendance reader. It is not a link/photo/phone-to-phone sharing app, it does not use Android Beam, and it never shows a fake "Attendance Punched" dialog. Only the institute's reader can record attendance.

> Use it only for your own card and only where the institute permits a phone/sticker/magic-card credential. No real attendance punch has been tested with the Aakash reader by this project.

---

## 1. First: why the old attendance punching did not work

The original app was an NDEF **link transmitter** for two Android phones. It invented `https://aakash-branch.psd-nfc-app/punch?...` URLs, Android App Records, `AAKASH_BRANCH_PUNCH` payloads and a local success popup. An attendance reader is not an Android phone; it will not open a URL or an AAR. It also checked `uidHex.startsWith("AAKASH")`, which is impossible because a UID is hex `0-9A-F`.

**Actual card evidence from your screenshots:**

- Tech list: `MifareClassic, NfcA, NdefFormatable`.
- Mode: **MIFARE Classic 1K / Crypto1**, not Type 2 / NTAG.
- `ATQA 0400`, `SAK 08`, 16 sectors, 1024 bytes, 64 blocks.
- `NDEF text: none`; `NdefFormatable` is present but the card is **not NDEF-formatted**.
- Sector 0 was readable with the common default key `FFFFFFFFFFFF`.
- Earlier v3.0 incorrectly assumed Type 2 / NTAG and showed a false `"Ultralight: NFC-A Type 2 (NfcA only)"` line because `NfcA` was present. v3.1 fixes that: `NfcA` alone is not Type 2, and MIFARE Classic takes priority.

The user confirmed this card punches over **NFC**, including from the other side, so the printed barcode is **not the active credential for this card**. The card photos show a printed barcode, Roll No, User ID and Branch Term, plus the instruction "Entry in the classroom is strictly by ID Card"; those are visual/admin or alternate identifiers, not an NFC payload. Aakash's own admission/terms documents say entry to classes is only with the ID card [1](https://www.aakash.ac.in/sites/default/files/medical_acst_admission-form.pdf) [5](https://dcx0p3on5z8dw.cloudfront.net/Aakash/s3fs-public/pdf_management_files/target_solutions/Updatd_Terms_and_Conditions_03_07_2023.pdf), without naming the reader technology. A deeper official-source check now shows the institute's attendance platform can ingest **Barcode, RFID and biometric machines** (§2.1), so "Aakash never uses barcode" would also be wrong. Public reports are branch/era dependent — one answers about **Allen** barcode attendance cards [2](https://www.quora.com/How-do-ALLEN-or-Aakash-track-attendance-of-students), another says Aakash ID-card scans trigger parent SMS and explicitly caveats "might not be true for all the branches" [3](https://www.quora.com/Do-parents-get-notifications-when-students-are-absent-in-Aakash). For **your** Saharanpur card, the confirmed active path is the embedded MIFARE Classic NFC chip.

### The hard protocol limit

- Android HCE only emulates **ISO-DEP / ISO 7816-4 / NFC Forum Type 4**, selected by an AID. The HCE UID is **random every tap**; an app cannot set UID, ATQA, SAK or ATS [1](https://developer.android.com/develop/connectivity/nfc/hce) [2](https://android.googlesource.com/platform/frameworks/base/+/c988ee/docs/html/guide/topics/connectivity/nfc/hce.jd).
- MIFARE Classic is a proprietary NXP protocol on top of ISO 14443-3 with **Crypto1** authentication and a **fixed 4-byte UID**. It is not APDU-based, so stock Android HCE cannot emulate it [1](https://stackoverflow.com/questions/48007948/how-to-simulate-mifare-classic-1k-card-with-android) [5](https://stackoverflow.com/questions/76850329/mifare-classic-emulation-not-working-i-havent-found-a-complete-code-or-step-b/76850393#76850393) [10](https://stackoverflow.com/questions/65833264/sending-data-using-hce-or-using-secure-element-android-kotlin-mifare-1k).
- `SAK 08` / `ATQA 0400` are consistent with MIFARE Classic 1K; block 0 (UID/BCC/SAK/ATQA/manufacturer data) is **permanently read-only on normal cards** [4](https://blog.linux-ng.de/2026/10/16/proxmark3-mifare-classic-bring-up/) [9](https://stackoverflow.com/questions/53731550/what-determines-the-sak-of-a-mifare-device).
- A 1K card has **16 sectors × 4 blocks × 16 bytes = 1024 bytes**; each sector trailer stores Key A, access bits, GPB and Key B [1](https://github.com/XaviTorello/mifare-classic-toolkit/blob/master/README.md) [4](https://deepwiki.com/miguelbalboa/rfid/6.1-mifare-classic-cards).

So **no ordinary HCE code change can make the phone become this card**. The working software path is to capture the exact full dump and write it to a real UID-changeable **Gen2/CUID magic MIFARE Classic 1K card**, or to an authorised external emulator.

---

## 2. Saharanpur-specific research

- Official Aakash listings place the Saharanpur / Court Road centre at **No 8, 2nd Floor, Parsvnath Plaza, Court Road, Saharanpur, Uttar Pradesh 247001**, phone **+91 8800013106**, email **care@aesl.in**. [Official centre page](https://centers.aakash.ac.in/aakash-institute-court-road-coaching-center-court-road-saharanpur-99263/Home), [Aakash Saharanpur city page](https://www.aakash.ac.in/saharanpur-city).
- The public AIATS centre list contains **`S.No. 178 Saharanpur | CC-028`**. That is an **exam-centre code**, not confirmed attendance, branch or NFC identifier. [AIATS centre PDF](https://dcx0p3on5z8dw.cloudfront.net/Aakash/s3fs-public/pdf_management_files/sm_sa/AIATS%20Center.pdf).
- Aakash's official myAakash guide describes **PSID as an 11-digit centre-issued ID**. That is a login/student identifier, not proof of an NFC payload. [myAakash login guide](https://www.aakash.ac.in/how-to-login-download-myaakash-app).
- No public source confirms the exact Saharanpur reader model, UID allowlist, Crypto1 sector usage, key diversification or backend API. `AAKASH-DELHI-001`, `CC-028`, `KIND_PUNCH`, printed Roll No / User ID / barcode number and `startsWith("AAKASH")` are **not** used as credential data.

### 2.1 Deep search: what Aakash actually uses (2026-11-05)

**Short answer:** Aakash does **not** publish "one protocol for all centres". Its official attendance monitoring pages say the platform can **pull attendance from Barcode / RFID / biometric machines** [26](https://www.aakash.ac.in/why-aakash) [27](https://dlp.aakash.ac.in/dlp-why-aakash). That is an institution-level capability list, **not** proof that the Saharanpur Court Road centre uses barcode today, and not proof that every branch uses the same reader.

| Official / public source | What it actually says | What it does **not** prove |
|---|---|---|
| Official **Why Aakash** and **DLP Why Aakash** pages [26](https://www.aakash.ac.in/why-aakash) [27](https://dlp.aakash.ac.in/dlp-why-aakash) | "Daily Attendance Monitoring": track daily / lecture-wise attendance, alert absentees, and **pull attendance from Barcode / RFID / biometric machines**. | Which machine is installed at Saharanpur, or that this card's printed barcode is enrolled. |
| Official **Benefits for Students** page [28](https://www.aakash.ac.in/benefits-aakash-students) and 2025 prospectus [30](https://d28cs5wqm3s0uy.cloudfront.net/AAKASH_V2/BrochureSection/pdf/Aakash_Medical_Engineering_Foundation_Prospectus_English_2025_Updated_2_1_1_1766082328507.pdf) | "Attendance Alert through App notifications"; prospectus describes notifying parents through app / SMS / email channels. | That the app **marks** attendance. It reports attendance captured elsewhere. |
| Official **myAakash** app listing [29](https://play.google.com/store/apps/details?id=com.aakash.myaakashapp) | Tracks missed lectures / upcoming classes; QR-code scanning is for **Foundation Study Module videos**, not for punching in. | That QR / barcode scanning in the app is an attendance credential. |
| Official admission form [20](https://www.aakash.ac.in/sites/default/files/medical_acst_admission-form.pdf) and terms [21](https://dcx0p3on5z8dw.cloudfront.net/Aakash/s3fs-public/pdf_management_files/target_solutions/Updatd_Terms_and_Conditions_03_07_2023.pdf) | "Entry in the classes / Institute will be only with Identity Card." | Whether the ID card is read as barcode, MIFARE Classic UID, sector data, biometric, or a mix. |
| Official Saharanpur centre pages [16](https://centers.aakash.ac.in/aakash-institute-court-road-coaching-center-court-road-saharanpur-99263/Home) [17](https://www.aakash.ac.in/saharanpur-city) and AIATS centre PDF [18](https://dcx0p3on5z8dw.cloudfront.net/Aakash/s3fs-public/pdf_management_files/sm_sa/AIATS%20Center.pdf) | Court Road address; `CC-028` is an **AIATS exam-centre code**. | Any NFC/barcode reader model or card protocol. `CC-028` is not an NFC branch ID. |
| Quora reports [22](https://www.quora.com/How-do-ALLEN-or-Aakash-track-attendance-of-students) [23](https://www.quora.com/Do-parents-get-notifications-when-students-are-absent-in-Aakash) | One answer describes **Allen** barcode attendance cards; another says Aakash ID-card scans trigger parent SMS but adds "might not be true for all the branches". | Official confirmation, current date, or Saharanpur-specific hardware. These are unverified and clearly branch / era dependent. |

**Reconciling that with your card:** the card has **both** a printed 1D barcode **and** an embedded MIFARE Classic 1K chip. You explicitly tested NFC punching from either side, and the v3.1 app captured a complete 16-sector MIFARE Classic dump with no NDEF text / AAKASH string / 11-digit PSID. So for **this card at your reader**, the active credential path is **RFID / NFC (MIFARE Classic)**, not the printed barcode. The printed barcode may be a legacy number, an admin/visual identifier, or an alternate path used by centres with barcode machines. Aakash supporting barcode machines somewhere in its network does **not** override your own NFC test.

**Therefore:** do **not** build a camera barcode scanner path for this card, and do **not** hardcode the printed barcode / Roll No / User ID / Branch Term as credentials. Keep the full MIFARE Classic dump + Gen2/CUID magic-card path. For 100% confirmation, ask the centre office for the reader model, or observe the reader: a barcode scanner normally has a red laser / CCD window and you show the printed side; an RFID reader is a flat pad you tap. Hybrid readers can support both, but your test still proves NFC works here.

---

## 3. What v3.1.2 adds over v3.1

1. **Correct MIFARE Classic classification.**
   - `MifareClassic` tech wins over `NfcA`; Type 2 / NTAG diagnostics are skipped for this card.
   - No more false `"Ultralight: NFC-A Type 2 (NfcA only)"` or misleading `"Type 2 diagnostics: transceive failed"` note.
   - Compatibility text says **NOT EMULATABLE by Android HCE** and explains Crypto1 + fixed UID.
2. **Full dump + key capture.**
   - Tries common factory/transport keys on **Key A and Key B for all 16 sectors**.
   - Reads all 64 blocks (1024 bytes), patches the discovered keys back into each sector trailer, and stores a `sector:type:key` map.
   - Saves block 0, validates its **BCC** before any magic-card write, and records whether the dump is complete. If a sector uses a custom key, the app says so and tells you to recover it with MIFARE Classic Tool / Proxmark.
3. **Real Gen2/CUID magic-card writer** (`MifareMagicActivity`).
   - Writes the exact full dump to a blank **Gen2 / CUID / DirectWrite UID-changeable MIFARE Classic 1K** card: block 0 last, sector trailers last per sector.
   - Uses `MifareClassic.authenticateSectorWithKeyA/B()` + `writeBlock()`; Gen2 cards accept a normal block-0 write from Android [2](https://lab401.com/blogs/academy/know-your-magic-cards) [10](https://lab401.com/blogs/academy/know-your-magic-cards).
   - Gen1a cards need 7-bit backdoor commands (`0x40`/`0x43`) that stock Android cannot send; use Proxmark / ChameleonUltra for those [1](https://www.blackhillsinfosec.com/rfid-proximity-cloning-attacks/) [4](https://tagbase.ksec.co.uk/resources/magic-uid/).
   - Re-tap verification compares the **new UID** and all data/access bytes; Key A is masked on read, so trailer key bytes are not falsely compared.
   - `COPY FULL DUMP + KEY MAP` and `SHARE DUMP TEXT` export a MCT/Proxmark-friendly text report.
4. **HCE is diagnostics only.** The app does not auto-arm HCE for MIFARE Classic. The Quick Settings tile, status line and logs say `HCE DIAGNOSTICS`, not "punch". A MIFARE Classic reader will never reach `HostApduService`, so an empty APDU log is expected.
5. **Printed barcode is not this card's credential.** The printed barcode / Roll No / User ID / Branch Term are not hardcoded and no camera barcode scanner path is built. Official Aakash pages show the institute can ingest barcode **or** RFID **or** biometric machines (§2.1), but your own NFC punch test makes the embedded MIFARE Classic chip the active path here.
6. **No guessed branch credential.** Saharanpur address/phone and `CC-028` appear only as context. The exact credential comes from the physical card's UID, block 0 and sector data.
7. **Unit tests.** JUnit tests cover NDEF parsing, APDU wildcard rules, hex/status words and MIFARE block-0 BCC validation.
8. **Deep official-source research (§2.1).** README and first-run help now state that Aakash supports multiple attendance capture types, while no public source names the Saharanpur reader model. The app notes that this is institution-level capability, not proof of your branch's protocol.
9. **Screenshot label cleanups.** `MIFARE: MIFARE Classic 1K` now uses `MifareClassic.getSize()` instead of the misleading generic `MIFARE Classic 1K/4K`; the old "exact AAKASH branch identifier must be read from this card" note is replaced with a truthful "no NDEF / PSID / branch identifier in this dump" note; a full dump with all `FFFFFFFFFFFF` keys now says so explicitly.
10. **Nuanced dialogs.** First-run help and the registration dialog mention institute-wide Barcode/RFID/biometric support but keep your card's NFC-confirmed path. `MIFARE_VERIFY_OK` still means dump match only, **not** an attendance punch.
11. **v3.1.2 leftover cleanup.** The v3.1.1 registration screenshots still showed `EXPECTED NDEF BRANCH PREFIX (OPTIONAL) = AAKASH` at the bottom, even though the same screen said `Aakash identifier: not found` and `No NDEF text / AAKASH / PSID credential was found`. That field is now **hidden for MIFARE Classic**, the guessed `AAKASH` default is removed, old saved profiles are sanitised on load, and `STUDENT ID / ROLL NO` is explicitly marked **label only**. The Type-2/NTAG writer also refuses to fabricate `AAKASH`, PSID or student text when no exact NDEF bytes exist. `NfcUtils.findAakashIdentifier()` is documented as reporting literal captured text only, never inventing a credential.

The single card profile is encrypted locally with AES-256-GCM using an Android Keystore key. Nothing is uploaded. The older Type-2/NTAG sticker writer is retained for cards that really are Type 2, but is hidden for this MIFARE Classic card.

---

## 4. Install / use

APK: [release `v3.1.2-mifare-saharanpur` → `app-release.apk`](https://github.com/pavit12301611/psd-nfc-app/releases/download/v3.1.2-mifare-saharanpur/app-release.apk)

1. Open **Aakash Saharanpur ID** → **REGISTER / REPLACE CARD** → hold the physical Aakash ID card to the back of the phone.
2. Review: `MIFARE Classic 1K / Crypto1`, 16/16 sectors dump, key entries, block 0, SAK/ATQA.
3. Tap **WRITE / EXPORT MIFARE MAGIC CARD DUMP**.
4. If the dump is complete, hold a blank **Gen2/CUID MIFARE Classic 1K, 4-byte UID** magic card to the phone. Keep it still until `MIFARE_WRITE_OK`.
5. Remove and re-tap it; wait for `MIFARE_VERIFY_OK` (UID + dump match).
6. Attach the written magic card to the phone/card and test at the institute reader.
7. If the reader rejects it:
   - It may detect/reject "Chinese magic" cards [1](https://www.blackhillsinfosec.com/rfid-proximity-cloning-attacks/).
   - It may require custom sector keys not found by the app; recover them with MIFARE Classic Tool / Proxmark [3](https://shop.mtoolstec.com/mifare-classic-tool-tutorial) [8](https://www.gavinjl.me/proxmark-3-cloning-a-mifare-classic-1k/).
   - Use the physical card, ask the institute to enroll the clone UID, or use an authorised external emulator such as ChameleonUltra / Proxmark / Flipper [6](https://github.com/RfidResearchGroup/ChameleonUltra/blob/main/CHANGELOG.md).
8. **ARM FOR DIAGNOSTICS** is only for an APDU log. It will not make the MIFARE reader accept the phone.

Example exported key line:

```text
0:A:FFFFFFFFFFFF
0:B:FFFFFFFFFFFF
...
15:A:FFFFFFFFFFFF
15:B:FFFFFFFFFFFF
```

---

## 5. Hard limits — read before debugging

- **Stock Android HCE:** cannot emulate MIFARE Classic, set a fixed UID/ATQA/SAK, or perform Crypto1 authentication [1](https://stackoverflow.com/questions/48007948/how-to-simulate-mifare-classic-1k-card-with-android) [10](https://stackoverflow.com/questions/65833264/sending-data-using-hce-or-using-secure-element-android-kotlin-mifare-1k).
- **Normal blank MIFARE Classic card:** block 0 UID is read-only forever; it will not clone the UID [4](https://blog.linux-ng.de/2026/10/16/proxmark3-mifare-classic-bring-up/) [7](https://www.blackhillsinfosec.com/rfid-proximity-cloning-attacks/).
- **Gen1a magic card:** needs 7-bit `0x40`/`0x43` backdoor commands; stock Android cannot send them. Use Proxmark / libnfc / ChameleonUltra [1](https://www.blackhillsinfosec.com/rfid-proximity-cloning-attacks/) [4](https://tagbase.ksec.co.uk/resources/magic-uid/).
- **Gen2/CUID magic card:** Android-compatible direct block-0 write, but some readers detect it by probing block 0 [2](https://lab401.com/blogs/academy/know-your-magic-cards) [10](https://lab401.com/blogs/academy/know-your-magic-cards).
- **Custom keys / access bits:** if any sector is not readable with common keys, the dump is incomplete and the writer is disabled. Bad access bits can irreversibly block a sector [1](https://github.com/XaviTorello/mifare-classic-toolkit/blob/master/README.md).
- **Reader behaviour unknown:** it may check only the UID, one authenticated sector, a value block, a backend database, or the original card's silicon behaviour. Without a reader trace / full key recovery, "perfect" cannot be promised.
- **Institute policy:** the app cannot add a clone UID to the institute's allowlist. Enrollment may require Aakash admin.
- **"Perfect" compatibility:** cannot be promised without the actual reader model and a full card/reader trace. Green CI, `MIFARE_VERIFY_OK` and `9000` APDUs are **not** proof of an attendance punch.
- **No real punch tested:** no institute reader was available in this workspace.

---

## 6. Build / test

Pushing to `arena/87e17c3d-psd-nfc-app` runs `.github/workflows/build-apk.yml`:

- JDK 17 + Android SDK 34 + Gradle 8.7,
- `./gradlew --no-daemon --stacktrace test assembleRelease`,
- signed APK uploaded as artifact `aakash-mifare-saharanpur-apk`,
- attached to release `v3.1.2-mifare-saharanpur`.

Local:

```bash
./gradlew test assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

`minSdk 24`, `targetSdk 34`, Java 17, Android framework APIs plus JUnit 4 for local unit tests only.

---

## 7. Sources

1. [Android HCE overview](https://developer.android.com/develop/connectivity/nfc/hce)
2. [AOSP HCE documentation](https://android.googlesource.com/platform/frameworks/base/+/c988ee/docs/html/guide/topics/connectivity/nfc/hce.jd)
3. [MIFARE Classic cannot be emulated by Android HCE](https://stackoverflow.com/questions/48007948/how-to-simulate-mifare-classic-1k-card-with-android)
4. [HCE only emulates Type 4A; not MIFARE Classic](https://stackoverflow.com/questions/76850329/mifare-classic-emulation-not-working-i-havent-found-a-complete-code-or-step-b/76850393#76850393)
5. [MIFARE Classic 1K / HCE / secure element discussion](https://stackoverflow.com/questions/65833264/sending-data-using-hce-or-using-secure-element-android-kotlin-mifare-1k)
6. [MIFARE Classic 1K memory layout / keys / sector trailer](https://github.com/XaviTorello/mifare-classic-toolkit/blob/master/README.md)
7. [MIFARE Classic 1K: 16 sectors, 64 blocks, sector trailer](https://deepwiki.com/miguelbalboa/rfid/6.1-mifare-classic-cards)
8. [SAK 08 / block 0 locked / magic vs normal card](https://blog.linux-ng.de/2026/10/16/proxmark3-mifare-classic-bring-up/)
9. [SAK / block 0 UID+BCC notes](https://stackoverflow.com/questions/53731550/what-determines-the-sak-of-a-mifare-device)
10. [Magic MIFARE cards: Gen1a vs Gen2/CUID, Android compatibility](https://lab401.com/blogs/academy/know-your-magic-cards)
11. [Gen1a unlock `0x43`/`0x40`, MCT / Proxmark cloning](https://www.blackhillsinfosec.com/rfid-proximity-cloning-attacks/)
12. [Magic UID card generations](https://tagbase.ksec.co.uk/resources/magic-uid/)
13. [MIFARE Classic Tool write-dump tutorial](https://shop.mtoolstec.com/mifare-classic-tool-tutorial)
14. [Proxmark3 MIFARE Classic key recovery / dump](https://www.gavinjl.me/proxmark-3-cloning-a-mifare-classic-1k/)
15. [ChameleonUltra MIFARE / NTAG emulation changelog](https://github.com/RfidResearchGroup/ChameleonUltra/blob/main/CHANGELOG.md)
16. [Official Aakash Saharanpur / Court Road centre](https://centers.aakash.ac.in/aakash-institute-court-road-coaching-center-court-road-saharanpur-99263/Home)
17. [Aakash Saharanpur city page](https://www.aakash.ac.in/saharanpur-city)
18. [AIATS centre list PDF (`CC-028`)](https://dcx0p3on5z8dw.cloudfront.net/Aakash/s3fs-public/pdf_management_files/sm_sa/AIATS%20Center.pdf)
19. [myAakash login / 11-digit PSID](https://www.aakash.ac.in/how-to-login-download-myaakash-app)
20. [Aakash admission form: entry only with ID card](https://www.aakash.ac.in/sites/default/files/medical_acst_admission-form.pdf)
21. [Aakash terms: entry to classes only with Identity Card](https://dcx0p3on5z8dw.cloudfront.net/Aakash/s3fs-public/pdf_management_files/target_solutions/Updatd_Terms_and_Conditions_03_07_2023.pdf)
22. [Public report: barcode attendance cards](https://www.quora.com/How-do-ALLEN-or-Aakash-track-attendance-of-students)
23. [Public report: ID-card scan / parent SMS](https://www.quora.com/Do-parents-get-notifications-when-students-are-absent-in-Aakash)
24. [Android Ndef API / Type 1-4](https://docs.kony.com/NativeAPIs/Android/android.nfc.tech-Android-10.0/source/Ndef.html)
25. [NFC Forum tag specifications](https://nfc-forum.org/build/specifications)
26. [Official Aakash "Why Aakash": Daily Attendance Monitoring / Barcode / RFID / biometric machines](https://www.aakash.ac.in/why-aakash)
27. [Official DLP "Why Aakash": Daily Attendance Monitoring / Bar Code / RFID / biometric machines](https://dlp.aakash.ac.in/dlp-why-aakash)
28. [Official Aakash benefits: Attendance Alert through App notifications](https://www.aakash.ac.in/benefits-aakash-students)
29. [Official myAakash Google Play listing: attendance tracking + QR codes for study-module videos](https://play.google.com/store/apps/details?id=com.aakash.myaakashapp)
30. [Official Aakash 2025 prospectus: attendance alerts / phygital QR study material](https://d28cs5wqm3s0uy.cloudfront.net/AAKASH_V2/BrochureSection/pdf/Aakash_Medical_Engineering_Foundation_Prospectus_English_2025_Updated_2_1_1_1766082328507.pdf)
