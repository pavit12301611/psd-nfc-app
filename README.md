# PSD NFC — fast & secure NFC link transmitter

Android app that transmits website links / text / scanned-card signals over NFC to **any
NFC phone** (no app needed on the receiving phone — Android reads the NDEF link natively
and opens it in the browser).

## 📥 Direct APK download

👉 **https://github.com/pavit12301611/psd-nfc-app/releases/download/v1.0.0/app-release.apk**

(Raw copy also committed in this repo at `dist/app-release.apk` on branch `arena/e25bd87f-psd-nfc-app`.)

Built automatically on GitHub (see `.github/workflows/build-apk.yml`) and published as a
GitHub Release asset on every push to `arena/e25bd87f-psd-nfc-app`.

### Install on Samsung Galaxy A53 5G (or any Android phone with NFC)
1. Download the APK above on the phone.
2. Tap it → if asked, allow **"Install unknown apps"** for your browser/file manager
   (Settings → Apps → Special app access → Install unknown apps).
3. Install & open **PSD NFC**.
4. Make sure **NFC is ON**: Settings → Connections → NFC and payment → ON.

## ✨ Features
- **Black, flat (non-gradient), clean UI** with a grid of tiles — one tile per saved NFC.
- **ID Card** — the only way to add an NFC. Hold the ID card / NFC tag to the back of the phone;
  the app reads the raw NDEF signal (and MIFARE block 0–3 with default keys, best-effort) plus the
  tag UID, and **duplicates** that signal to other phones. Then add:
  - a **card name** (e.g. "Office ID") — shown on the tile,
  - a **photo** (picked from your gallery, stored downscaled on the device),
  - any number of **custom fields** (label + value, e.g. "Blood group" = "B+"),
  - an optional **open link**.
  Long-press a tile → **Edit ID card** to change the name, photo, or fields later.
- **Tags** for every tile (e.g. `#site`, `#main`) — shown as colored chips, editable anytime.
- **Always active:** pick a tile once and it stays armed — a Quick-Settings tile
  (**NFC Transmit**) keeps transmission on with one tap (works even from the lock screen),
  and the app auto-launches when an NFC link is received.
- **Phone-to-phone:** the armed tile is emitted via Host Card Emulation (NDEF Type-4 tag),
  so the other phone just needs NFC on — tap backs of the phones together and the link opens.
- **Write to physical tags:** while the Transmit screen is open, tapping a blank NFC tag
  writes the payload onto it.
- **Secure:** all saved payloads are stored only on the device, encrypted with AES-256-GCM
  using a hardware-backed Android Keystore key. Nothing is uploaded anywhere.

## How to transmit
1. Open **PSD NFC** → tap a tile (it gets armed, green **● ARMED**). Or add the
   **NFC Transmit** Quick-Settings tile and keep it ON — then the app works even closed.
2. On the other phone: NFC ON, **screen ON** (lock screen is fine on most devices).
3. Touch the **backs** of both phones together — the NFC antenna is near the
   **upper-middle** of the back — and hold for 2–3 seconds.
4. The other phone opens the link in the browser (or shows a "NFC link received"
   dialog if it also has PSD NFC). No app is needed on the receiving phone.

### Fix: “No supported application to open the NFC signal” on the receiving phone
This message appears when the received NFC data has **no record that any app can open**
(e.g. a raw scanned card). Workaround built into v1.2:

- **Every transmission now starts with a URI (web link) record.** Any phone — no app
  needed, every phone has a browser — opens it automatically. No more "no supported
  application".
- An **Android App Record (AAR)** is appended last, so phones that DO have PSD NFC
  installed open the app directly and show the received link dialog.
- For **scanned ID cards / tags**, set the **“Link that opens on the other phone”**
  field (e.g. your business website/menu/payment page). If left empty, a Google search
  page of the tag ID opens instead. The raw scanned signal is still included in the
  same NFC message for phones running this app.

### Troubleshooting phone-to-phone ("No supported application for this NFC tag")
That message means the receiving phone did NOT recognise the signal as an NDEF link — it
saw a raw tag. Checklist (most common causes first):

1. **Hold for 3 full seconds** and align the **top-middle** of both phone backs (that is
   where the NFC antenna is on Galaxy A-series). Quick taps get missed.
2. **Both phones: NFC ON + screen ON** (lock screen OK). Screen off = OS-level block.
3. Sending phone must show the green **● ARMED & TRANSMITTING** banner (tap a tile first),
   and must NOT show the "⚠ NFC is OFF" warning.
4. Try a few times with slight repositioning — the first tap after screen-on sometimes wakes
   the NFC stack.

What the app does to maximise compatibility (v1.3): the phone emulates a standard
**Type 4 NDEF tag** (canonical 15-byte Capability Container, NDEF AID `D2760000850101`
+ Type 4 Tag AID `D2760000850100`, tolerant SELECT/READ handling), always puts a
**URI record first** (every phone has a browser → link opens, no app needed), appends an
**Android App Record** (phones with this app open it directly), and also pushes via
**Android Beam** on Samsung. Receiving phone: pick Browser in the chooser, or if PSD NFC
is installed there it shows the "📲 NFC link received" dialog.
- Physical tags: while the Transmit screen is open, tap a blank tag to write the payload.

> Note: `requireDeviceUnlock=false` is set, so transmission also works from the lock
> screen on most devices (screen must still be on, not off).

## Build from source (on GitHub)
Push to `arena/e25bd87f-psd-nfc-app` or run the **Build PSD NFC APK** workflow manually —
it compiles `app-release.apk` with the Android SDK and attaches it to the `v1.0.0` release.

- `minSdk 24` / `targetSdk 34` — covers Samsung Galaxy A53 5G (Android 12+) and virtually
  every NFC phone.
- Pure framework code (no third-party libraries), signed release APK.
