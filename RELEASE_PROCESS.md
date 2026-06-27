# Production Release Playbook

This document details the step-by-step procedures required to verify, package, sign, and distribute an official release of **Nexus Mesh** for public deployment.

---

## 1. Release Branching Strategy (Git Flow)

Official releases flow from development to stable release tags using a structured Git workflow:

```
[develop] ----+-------------------> (Regression Testing Sweeps)
              |
              v
[release/vX.Y.Z] (Hotfixes, Version Code increments)
              |
              +----> [main] (Stable Release Tag: vX.Y.Z)
```

1.  **Freeze**: When minor/major goals are met, branch from `develop` to `release/vX.Y.Z`.
2.  **Verify**: Run all automatic checks, linters, and JVM test suites.
3.  **Merge**: Merge the release branch into `main` and `develop`. Tag `main` with the release version name.

---

## 2. Release Readiness Sweeps (Verification Checklist)

Before generating release binaries, developers must verify the following checklists:

### Automated Checklists
- [ ] Run and pass all unit and Robolectric tests: `gradle :app:testDebugUnitTest`
- [ ] Check code styling and lint compliance: `gradle spotlessCheck` (or equivalent code formatters)
- [ ] Verify screenshot regression tests are identical: `gradle :app:verifyRoborazziDebug`

### Manual Quality Checks (Emulator / Device Lab)
- [ ] **Onboarding System**: Clear application storage and launch the app. Complete onboarding successfully, backup 12-word recovery phrase, and verify recovery.
- [ ] **Direct Messaging**: Connect two test devices over simulated Bluetooth or peer networks. Verify 1:1 messaging, message edits, inline replies, pinning, and deleting.
- [ ] **Wiki Collaborative Sync**: Open a wiki page, edit it, synchronize with a peer, and confirm the higher-version logical clock merges successfully.
- [ ] **Extreme SOS broadcast**: Trigger SOS Mode. Confirm siren sounds and emergency beacon cascades across the topology graph.

---

## 3. Cryptographic Code Signing & Keystore Management

Production Android applications must be signed with a secure, private release key before being distributed or uploaded to app stores.

### Keystore Configuration
Create a secure `keystore.properties` in the project root containing your signing details (never commit this file to git):

```properties
keyAlias=nexus_release_alias
keyPassword=secure_key_password
storeFile=/path/to/nexus-release.keystore
storePassword=secure_keystore_password
```

The `build.gradle.kts` configuration will automatically parse these parameters to configure the `release` signing config block.

### Generating signed Release APK/AAB
Execute the following Gradle tasks to produce signed release binaries:

```bash
# To generate a signed Android App Bundle (AAB) for Google Play Console:
gradle :app:bundleRelease

# To generate a signed Android Package (APK) for sideloading/F-Droid:
gradle :app:assembleRelease
```

---

## 4. Distribution Channels

### A. Sideloading (Direct Downloads)
For off-grid and emergency scenarios, the primary distribution vector is direct APK sideloading via memory cards, USB storage, or Local BLE share.
*   The compiled `/app/build/outputs/apk/release/app-release.apk` is posted to the GitHub Releases page corresponding to the tagged release version.

### B. F-Droid (Open Source Repository)
F-Droid compiles and builds apps directly from source.
*   Update the metadata file in the F-Droid data repository with the latest git commit tag.
*   Verify that no proprietary binary blobs are bundled into the release candidate.

### C. Google Play Store
For mainstream accessibility, releases are uploaded to the Google Play Console:
*   **Closed Testing Track**: Internal testing release for immediate QA feedback.
*   **Production Track**: Staged rollout (starting at 10% and moving incrementally to 100%) to monitor crash reporting rates in production enclaves.
