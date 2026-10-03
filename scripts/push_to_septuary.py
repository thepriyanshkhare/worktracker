#!/usr/bin/env python3
"""
Pushes one log entry into the Septuary app's Firestore inbox — this is how the Claude
chat session ("update the app with X") gets something into Priyansh's phone without
ever touching his local encrypted database directly.

Flow: this script writes a document to the inbox collection (see INBOX_COLLECTION below —
an unguessable token-based name, not a plain word, as of the Oct 2026 security hardening)
using an admin service-account credential, which bypasses Firestore security rules entirely
by design (see /firestore.rules). The Septuary app itself drains that collection into its
local encrypted DB every time Priyansh unlocks it, then deletes each consumed document.

Requires:
    pip install --break-system-packages google-auth requests

Needs one file that must NEVER be committed to git: a Firebase service-account key
JSON, downloaded from Firebase Console -> Project Settings -> Service Accounts ->
Generate new private key. Pass its path via --key or the SEPTUARY_SERVICE_ACCOUNT
env var.

Usage examples:
    python3 push_to_septuary.py weight --kg 94.5
    python3 push_to_septuary.py glucose --value 165 --glucose-type random
    python3 push_to_septuary.py exercise --exercise-type Walking --minutes 30 --note "evening walk"
    python3 push_to_septuary.py sleep --bed-time 23:30 --wake-time 06:45 --quality 3
    python3 push_to_septuary.py note --text "Sep 30 urine test: glucose detected, specific gravity high"

All commands accept --date YYYY-MM-DD to backdate an entry (defaults to today on the
phone at import time, not the date this script runs).
"""
import argparse
import json
import os
import sys
import time

try:
    import google.auth.transport.requests
    from google.oauth2 import service_account
except ImportError:
    print("Missing dependency. Run: pip install --break-system-packages google-auth requests", file=sys.stderr)
    sys.exit(1)
import requests

SCOPES = ["https://www.googleapis.com/auth/datastore"]
# Oct 2026 security hardening: must match SyncRepository.ROOT_COLLECTION in the Android app,
# the Supervisor app's ROOT_COLLECTION, and firestore.rules exactly. Admin-credential calls
# (this script) bypass security rules entirely, but the collection name still has to match
# what the app actually drains on unlock.
INBOX_COLLECTION = "septuary_86800832c1af658f9d30a04276952c81_inbox"


def get_access_token(key_path: str) -> tuple[str, str]:
    creds = service_account.Credentials.from_service_account_file(key_path, scopes=SCOPES)
    creds.refresh(google.auth.transport.requests.Request())
    with open(key_path) as f:
        project_id = json.load(f)["project_id"]
    return creds.token, project_id


def to_firestore_value(v):
    if isinstance(v, bool):
        return {"booleanValue": v}
    if isinstance(v, int):
        return {"integerValue": str(v)}
    if isinstance(v, float):
        return {"doubleValue": v}
    return {"stringValue": str(v)}


def push_entry(key_path: str, fields: dict):
    token, project_id = get_access_token(key_path)
    url = f"https://firestore.googleapis.com/v1/projects/{project_id}/databases/(default)/documents/{INBOX_COLLECTION}"
    body = {"fields": {k: to_firestore_value(v) for k, v in fields.items() if v is not None}}
    resp = requests.post(url, headers={"Authorization": f"Bearer {token}"}, json=body, timeout=15)
    resp.raise_for_status()
    return resp.json()


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--key", default=os.environ.get("SEPTUARY_SERVICE_ACCOUNT"), help="Path to the Firebase service-account JSON")
    sub = p.add_subparsers(dest="type", required=True)

    common = dict(type=str)

    w = sub.add_parser("weight")
    w.add_argument("--kg", type=float, required=True)
    w.add_argument("--note", default="")
    w.add_argument("--date")

    g = sub.add_parser("glucose")
    g.add_argument("--value", type=int, required=True)
    g.add_argument("--glucose-type", default="random")
    g.add_argument("--date")

    e = sub.add_parser("exercise")
    e.add_argument("--exercise-type", required=True, choices=["Swimming", "Cycling", "Walking", "Yoga"])
    e.add_argument("--minutes", type=int, required=True)
    e.add_argument("--note", default="")
    e.add_argument("--date")

    s = sub.add_parser("sleep")
    s.add_argument("--bed-time", required=True, help="HH:MM 24h")
    s.add_argument("--wake-time", required=True, help="HH:MM 24h")
    s.add_argument("--quality", type=int, choices=[1, 2, 3, 4, 5], default=3)
    s.add_argument("--note", default="")

    n = sub.add_parser("note")
    n.add_argument("--text", required=True)

    args = p.parse_args()
    if not args.key:
        print("No service-account key given. Pass --key or set SEPTUARY_SERVICE_ACCOUNT.", file=sys.stderr)
        sys.exit(1)

    fields = {"type": args.type, "createdAt": int(time.time())}
    if args.type == "weight":
        fields.update(kg=args.kg, note=args.note, date=args.date)
    elif args.type == "glucose":
        fields.update(value=args.value, glucoseType=args.glucose_type, date=args.date)
    elif args.type == "exercise":
        fields.update(exerciseType=args.exercise_type, minutes=args.minutes, note=args.note, date=args.date)
    elif args.type == "sleep":
        fields.update(bedTime=args.bed_time, wakeTime=args.wake_time, quality=args.quality, note=args.note)
    elif args.type == "note":
        fields.update(text=args.text)

    result = push_entry(args.key, fields)
    print("Pushed:", json.dumps(result.get("name", result), indent=2))


if __name__ == "__main__":
    main()
