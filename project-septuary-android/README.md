# Project Septuary — Native Android App

A private, fully offline medication/health tracker. Kotlin + Jetpack Compose,
encrypted local database, exact-alarm reminders.

## Read this first: what's untested

**This project was written without access to the Android SDK, an emulator, or a Kotlin
compiler.** Every file was reviewed carefully by hand for correctness (imports, API
signatures, version compatibility), and a version-mismatch bug was already caught and
fixed this way (the Compose compiler plugin only works with Kotlin 2.0+, which is why
the project pins Kotlin 2.0.21). But "reviewed by hand" is not "compiled and run" —
expect a handful of small issues on first build, most likely:

- A dependency version that doesn't resolve exactly as pinned (KSP's version string is
  tied tightly to the Kotlin version — if Gradle can't find `2.0.21-1.0.27`, check
  https://github.com/google/ksp/releases for the exact string matching Kotlin 2.0.21).
- Material3 `OutlinedTextFieldDefaults.colors()` parameter names shifting slightly
  between Compose versions (`ui/screens/LogScreen.kt`, `fieldColors()`).
- The Gradle wrapper jar is **not included** (couldn't download it in this environment) —
  see step 1 below.

Paste me the exact Gradle error if something doesn't build — these are normally
one-line fixes.

## 1. First build steps

1. Open the `SeptuaryNative/` folder in Android Studio (Koala or newer recommended).
2. Android Studio will detect the missing `gradle-wrapper.jar` and offer to regenerate
   it automatically. If it doesn't: **File → Sync Project with Gradle Files**, or run
   `gradle wrapper --gradle-version 8.7` from a terminal with Gradle installed (Android
   Studio bundles one under its own installation).
3. Let Gradle sync. Fix any version-resolution errors per the notes above.
4. Connect your Android phone via USB with Developer Options + USB debugging on, or use
   an emulator, and hit Run.
5. To install permanently without USB: **Build → Generate Signed Bundle / APK → APK**,
   copy the resulting `.apk` to your phone, and install it (you'll need to allow
   "install from unknown sources" for the file manager you use — standard for sideloading).

## 2. What "completely offline" means here, concretely

- **No `INTERNET` permission is requested anywhere in `AndroidManifest.xml`.** This isn't
  just "the code doesn't make network calls" — without that permission, Android's own
  sandboxing blocks the app from reaching the network at the OS level. It's structurally
  incapable of a data leak over the network, not just well-behaved.
- **`android:allowBackup="false"`** — Android's own Auto Backup to Google Drive is
  disabled, so the OS can't copy your (encrypted) database off the device either.
- **Encrypted at rest**: SQLCipher-backed Room database. The encryption key is derived
  from your PIN via PBKDF2 (150,000 rounds, SHA-256) — the PIN itself is never stored,
  anywhere, in any form.

## 3. The one deliberate trade-off — read this

Reminders need to survive a phone reboot without you having to reopen and unlock the
app first (`AlarmManager` alarms are wiped on every reboot). But a boot-time receiver
has no PIN to decrypt your database with — it isn't you typing the PIN in, it's the OS
starting a background component.

So: **medication names and times (not your weight/glucose logs, not your dose-taken
history) are kept in a small *unencrypted* cache** — see `alarm/ScheduleCache.kt`. This
is the one place plaintext exists on disk. Threat model: someone with your unlocked
phone could see "Roseday-F 10, 9pm" in that cache file without needing your PIN. Your
actual health data — logs, readings, dose history — stays encrypted regardless.

If you'd rather have zero plaintext ever, at the cost of reminders not surviving a
reboot until you next open and unlock the app: delete the `ScheduleCache.write(...)`
call in `Repository.seedIfEmpty()` and have `AlarmScheduler` read directly from the
(already-decrypted, in-memory) medication list instead of the cache. I can make that
change for you if you'd rather have that trade-off.

## 4. Reminder reliability — the honest ceiling

This app requests:
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` — for millisecond-accurate wake-ups via
  `AlarmManager.setExactAndAllowWhileIdle`, which bypasses Doze.
- Battery-optimization exemption (via a Settings deep link in the app's Settings tab) —
  recommend enabling this for real reliability.

With both granted, this is about as close to "guaranteed" as a non-system app gets on
stock Android. Two remaining gaps, stated plainly:
1. Extreme low-power/battery-saver modes can still defer alarms system-wide.
2. **OEM battery managers** (Xiaomi/MIUI, Oppo/ColorOS, Vivo, OnePlus/OxygenOS, Huawei)
   layer their own aggressive app-killers on top of stock Android, outside what any
   app's manifest permissions can control. If you're on one of these, also check that
   manufacturer's own "autostart" / "protected apps" / "battery saver exceptions" list
   and add Project Septuary manually. The Medicine tab shows a banner until reminders are fully enabled.

## 5. Project layout

```
app/src/main/java/com/septuary/app/
  MainActivity.kt          — entry, unlock (PIN / fingerprint), auto-lock, three-tab navigation
  Session.kt               — process-wide unlocked session (survives rotation)
  data/                    — Room entities, DAOs, encrypted AppDatabase, Repository, seed data,
                             FamilyLink (private pairing code), PendingActions/DoneMirror, sync
  crypto/                  — PIN -> key (PBKDF2 + throttle), BiometricVault, LocalVault (Keystore)
  alarm/                   — exact alarms, Taken/Snooze actions, boot/time-change re-arm, channels
  ui/screens/              — Medicine + Food (DoseScreen), Exercise, Lock, Family sharing
  ui/theme/                — dark theme
```

## 6. Schedule (seed) data

`data/SeedData.kt` holds the medicine and food schedule. It is written on first unlock and
re-applied to an existing install whenever `SeedData.VERSION` is bumped — times you changed
in-app are kept, removed items are deactivated, logged history is never touched.

## 7. Family sync

Only today's Medicine / Food / Exercise status and the item list go to Firestore, under a
path derived from a **private 20-character family code** generated on the phone (menu →
Family sharing). The code is not in this repository or in either APK; parents enter it once
in the Supervisor app. Deploy `../firestore.rules` (Firebase Console → Firestore → Rules) and
enable **Anonymous** sign-in (Authentication → Sign-in method).

The chat-logging script needs the same code: `SEPTUARY_FAMILY_CODE=XXXX-... python3
../scripts/push_to_septuary.py ...`. Never commit the service-account key.

## 8. Signing (required for updates to install)

CI signs both APKs with a stable key from two repository secrets:
`SEPTUARY_KEYSTORE_B64` (base64 of the keystore) and `SEPTUARY_KEYSTORE_PASSWORD`. Without
them each build gets a throwaway key and Android refuses to install it over the previous one.
