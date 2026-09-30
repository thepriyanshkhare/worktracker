# Project Septuary — Supervisor App

A small, read-only companion app for Priyansh's parents. One screen: **Medicine / Food
/ Exercise**, each showing **Done / Pending / Not Done** for today, updating live.

## What it does and doesn't do

- Reads exactly one Firestore document (`septuary/status`) via a realtime listener.
- Never writes anything, ever.
- Never sees medication names, doses, or any actual health values (weight, glucose,
  sleep, notes) — only the three status words, pushed by the main Septuary app. See
  `../project-septuary-android/app/src/main/java/com/septuary/app/data/SyncRepository.kt`
  for exactly what gets computed and sent.
- No login, no PIN — it's meant to sit on a parent's home screen and just be glanced at.

## Setup

This app shares a Firebase project with the main Septuary app — see the main app's
README, section 7 ("Cloud sync"), for the full setup chain (create the Firebase
project, register both apps, swap in real `google-services.json` files, deploy
`../firestore.rules`). Until that's done, this app builds and runs fine but shows
"Waiting for his first update" forever — nothing is broken, there's just nothing to
show yet.

## Build

Same CI as the main app — see `../.github/workflows/build.yml`, which now builds both
apps on every push and attaches both APKs to one GitHub Release.
