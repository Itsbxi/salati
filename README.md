# صلاتي · Salati

Prayer-times app for Android (Kotlin, Material 3) in **Arabic and English**.

## What it does
- **First launch:** asks for your country and city. Times come from the free
  [AlAdhan](https://aladhan.com/prayer-times-api) API, are saved on the phone (so it also works
  offline) and are refreshed every day.
- **At every prayer time:** plays the adhan and shows a notification with **I prayed** / **I will pray**.
- **Keeps reminding you** until the next prayer starts, and **more often the closer it gets**:
  the gap is a quarter of the time left (never under 2 min, never over 30 min).
  Example: with 80 min left the gaps go 20, 15, 11, 8, 6, 5, 3 … down to 2 minutes.
- **I prayed** stops every reminder for that prayer. **I will pray** silences the current call,
  but the reminders carry on.
- Fajr reminders stop at sunrise, Isha reminders at Islamic midnight.
- Settings: language (System / English / العربية), change city, calculation method, Standard or
  Hanafi Asr, reminders on/off per prayer, adhan sound, and a **Test the prayer call** button.

## Get the APK with GitHub (no Android Studio needed)
1. Create a new GitHub repository and upload **everything** in this folder
   (keep the hidden `.github` folder, it contains the build recipe).
2. Open the **Actions** tab. **Build APK** starts by itself on every push
   (or press *Run workflow*).
3. When it turns green, open the run, scroll to **Artifacts**, download **salati-apk**,
   unzip it and install `salati.apk` (allow "install unknown apps" if asked).

Push a tag such as `v1.0.0` and the APK is also attached to a GitHub Release.

## The adhan voice
No recording is included. Either:
- in the app: **Settings → Adhan sound → Choose audio file**, or
- before building: create `app/src/main/res/raw/` and put an `adhan.mp3` in it
  (lower-case name). A short `reminder.mp3` there is used for the follow-up reminders.

Without a file the phone's default alarm sound is used.

## Make the calls reliable
Phones kill background apps to save battery. After installing:
1. Allow notifications.
2. Allow alarms & reminders (Android 12) when the app asks.
3. Allow background activity / disable battery optimisation for Salati
   (the app shows a button for this). On Oppo, Realme, Xiaomi, Vivo, Huawei and similar
   phones also switch on **Auto-start** for Salati in the phone's app settings.
4. Use **Settings → Test the prayer call now** to check sound and buttons.

## Open in Android Studio
**File → Open** and pick this folder. Gradle 8.9 and JDK 17 are used
(`gradle/wrapper/gradle-wrapper.properties`). To build from a terminal: `gradle assembleDebug`.

## How it is organised
- `data/` prayer times: API, cache on the phone, settings
- `alarm/` scheduling (one exact alarm for the next call/reminder), notification, adhan service
- `work/` daily refresh (WorkManager)
- `res/values` English strings, `res/values-ar` Arabic strings
