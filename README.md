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
- **Two ways to add an NFC:**
  1. ✏️ **Type link / text** — links are auto-saved as secure `https://`.
  2. 📲 **Scan & duplicate** — hold an ID card / NFC tag to the back of the phone; the app
     reads the raw NDEF signal (and MIFARE block 0–3 with default keys, best-effort) plus
     the tag UID, saves it, and **duplicates** that exact signal to other phones.
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

### Troubleshooting phone-to-phone
- **Both phones: NFC ON + screen ON.** Android OS requires the screen to be on for NFC
  transmission/reception — this is an OS security rule that **no app can bypass**
  (not a limit of this app; there are no other limits — any NFC phone can receive).
- Hold the phones steady at the antenna spot for 2–3 seconds; try slight repositioning.
- On the sending phone keep a tile armed (green banner). The app also pushes via
  Android Beam on Samsung in addition to HCE.
- Receiving phone: if a chooser appears, pick Browser; if PSD NFC is installed there,
  it shows a "📲 NFC link received" dialog with an **Open link** button.
- Physical tags: while the Transmit screen is open, tap a blank tag to write the payload.

> Note: `requireDeviceUnlock=false` is set, so transmission also works from the lock
> screen on most devices (screen must still be on, not off).

## Build from source (on GitHub)
Push to `arena/e25bd87f-psd-nfc-app` or run the **Build PSD NFC APK** workflow manually —
it compiles `app-release.apk` with the Android SDK and attaches it to the `v1.0.0` release.

- `minSdk 24` / `targetSdk 34` — covers Samsung Galaxy A53 5G (Android 12+) and virtually
  every NFC phone.
- Pure framework code (no third-party libraries), signed release APK.
