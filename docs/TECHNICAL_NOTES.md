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

Filesystem race conditions are mentioned in the introduction to the supplied notes but no race scenario is specified. This implementation covers the five concrete scenarios rather than adding an unrelated race lab.

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
