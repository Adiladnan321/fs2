# Validation record

Validated on 16 October 2026 with JDK 21, Gradle 8.9, Android Gradle Plugin 8.7.3, Android SDK 35, and an Android 9 / API 28 emulator. All three final APKs were installed from their packaged build outputs.

## Build and static checks

- Vulnerable target, hardened target, probe, and both target test APKs built successfully.
- Android lint completed for both target flavors and the probe with zero errors. The remaining warnings are documented training choices or ordinary localization/version notices.
- The Python collector, smoke, acquisition, WebView, and packaging scripts pass Python bytecode compilation.
- The Gradle distribution is pinned to its SHA-256 checksum in `gradle-wrapper.properties`.

## Device checks

The end-to-end smoke run passed 17 checks:

- vulnerable and hardened synthetic fixture policies;
- a real browsable deep-link transfer and its hardened rejection;
- a real cross-UID FileProvider grant containing the synthetic token;
- private-file denial and public-file success in the hardened build;
- OS rejection of a direct launch of the unexported admin activity;
- vulnerable PendingIntent component fill-in reaching the private admin activity;
- hardened immutable PendingIntent staying on the receipt activity;
- vulnerable audit export and hardened non-export behavior.

The target instrumentation performed 15 checks across both flavors. These include real encrypted-vault decryption without biometric cryptographic authorization in the vulnerable flavor, rejection of a forged success callback without a `CryptoObject` in the hardened flavor, provider path scope, backup placement, invoice generation, and `FLAG_SECURE` policy.

The separate WebView run loaded the loopback training page inside the vulnerable app and observed `LAB_SESSION_f3a9_synthetic_only` at the local collector. The hardened build rejected the same URL.

The acquisition script produced binary-safe sandbox archives and SHA-256 metadata for the target flavors and probe. The vulnerable archive contains the synthetic session, encrypted vault fixture, preferences, SQLite database, WAL, SHM, and event log. The hardened archive places sensitive fixtures under `no_backup/`.

Machine-readable results are in `artifacts/evidence/smoke.json` and `artifacts/evidence/webview.json`. The dashboard screenshot is `artifacts/evidence/home.png`.

## Compatibility boundary

The delivered APKs compile and target API 35 and contain explicit API 31 and API 34 handling for PendingIntent mutability, unsafe implicit-intent opt-in, scoped Downloads export, and background activity launch options. The available Android 15 emulator could not be started during validation because the host drive had about 1.1 GB free, below the AVD requirement. Device execution was therefore completed on API 28. This limitation is recorded rather than presented as Android 15 runtime validation.

Optional Frida execution and privileged recent-app snapshot carving were not automated. Both depend on instrumentation or acquisition privileges outside a normal app installation. The supplied Frida script targets only this lab and the demo guide states its prerequisites.
