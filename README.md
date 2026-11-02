# PSD NFC Link Transfer

Bhai, ye app aapke **website link ko NFC ke through** doosre phone tak pahunchati hai — **fast aur secure**. Khaas **Samsung Galaxy A53 5G** ke liye banaya gaya hai (isme NFC support hai, Android 13/14).

## Kya karta hai ye app

1. **NFC se link bhejna** — apna website link enter karein, NFC on rakhein, aur doosre phone se peechhe se peechhe (back-to-back) tap karein.
2. **Secure (kisi bhi phone par nahi khulega)** — link **AES-256-GCM se encrypt** hota hai. Ciphertext NFC ke through jaata hai. Bina shared secret key ke, koi bhi random phone wo link decode nahi kar paayega.
3. **PIN lock** — app kholne ke liye PIN chahiye (4-6 ank).
4. **Whitelist** — sirf wohi links bhej paayenge jise aapne whitelist mein add kiya hai.
5. **Hamesha active** — foreground service ke through NFC background mein chala rahta hai, aur phone reboot hone par bhi auto-start hota hai (BootReceiver).

## Build kaise karein (APK banane ke liye)

Ye project pure **Android (Java)** mein hai. Isse build karne ke liye aapke PC par **Android Studio** hona chahiye (is sandbox mein APK compile nahi ho sakta kyunki yahan Java/Android SDK nahi hai).

Mukhya steps:

1. **Android Studio install karein** (developer.android.com se).
2. **Ye project open karein:** `File > Open` → `/path/to/psd-nfc-app` select karein.
3. **Gradle Sync karein** (pehli baar internet chahiye taaki Gradle aur dependencies download ho sakein).
4. **APK banayein:** `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
5. APK yahan milegi: `app/build/outputs/apk/debug/app-debug.apk`.
6. **Samsung Galaxy A53 5G par install karein:**
   - Phone mein **Developer options > USB debugging ON** karein.
   - USB se connect karein aur chalaayein: `adb install app/build/outputs/apk/debug/app-debug.apk`
   - Ya APK file phone mein bhejkar directly install karein (Install unknown apps ki permission deni pad sakti hai).

## Setup aur istemal

1. App open karein → **PIN set karein**.
2. **Website link** daalein (https://...).
3. **Secret key** daalein — yehi key doosre phone (receiver) mein bhi daalni hogi. Key kisi se share na karein.
4. **"Whitelist mein daalein aur NFC start karein"** dabayein.
5. App background mein active rahegi aur NFC on rahegi (persistent notification dikhegi).
6. **Receiver phone** par bhi yehi app install karein, wohi secret key daalein, NFC ON rakhein.
7. Dono phone ke peechhe ke hisse ko ek doosre se lagayein (back-to-back tap). Receiver phone par app khulegi, link decrypt hoga, aur **"Browser mein kholein"** se website open ho jayegi.

### "Hamesha active" ke liye zaroori setting (Samsung One UI)

Samsung phones background apps ko kill kar dete hain. Isliye:
- `Settings > Device care > Battery > Background usage limits > Never sleeping apps` mein ye app add karein.
- NFC quick-settings toggle se **ON** rakhein (app ko NFC on karne ki koshish karti hai, lekin system toggle user control mein hota hai).

## Security notes

- Link encrypt hota hai taba hi NFC tag (HCE) ke through broadcast hota hai.
- Bina app aur bina secret key ke, koi anya phone tap karke kuch nahi dekh payega.
- PIN lock se app miss-use nahi ho payegi.
- Whitelist se galti se galat link bhejna rok paayenge.

> Disclaimer: NFC range bahut kam hoti hai (~4 cm). Ye app authorized devices ke liye hai. Secret key dono devices par samaan honi chahiye; key agar leak ho jaye to koi bhi usse decrypt kar sakta hai.
