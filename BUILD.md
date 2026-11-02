# How to build the APK (for Samsung Galaxy A53 5G)

This is a native Android (Java) project. It cannot be compiled in this sandbox (no Java/Android SDK here), but it builds fine on any PC with Android Studio.

## Prerequisites
- Android Studio (latest stable) — https://developer.android.com/studio
- A Samsung Galaxy A53 5G (has NFC, Android 13/14)

## Steps

1. **Open the project**
   - Android Studio → `File > Open` → select the `psd-nfc-app` folder.

2. **Sync Gradle**
   - Let Android Studio download the Gradle wrapper and dependencies (internet needed once).
   - `compileSdk 34`, `minSdk 21`, `targetSdk 34`.

3. **Build the APK**
   - `Build > Build Bundle(s) / APK(s) > Build APK(s)`
   - Choose **debug** variant for testing.
   - Output: `app/build/outputs/apk/debug/app-debug.apk`

4. **Install on the A53 5G**
   - Enable `Developer options > USB debugging` on the phone.
   - Connect via USB and run:
     ```
     adb install app/build/outputs/apk/debug/app-debug.apk
     ```
   - Or transfer the APK to the phone and install directly (allow "Install unknown apps" when prompted).

5. **For a Play Store release**
   - Create a signing keystore: `keytool -genkeypair -alias release -keyalg RSA -keysize 2048 -validity 10000 -keystore release.jks`
   - Add a `signingConfigs.release` block in `app/build.gradle` and set `minifyEnabled true` with your proguard rules.
   - `Build > Generate Signed Bundle / APK`.

## Runtime setup on the phone
1. Open the app → set a **PIN** (4–6 digits).
2. Enter your website link and a **shared secret key** (same key on the receiver phone).
3. Tap **"Whitelist mein daalein aur NFC start karein"**.
4. Keep **NFC ON** (quick settings) and add this app to **Never sleeping apps** (`Settings > Device care > Battery`) so it stays active in the background.
5. On the receiver phone, install the same app, enter the same secret key, turn NFC on, and tap the phones back-to-back. The encrypted link is decrypted only with the matching key.
