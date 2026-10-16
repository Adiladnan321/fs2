# SurfaceLab demo guide

This guide includes plain-language explanations you can say during a presentation, followed by the technical steps for reproducing each result. All accounts, transfers, tokens, keys, invoices, and flags are synthetic training data.

## Start with this explanation

> "SurfaceLab is a practice app that shows six ways an Android app can trust the wrong thing or leave sensitive information behind. We will compare a deliberately vulnerable version with a version that adds specific protections. The companion app lets us test some of those boundaries from outside the target app. No real money or customer information is involved."

Use these names when speaking:

| App | Simple description |
| --- | --- |
| **SurfaceLab** | The practice app with deliberate weaknesses. |
| **SurfaceLab Hardened** | The comparison app with protections for these examples. |
| **SurfaceLab Probe** | A separate test app that asks the target to share files or perform actions. |

For each lab, explain the idea, show the vulnerable result, then repeat against the hardened app. Explain what the audience can actually see before discussing the Android APIs. A `FLAG{...}` is a training marker identifying a result; use the actual action or file as evidence too.

Use the **Before and after** tables to describe the change caused by each demonstration. "Before" means before the current attempt, not before the app became vulnerable. For a clean baseline, follow **Reset and cleanup**, reopen the apps to create their training data, remove any previous exported audit, and start a fresh collector when needed. Dashboard reset alone does not remove previous transfers or file copies. Save any evidence you want to keep before clearing app data.

## Preparation

Install all three APKs and open SurfaceLab once. The guide uses package `com.example.vulnerable` and serial `emulator-5554`; substitute your explicit emulator serial. The hardened comparison uses package `com.example.hardened` and scheme `safelab`.

The installable files are in `artifacts/`: `surfacelab-vulnerable.apk`, `surfacelab-hardened.apk`, and `surfacelab-probe.apk`. An APK is an Android app installation file. See [the installation walkthrough](../CRACKING_README.md#1-install-the-lab) for commands. Run the commands below from the project folder. `adb` is the computer tool used to communicate with the emulator or connected training device.

For a presentation with minimal setup, use the probe's **Send transfer link**, **Request private session**, and ticket buttons, plus the main app's **Open local WebView demo** and **Open evidence inventory** buttons. The WebView collector, biometric bypass, and file collection need the additional setup described in their sections. If you only explain a result without running it, tell the audience that it has not been demonstrated live.

The private Android component classes live under `com.example.surfacelab` in both flavors. Package and Java namespace are intentionally different. Use the exact component names below.

## 1. Deep-link authorization and WebView bridge

### Simple explanation: a link should not be permission

> "A deep link is an address that opens a particular part of an app. Here, opening a link also tells the vulnerable app to move 42 practice credits. The app carries out that request without checking whether the person is allowed to do it. The address tells the app what was requested; it does not prove permission."

**Show:** In SurfaceLab Probe, select the vulnerable target and tap **Send transfer link**. Point to the message saying that 42 demo credits were recorded without authentication. Return to the probe, tap **Switch target**, and send the link to the hardened app. It blocks the transfer request.

**Explain the result:** The weak app treats an incoming request as authority. The hardened example refuses to let an external link authorize a transfer. In a real app, the action needs its own authorization check.

#### Before and after: transfer link

| Stage | What to explain or show |
| --- | --- |
| Before the exploit | With fresh app data, the ledger contains the seeded training-merchant transaction. The link has not yet added a transfer for the probe's request. |
| After the exploit: vulnerable app | The link adds a new ledger row for 42 demo credits without authentication. The screen says the credits were recorded and shows `FLAG{deep_link_without_auth}`; `events.log` records the transfer too. |
| After the same attempt: hardened app | The screen says `BLOCKED: external links cannot authorize a transfer.` The request adds no transfer row. |

**Say:** "Before, this request had made no transfer. After opening the link, the weak app recorded one without checking permission. The protected example rejects the same request."

### Simple explanation: a web page can reach into the app

> "A WebView is a small browser inside an app. This app gives loaded pages a bridge to read its practice session token. A session token is a value an app uses to recognize a signed-in session. If an untrusted page can ask for that value, information can leave the app through the bridge."

**Show:** Tap **Open local WebView demo** in SurfaceLab. The offline page reads and displays the synthetic session token. To show the value reaching your computer, use the collector steps below. In the hardened app, the external collector URL is blocked and the local page has no session bridge.

**Explain the result:** Opening the offline page proves that the bridge can read the token. Seeing it arrive at the collector proves that the page sent it to your computer. These are separate observations.

#### Before and after: WebView bridge

| Stage | What to explain or show |
| --- | --- |
| Before the exploit | The synthetic token already exists in the app's training data. The demo page has not yet read it, and a fresh collector has received no token. |
| After the exploit: vulnerable app | The loaded page reads and displays `LAB_SESSION_f3a9_synthetic_only`. In the collector demonstration, the computer also receives that value. The app logs `FLAG{untrusted_webview_session_bridge}`. |
| After the same attempt: hardened app | The external collector URL is blocked. The local demo page says no session bridge exists. This attempt sends no token to the collector. |

**Say:** "Before, the page had not received the app's token. After, the weak bridge lets it read the token and, in the collector demo, send it to our computer."

### Technical walkthrough

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

### Simple explanation: the app hands out the wrong file

> "Think of each app as having a private filing cabinet. Another app normally cannot open it. Android also lets an app deliberately share a particular file. Here, the vulnerable app's sharing desk accepts requests for private files and gives the requester permission to read them."

**Show:** In the probe, select the vulnerable build and tap **Request private session**. Point to the file contents and synthetic token. Switch to the hardened build and repeat: the private file is blocked. Then tap **Request public handout** to show that allowed sharing still works.

**Explain the result:** The probe did not gain control of the phone. The target app gave it a read grant for a file it should have kept private. The fix is to limit both the shareable files and the requests the sharing code accepts.

#### Before and after: private-file sharing

| Stage | What to explain or show |
| --- | --- |
| Before the exploit | The target already has `session.json` in its private storage. A freshly reset probe has no copied file or read grant from this request. |
| After the exploit: vulnerable app | The target grants access. The probe displays the private session contents and `FLAG{provider_grant_crosses_app_sandbox}`, then saves the bytes as `capture-com.example.vulnerable.bin` in its own storage. The original target file still exists. |
| After the same attempt: hardened app | The private-session request is blocked and creates no new copy. The public handout remains shareable. |

**Say:** "Before, the other app had no copy of the session file. After, the weak app handed it permission to read and copy that file."

Collect the session copy before requesting the database or public handout: each successful file request overwrites the probe's capture file for that target.

### Technical walkthrough

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

### Simple explanation: a success message is not a cryptographic lock

> "Imagine a locked box where someone can open it just by telling the receptionist, 'The fingerprint check passed.' That is the weak design here: the app trusts a success message, but the decryption key itself does not require a biometric check. With instrumentation on our training device, we can supply that message and make the app decrypt the vault."

**Show:** With the optional Frida setup below, open the vulnerable vault and tap **Authenticate to unlock**. Point to the decrypted training record and the synthetic data key printed in the Frida terminal. Repeat against the hardened app: the forged success message is blocked.

**Explain the result:** The hardened example ties the key operation to actual biometric authorization. A fake callback alone cannot complete that operation. The key printed in the weak example is an app-managed data key; this does not show extraction of Android's protected Keystore key.

**If instrumentation is unavailable:** Show the normal biometric prompt and explain the design difference. Do not describe opening the prompt or completing a real fingerprint check as a demonstrated bypass.

#### Before and after: biometric bypass

| Stage | What to explain or show |
| --- | --- |
| Before the exploit | Open the vault so the result says `Vault is locked.` The decrypted record is not displayed, and no real biometric approval has been supplied for this attempt. |
| After the exploit: vulnerable app | The Frida script supplies a forged success callback. The vault displays the decrypted training record and `FLAG{biometric_callback_is_not_crypto_auth}` without a real biometric approval. Frida prints the app-managed AES data key. |
| After the same attempt: hardened app | The forged callback lacks the required authenticated cryptographic object. The result says `BLOCKED`, and this attempt displays no decrypted record. |

**Say:** "Before, the vault showed a locked message. After a fake success message, the weak app decrypted the record. The protected example needs actual authorization for the key operation."

### Technical walkthrough

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

### Simple explanation: a permission ticket can be rewritten

> "A PendingIntent is a ticket an app gives someone else to carry out an action using its authority. The vulnerable app gives out a ticket with the destination left blank and permission to fill it in. The probe writes the private admin screen into that blank. Android then carries out the delegated action using the target app's authority."

**Show:** First tap **Try direct admin launch** in the probe so the audience sees Android block direct access. Then tap **Request ticket** and **Send modified ticket**. The private admin screen opens and exports a synthetic audit. Switch to the hardened build, request a new ticket, and send it: only the harmless receipt screen opens.

**Explain the result:** Android still blocks a direct launch of the private screen. The vulnerable app has delegated too much authority through its ticket. The hardened ticket has a fixed destination and cannot be rewritten by the probe.

#### Before and after: delegated ticket

| Stage | What to explain or show |
| --- | --- |
| Before the exploit | **Try direct admin launch** reports a `SecurityException`: the probe cannot open the private admin screen directly. With previous exports removed, no audit has been exported for this run. |
| After the exploit: vulnerable app | Sending the modified ticket opens **Admin route reached.** The screen shows `FLAG{mutable_ticket_admin_execution}` and the location of a newly exported `leaked_audit.txt`. |
| After the same attempt: hardened app | A newly requested hardened ticket opens the fixed receipt screen. It does not reach the admin screen or export a new audit. An old vulnerable export, if present, is not deleted by this blocked attempt. |

**Say:** "Before, Android blocked direct access to the admin screen. After we changed the weak ticket, the target's delegated authority opened that screen and exported the audit."

### Technical walkthrough

In the probe:

1. Select the vulnerable build.
2. Tap **Try direct admin launch**. Expected: Android raises `SecurityException` because `AdminActivity` is not exported.
3. Tap **Request ticket**. The exported target broker returns a PendingIntent created by the target UID.
4. Tap **Send modified ticket**. The probe supplies the component that was missing in the base intent. The vulnerable creator deliberately included `Intent.FILL_IN_COMPONENT` when issuing the ticket; component copying requires this extra permission in addition to mutability.

Expected: the target's private admin screen opens and shows `FLAG{mutable_ticket_admin_execution}`. On API 29+, it writes a real synthetic text file through MediaStore to `Downloads/SurfaceLab/leaked_audit.txt`. Duplicate runs may cause Android to add a numeric suffix. On API 28, it writes the synthetic audit to the target's external app-specific files directory, since the lab does not request legacy broad storage permission.

Switch to the hardened target and repeat. The immutable explicit ticket opens **The destination stayed fixed** and exports no audit.

Version detail: Android 12+ requires explicit mutability flags. At target 34+, Android rejects creation of a mutable, fully implicit PendingIntent unless the app deliberately supplies `FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT`. The vulnerable training build opts in to show that anti-pattern; the hardened one does not. The probe sends the ticket from a visible activity and supplies the sender background-launch options on API 34+. No notification permission or background interception is necessary.

## 5. Forensic evidence and acquisition boundaries

### Simple explanation: an app leaves traces behind

> "Closing an app does not necessarily remove the information it saved. Files, transaction records, invoices, backups, and recent-app previews may leave traces. This lab shows which traces our practice app creates, where they are stored, and what access is needed to collect them."

**Show:** Open the evidence inventory in SurfaceLab and point to the session file, database, invoice, and event log. Use the collection command below to save the actual available files. Compare the hardened app's storage locations and protected evidence screen.

**Explain the result:** An inventory lists possible evidence; collecting and inspecting a file proves you obtained it. This collector has explicit debugging access because these are debug builds. Successful collection does not prove that an ordinary app can read the files or that a backup service included them.

**Describe the protections carefully:** The hardened app keeps sensitive fixtures out of normal backup locations and protects selected screens from capture. Those controls address particular routes of exposure; authorized debug collection can still read the training app's files.

#### Before and after: evidence collection

This lab demonstrates collection of existing traces using authorized debug access. The collection step is not a sandbox exploit.

| Stage | What to explain or show |
| --- | --- |
| Before collection | Running the app has already created the session, invoice, database, and other available training files. Opening the inventory lists their locations; it has not copied them to the computer. Keep any earlier acquisition separate when comparing runs. |
| After collection: vulnerable app | The computer has `artifacts/evidence/com.example.vulnerable.tar` and a matching `.json` metadata file with a SHA-256 hash. Inspect the archive for actual files; the script also attempts to copy external files such as the invoice. |
| After collection: hardened app | Authorized debug collection still works. Sensitive fixtures appear under `no_backup/` in the acquired sandbox. This does not establish that Android backup included them or that the probe could read them. |

**Say:** "Before, the evidence was stored on the device. After collection, we have copies we can inspect on our computer. We obtained them through this lab's debugging permission."

For a separate screen-preview comparison, open the vulnerable evidence screen and inspect Recents, then repeat with the hardened screen. Preview behavior depends on the device. Tapping **Mark evidence inventory observed** only changes the teaching marker; it does not collect any files.

### Technical walkthrough

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

## 6. Anti-Frida detection and runtime secret recovery

### Simple explanation: the guard can also be changed

> "This app has a guard that looks for an inspection tool inside its own running process. If the guard sees Frida, it closes the app before a report is read. Our first task is to change the guard's answer. Our second task is to inspect the report as the app processes it, because the normal screen does not show its contents."

**Show:** First attach Frida without a bypass and open **Open runtime challenge**. SurfaceLab closes. Next load the detector-only script and repeat: the screen stays open, but **Run protected operation** only shows a character count. Finally use the full script to capture the report, copy its flag, and verify it in the app.

### Before and after: two separate cracking stages

| Stage | What to explain or show |
| --- | --- |
| Before instrumentation | The challenge opens normally. Running the protected operation processes a synthetic report, but the screen does not display its contents or mark the vulnerable lab complete. |
| Frida attached, no bypass | Opening the challenge detects the agent in real `/proc/self/maps` mappings. The app saves status `instrumentation-detected`, logs the detection, and terminates its own process before reading the report. |
| After stage 1 | The detector hook returns `false`. The challenge remains open, but running the operation still hides the report. The vulnerable lab remains incomplete. |
| After stage 2 | The return-value hook prints the actual synthetic report and `FLAG{runtime_hook_<32 hex characters>}`. Pasting that flag into the app and tapping **Verify recovered flag** completes Lab 6. |
| Hardened comparison | The same guard can still be bypassed. The report hook receives a public receipt containing no Lab 6 flag, because this flavor ships a separate payload without the secret-report generator. |

**Say:** "Before, the guard stopped our inspection. After the first hook, we could keep the app running. After the second hook, we could see information that the screen was hiding. The comparison avoids supplying that secret to the client."

### Preparation for Frida

Use an emulator or training device where you can run a matching Frida server, or your own Gadget-instrumented lab build. A Google APIs emulator that permits `adb root` is suitable; a Play Store image or stock non-rooted device may not permit that setup. Root is a prerequisite for this server-based test, not something the lab obtains.

Install the host tools and check their version:

```powershell
python -m pip install frida frida-tools
frida --version
adb -s emulator-5554 shell getprop ro.product.cpu.abi
```

Download a server of the **same Frida version** for that Android CPU architecture from [Frida's official releases](https://github.com/frida/frida/releases), and decompress the `.xz` file on your computer. With a root-capable training emulator, start it as follows; replace the local server path:

```powershell
adb -s emulator-5554 root
adb -s emulator-5554 wait-for-device
adb -s emulator-5554 push C:/path/to/frida-server /data/local/tmp/surfacelab-frida-server
adb -s emulator-5554 shell chmod 755 /data/local/tmp/surfacelab-frida-server
adb -s emulator-5554 shell '/data/local/tmp/surfacelab-frida-server >/data/local/tmp/surfacelab-frida-server.log 2>&1 &'
```

Use your emulator's actual serial throughout. The examples attach to an already opened dashboard so the guard runs only when you enter Lab 6. There is no detector on the dashboard or the five earlier labs. See [Frida's Android setup](https://frida.re/docs/android/) for other instrumentable-device configurations.

### Baseline: see the real detection

Open the vulnerable dashboard, then attach without a bypass:

```powershell
adb -s emulator-5554 shell am start -W -n com.example.vulnerable/com.example.surfacelab.MainActivity
frida -D emulator-5554 -N com.example.vulnerable
```

While that Frida session is attached, tap **Open runtime challenge**. The process terminates. Inspect the detection log and status:

```powershell
adb -s emulator-5554 logcat -d -s SurfaceLabRuntime:W
adb -s emulator-5554 shell run-as com.example.vulnerable cat files/runtime-challenge.json
```

Expected log: `INSTRUMENTATION DETECTED: closing SurfaceLab before protected report access`. The observation file contains status only, not the answer. Close the terminated Frida session before the next stage.

### Stage 1: bypass the guard

Reopen the dashboard and attach with the first script:

```powershell
adb -s emulator-5554 shell am start -W -n com.example.vulnerable/com.example.surfacelab.MainActivity
frida -D emulator-5554 -N com.example.vulnerable -l scripts/frida-runtime-bypass.js
```

The script prints the real detector result before replacing `InstrumentationGuard.isInstrumentationPresent()` with a `false` return. Open the challenge and tap **Run protected operation**. The operation completes, but the report stays hidden and no flag is printed by this script. Bypassing a detector does not by itself recover the data.

### Stage 2: hook the protected return value

Exit the first Frida session, force-stop only the selected lab app, reopen its dashboard, and attach the full script:

```powershell
adb -s emulator-5554 shell am force-stop com.example.vulnerable
adb -s emulator-5554 shell am start -W -n com.example.vulnerable/com.example.surfacelab.MainActivity
frida -D emulator-5554 -N com.example.vulnerable -l scripts/frida-runtime.js
```

Open the challenge and tap **Run protected operation**. This script includes the guard bypass and intercepts `RuntimeSecret.readProtectedReport(String)` while calling the real implementation. The Frida terminal prints a synthetic report ending in a flag shaped like:

```text
FLAG{runtime_hook_<32 hex characters>}
```

Copy the actual flag from your terminal into **Paste the recovered FLAG{...}** and tap **Verify recovered flag**. Expected: **Flag verified. Both challenge stages are complete.** Returning to the dashboard shows Lab 6 as observed. A guessed flag, a flag from a different challenge, or running the operation without submitting a flag does not complete it.

The flag is generated for the current challenge, so do not type the placeholder above. Reopening the challenge creates a new answer; ordinary activity recreation preserves the current challenge. Its SHA-256-based marker is not encryption or a production authorization mechanism.

### Hardened comparison and limits

Repeat with `com.example.hardened`, using the same full script. Its detector is intentionally just as hookable. The intercepted result is **SurfaceLab / public runtime receipt** and the screen reports **No Lab 6 secret is present in this build.** Processing the public receipt marks the comparison observed; there is no secret flag to submit.

This demonstrates that detector code inside a controlled process can be altered, and that hiding a value from the UI does not remove it from memory. It does not claim that better detection makes local secrets inaccessible. The public-only hardened payload models withholding a secret from the client; this lab does not implement a backend service.

For an independent cracking attempt, inspect the APK in JADX to find the guard and report methods before reading the supplied solution scripts. The challenge activity is not exported, so open it from the dashboard.

### Automated verification and cleanup

With both target APKs installed and the matching server already running:

```powershell
python scripts/check_runtime.py --serial emulator-5554
```

Use `--adb C:/path/to/adb.exe` if needed. The script attaches a real Frida agent, verifies the unmodified detector's process termination, runs both stages, submits the captured flag through the UI, and repeats against the public-only comparison. Its machine-readable evidence is `artifacts/evidence/runtime.json`. It removes the temporary ADB forwarding it creates.

Exit Frida to remove the hooks, then force-stop and reopen the selected target to get a fresh process. If you started the server specifically for this demo, stop that server process after detaching. Dashboard reset clears the observation marker; the `runtime-challenge.json` status file remains until overwritten or app data is cleared.

## Plain-language recap

| Example | What goes wrong | What the hardened example changes |
| --- | --- | --- |
| Transfer link | Opening an address triggers a transfer without permission checks. | An external link cannot authorize the transfer. |
| Web page bridge | A loaded page can ask the app for its session token. | The external URL is rejected and the bridge is removed. |
| File sharing | The app grants another app access to a private file. | Only the intended public handout is shared. |
| Biometric vault | A forged success message is enough to use the decryption key. | Key use requires a biometric-authorized cryptographic operation. |
| Delegated ticket | The recipient can choose a privileged destination. | The ticket's destination is fixed and immutable. |
| Leftover evidence | Sensitive information appears in exposed storage, backup scope, or screen previews. | Storage, backup rules, and selected screen protections reduce those exposure routes. |
| Anti-Frida and hidden report | A detector's answer is replaced, then a hidden report is read from a function's return value. | The comparison supplies only a public receipt, so the same hook recovers no Lab 6 secret. |

There are six labs; the first lab contains both the transfer-link and WebView examples.

## Terms you can explain without jargon

| Term | Simple meaning in this demo |
| --- | --- |
| Authentication | Checking who someone is, such as with a fingerprint. |
| Authorization | Checking whether someone is allowed to perform a particular action. |
| Sandbox / UID | Android gives each app an identity and normally separates its private data from other apps. |
| FileProvider / URI grant | Android's controlled file-sharing mechanism and permission to access a particular shared file. |
| Callback | A message reporting a result. A reported success is only trustworthy if the receiving code checks the required proof. |
| Keystore / data key | Keystore protects cryptographic keys. This app also uses a separate data key to encrypt its vault record. |
| Mutable / immutable | Changeable / fixed. In the ticket example, this affects what the recipient can alter. |
| SQLite WAL / SHM | Companion files used with the database. Preserve them with the database when they are present. |
| SHA-256 hash | A file fingerprint used to check whether collected bytes have changed. It does not hide or encrypt the contents. |
| Hardened | Protected against the specific examples demonstrated here. These debug builds are not a production security template. |
| Frida / hook | A tool for inspecting and changing a running app, and the code used to intercept a function call or its result. |
| Process mappings | A list of memory regions and loaded files inside a running process. The Lab 6 detector scans its own list for recognizable Frida names. |

## Reset and cleanup

In simple terms: resetting the dashboard only clears the progress markers. It does not delete the files created during the demonstrations.

To reset the vulnerable app's data, use the explicitly selected emulator:

```powershell
adb -s emulator-5554 shell pm clear com.example.vulnerable
```

This clears that lab's state and Keystore entries; launch it again to regenerate fixtures. Repeat with `com.example.hardened` or `com.example.labprobe` if needed. Uninstalling removes an app's sandbox. Delete the synthetic Downloads audit manually and remove any ADB reverse forwarding after the WebView demo.
