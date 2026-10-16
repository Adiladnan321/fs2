# Implementation and corrections

## Source map

| Surface | Implementation |
| --- | --- |
| Browsable routes, untrusted WebView bridge | `app/src/main/java/com/example/surfacelab/DeepLinkActivity.java` |
| URI grant broker | `app/src/main/java/com/example/surfacelab/ShareActivity.java` |
| Broad/narrow provider roots | `app/src/{vulnerable,hardened}/res/xml/file_paths.xml` |
| Biometric callback and vault | `AuthCallback.java`, `VaultActivity.java`, `VaultCrypto.java` |
| Ticket issuance and private destination | `TicketActivity.java`, `AdminActivity.java`, `ReceiptActivity.java` |
| Synthetic fixtures and forensic artifacts | `LabStore.java`, `ForensicsActivity.java` |
| Instrumentation detector and protected runtime report | `InstrumentationGuard.java`, `RuntimeChallengeActivity.java`, `RuntimeSecret.java`, flavor-specific `RuntimePayload.java` |
| Cross-UID probe | `probe/src/main/java/com/example/labprobe/MainActivity.java` |
| Flavor policy and exported surface | `app/build.gradle`, `app/src/main/AndroidManifest.xml` |

The two target APKs share source but have separate package IDs, UIDs, data directories, provider authorities, URI schemes, and Keystore namespaces. The companion calls exported broker activities and receives actual OS grants / PendingIntent capabilities. It cannot directly open the private admin or vault activity.

## Important distinctions from the starting notes

1. **Browsable declarations:** use the `android.intent.category.BROWSABLE` category. The flaw is missing authorization at the destination and an unsafe WebView bridge, rather than the mere presence of a URI scheme.
2. **FileProvider:** AndroidX requires a non-exported provider. The exported activity that returns a read grant is the deputy. `<root-path>` expands which files that deputy can share; it cannot expand the provider UID's Linux/SELinux permissions or cross Android user boundaries.
3. **Keystore:** `getEncoded()` on a Keystore-protected secret key does not export hardware key bytes. The vulnerable implementation instead exposes its own unwrapped AES data key. The original sample callback hook merely called the real success callback when it was already invoked; this project's script actually causes a forged success path.
4. **PendingIntent:** component copying requires the creator to explicitly include `Intent.FILL_IN_COMPONENT`; mutability alone is insufficient for this exact component-redirection example, even when the original component is empty. The vulnerable lab deliberately grants that permission along with a mutable empty base intent. Target 34+ additionally requires an unsafe implicit-intent opt-in. Modern background activity restrictions are distinct from mutability.
5. **Downloads:** scoped storage requires a supported API such as MediaStore rather than blindly writing `/sdcard/Download`. API 28 has an explicitly documented app-specific fallback.
6. **Backups:** `allowBackup=true`, legacy `adb backup`, Auto Backup, and D2D transfer are different controls and flows. A debug `run-as` collection does not prove a backup vulnerability, and no-backup storage is still readable through authorized debug access.
7. **External files:** on Android 11+ ordinary apps cannot freely read another app's external app-specific directory. ADB/forensic acquisition access and a separate app's runtime permissions are distinct.
8. **Snapshots:** recents captures are system artifacts. Recovering them requires a compatible privileged acquisition; no claim is made that ordinary ADB can dump them or that every device retains a readable screenshot.

Filesystem race conditions are mentioned in the introduction to the supplied notes but no race scenario is specified. The initial five scenarios are preserved; Lab 6 adds the requested Frida detection and runtime interception challenge.

## Lab 6 runtime boundary

The detector reads `/proc/self/maps` in the app's own process and looks for recognizable Frida agent or Gadget mapping names. It runs on challenge entry and before report processing. Detection records status and terminates only SurfaceLab's process. A scan failure blocks the challenge instead of silently allowing access. There are no simulated detection switches, open-port probes, global startup checks, or effects on unrelated apps.

The deliberately centralized `isInstrumentationPresent()` method can be replaced by a Frida Java hook. The stage-one script does that only; the full script also intercepts the real return value of `RuntimeSecret.readProtectedReport(String)`. Neither script fabricates a report or a success marker. The vulnerable screen processes the report without displaying it, then accepts the recovered per-challenge flag as a demonstration proof. Only successful proof submission records vulnerable Lab 6 completion. The SHA-256 marker is reproducible from the challenge and training code, not a cryptographic access-control design.

The flavor-specific payload files are compiled separately. The hardened payload returns only a public receipt and has no Lab 6 secret-report generator. This illustrates avoiding local delivery of a secret, rather than claiming detection resists an adversary who controls the process. Existing lab fixtures in that flavor remain debug-accessible as documented.

`scripts/check_runtime.py` uses an explicit ADB serial, a temporary forwarding port to an already running Frida server, and the Java bridge bundled with `frida-tools` when needed by Frida 17+. It checks real process detection and termination, both hook stages, actual UI proof submission, and public-only behavior. Its evidence is separate from the earlier smoke run.

## Official references

- [FileProvider API and URI permission grants](https://developer.android.com/reference/androidx/core/content/FileProvider)
- [Improperly exposed FileProvider directories](https://developer.android.com/privacy-and-security/risks/file-providers)
- [Unsafe use of deep links](https://developer.android.com/privacy-and-security/risks/unsafe-use-of-deeplinks)
- [WebView native bridge risks](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges)
- [BiometricPrompt CryptoObject](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.CryptoObject)
- [KeyGenParameterSpec authentication controls](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder)
- [PendingIntent flags and fill-in behavior](https://developer.android.com/reference/android/app/PendingIntent)
- [Intent.fillIn component-copy opt-in](https://developer.android.com/reference/android/content/Intent#fillIn(android.content.Intent,%20int))
- [Android 14 safer intent behavior](https://developer.android.com/about/versions/14/behavior-changes-14#safer-intents)
- [Android 12 ADB backup restrictions](https://developer.android.com/about/versions/12/behavior-changes-12#adb-backup-restrictions)
- [Auto Backup include / exclude rules](https://developer.android.com/identity/data/autobackup)
- [App-specific external storage access restrictions](https://developer.android.com/training/data-storage/app-specific#external-access-permissions)
- [FLAG_SECURE](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE)
- [AGP 8.7 / Gradle 8.9 / API 35 compatibility](https://developer.android.com/build/releases/agp-8-7-0-release-notes)

Dependencies are pinned for reproducibility. The lab intentionally contains security findings; any lint suppressions should be limited to the particular teaching surfaces, while unrelated implementation failures should still fail checks.
