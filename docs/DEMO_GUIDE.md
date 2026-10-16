# SurfaceLab demo guide

## Preparation

Install all three APKs and open SurfaceLab once. The guide uses package `com.example.vulnerable` and serial `emulator-5554`; substitute your explicit emulator serial. The hardened comparison uses package `com.example.hardened` and scheme `safelab`.

The private Android component classes live under `com.example.surfacelab` in both flavors. Package and Java namespace are intentionally different. Use the exact component names below.

## 1. Deep-link authorization and WebView bridge

From PowerShell, quote the complete remote shell command so `&` stays inside the URI:

```powershell
adb -s emulator-5554 shell 'am start -W -a android.intent.action.VIEW -d "myapp://transfer?to=TrainingRecipient&amount=42" -p com.example.vulnerable'
```

Expected: a synthetic ledger row is inserted without sign-in, and the app displays `FLAG{deep_link_without_auth}`. This shows an authorization failure in the app; Android is correctly delivering a browsable link. `BROWSABLE` is an intent category, not a manifest attribute named `android:browsable`.

The main screen's **Open local WebView demo** button runs an offline proof that a page can invoke `LabSession.readSession()`. To demonstrate a different origin and actual collection on your computer:

```powershell
python scripts/collector.py
```

In a second terminal:

```powershell
adb -s emulator-5554 reverse tcp:8765 tcp:8765
adb -s emulator-5554 shell 'am start -W -a android.intent.action.VIEW -d "myapp://webview?url=http%3A%2F%2F127.0.0.1%3A8765%2F" -p com.example.vulnerable'
```

Expected: the page displays `LAB_SESSION_f3a9_synthetic_only`, and the local collector prints it. Reading the bridge records `FLAG{untrusted_webview_session_bridge}` in the victim's event log. The collector binds only to loopback and does not forward data.

Repeat with `safelab://webview?...` and `-p com.example.hardened`: the URL is rejected. The hardened local page has no session bridge. Loading an arbitrary `file://` page is not by itself proof that cross-origin reads or SQLite exfiltration will work on a particular WebView version; this lab uses the explicit JavaScript bridge for a reproducible disclosure.

Cleanup: stop the collector with Ctrl+C and run `adb -s emulator-5554 reverse --remove tcp:8765`.

## 2. Broad FileProvider root plus grant broker

Open **SurfaceLab Probe** and tap **Request private session**. This uses `startActivityForResult` to reach the exported `ShareActivity`, which returns a FileProvider URI and `FLAG_GRANT_READ_URI_PERMISSION`. The provider itself remains `exported=false`, as FileProvider requires.

Expected: the probe reads and displays the target's actual `session.json`, including `FLAG{provider_grant_crosses_app_sandbox}`. **Request ledger database** demonstrates a binary copy; the probe saves it to its own internal files directory. **Request public handout** is the positive control.

Switch to the hardened target. The private session and database requests are rejected. The public handout is still readable, demonstrating a narrow, functional sharing policy.

This crosses an app sandbox boundary through a URI grant. It does not grant root, cross Android user/profile boundaries, or reveal files belonging to another UID that the provider's own UID cannot read. A broad root alone is insufficient: the untrusted grant broker makes this exploitable.

Acquire the probe's copied file with:

```powershell
python scripts/collect.py --serial emulator-5554 --package com.example.labprobe
```

The newest file request overwrites `capture-PACKAGE.bin`. Collect after the request whose bytes you want to preserve.

## 3. Biometric callback bypass and plaintext data key

For a normal biometric interaction, enroll a fingerprint and device PIN in emulator Settings. Open the vault and tap **Authenticate to unlock**. On an emulator, use its fingerprint simulation control (or `adb emu finger touch ID` after enrollment).

The optional Frida demonstration requires a matching Frida client/server and an emulator on which you can run frida-server, or your own Gadget-instrumented lab APK. A stock non-rooted physical device does not automatically permit arbitrary app instrumentation. The project does not install instrumentation into other apps.

```powershell
frida -U -f com.example.vulnerable -l scripts/frida-vault.js
```

Open the confidential vault, then tap **Authenticate to unlock**. The script replaces this lab's `beginAuthentication` method and supplies a forged success callback. Expected:

- The vault decrypts and displays `FLAG{biometric_callback_is_not_crypto_auth}`.
- Frida prints the synthetic AES data key in Base64 when `unwrapKey` runs.

The Keystore wrapping key is still non-exportable. The exposed bytes belong to a separate AES data key that the app unwraps and caches in Java memory. The attack therefore demonstrates weak key-use authorization and plaintext-key handling, not extraction of hardware-protected Keystore key material.

Run the same script against `com.example.hardened`. The forged callback has no CryptoObject, and the app shows `BLOCKED`. A real unlock uses an authentication-bound private-key operation to unwrap the data key. The data key is cleared after the decrypted record is produced. Dynamic instrumentation can still alter an app's UI and observe data after a legitimately authorized decrypt; this comparison addresses callback-only authorization.

## 4. Mutable PendingIntent reaches an unexported activity

In the probe:

1. Select the vulnerable build.
2. Tap **Try direct admin launch**. Expected: Android raises `SecurityException` because `AdminActivity` is not exported.
3. Tap **Request ticket**. The exported target broker returns a PendingIntent created by the target UID.
4. Tap **Send modified ticket**. The probe supplies the component that was missing in the base intent. The vulnerable creator deliberately included `Intent.FILL_IN_COMPONENT` when issuing the ticket; component copying requires this extra permission in addition to mutability.

Expected: the target's private admin screen opens and shows `FLAG{mutable_ticket_admin_execution}`. On API 29+, it writes a real synthetic text file through MediaStore to `Downloads/SurfaceLab/leaked_audit.txt`. Duplicate runs may cause Android to add a numeric suffix. On API 28, it writes the synthetic audit to the target's external app-specific files directory, since the lab does not request legacy broad storage permission.

Switch to the hardened target and repeat. The immutable explicit ticket opens **The destination stayed fixed** and exports no audit.

Version detail: Android 12+ requires explicit mutability flags. At target 34+, Android rejects creation of a mutable, fully implicit PendingIntent unless the app deliberately supplies `FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT`. The vulnerable training build opts in to show that anti-pattern; the hardened one does not. The probe sends the ticket from a visible activity and supplies the sender background-launch options on API 34+. No notification permission or background interception is necessary.

## 5. Forensic evidence and acquisition boundaries

Open **Open evidence inventory**. The app shows the actual locations of:

- `files/session.json` and `shared_prefs/session.xml` in the vulnerable flavor;
- `databases/ledger.db` and any live `-wal` / `-shm` siblings;
- external `files/Documents/invoice-1042.pdf`;
- `files/vault.json` containing ciphertext, wrapping IV, and wrapped key;
- `files/events.log` containing synthetic observation events.

The hardened flavor stores sensitive fixtures in `no_backup/`, uses restrictive backup rules, and applies `FLAG_SECURE` to the vault and evidence screens. **Mark evidence inventory observed** is a manual teaching marker, not proof of acquisition.

For a deterministic logical collection from these debug builds:

```powershell
python scripts/collect.py --serial emulator-5554 --package com.example.vulnerable
```

The script stops only this app, streams a binary-safe tar through `adb exec-out run-as`, writes acquisition metadata and a SHA-256 hash, and attempts to pull its external files. Copy the entire SQLite DB/WAL/SHM set before opening it in an analysis tool. Do not interpret `run-as` success as a privilege escalation: debuggable apps explicitly permit it. Repeat with the hardened build to see that debug collection remains possible even though backup and public sharing are restricted.

Legacy backup can be explored on a device that still supports it:

```powershell
adb -s emulator-5554 backup -f app_backup.ab -noapk com.example.vulnerable
```

Complete any on-device confirmation and inspect the archive before claiming acquisition success. `allowBackup=true` does not guarantee that a contemporary device supports this command or includes app data. At target API 31+, Android 12+ normally excludes app data from `adb backup`; `debuggable=true` is the documented development opt-in. Auto Backup and device-to-device transfer are separate mechanisms with separate modern rule sections. Legacy `.ab` extraction requires an independently supplied compatible Android Backup Extractor; it is not bundled here.

To inspect recent-app previews, open the vulnerable evidence screen, press Home, and open Recents. Snapshot content, persistence, format, and paths depend on Android/OEM behavior. `/data/system_ce/0/snapshots/` is a system-owned acquisition target and requires an appropriate privileged forensic image. Ordinary ADB shell, the probe, and the URI grant lab do not acquire it. `FLAG_SECURE` changes screen capture behavior but is not a retroactive erasure guarantee for previous artifacts.

## Reset and cleanup

Dashboard reset clears observations only. `adb shell pm clear PACKAGE` clears that lab's state and Keystore entries; launch it again to regenerate fixtures. Uninstalling removes its sandbox. Delete the synthetic Downloads audit manually and remove any ADB reverse forwarding after the WebView demo.
