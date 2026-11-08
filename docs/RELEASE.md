# Release runbook

This is the repository-side companion to the manual release checklist in issue #6.
CI proves compilation and unit tests; the steps here prepare a signed release
artifact on your machine.

## 1. Release signing (outside the repository)

Create a keystore that never enters the repo:

```bash
keytool -genkeypair -v \
  -keystore keys/crucible-release.jks \
  -alias crucible-release \
  -keyalg RSA -keysize 2048 -validity 10000
```

In the project root (next to `settings.gradle.kts`), create `keystore.properties`
(also .gitignored):

```properties
storeFile=keys/crucible-release.jks
storePassword=<keystore password>
keyAlias=crucible-release
keyPassword=<key password>
```

`storeFile` is resolved against the project root. `app/build.gradle.kts` picks this
file up automatically when it exists and wires it into the `release` build type;
without it, `:app:assembleRelease` produces an unsigned APK as before.

## 2. Building the artifact

```bash
# Local smoke build
./gradlew :app:assembleDebug

# Signed release APK (direct distribution)
./gradlew :app:assembleRelease        # -> app/build/outputs/apk/release/app-release.apk

# Play Console bundle
./gradlew :app:bundleRelease         # -> app/build/outputs/bundle/release/app-release.aab
```

Verify the signature:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Release builds are currently **unminified** (`isMinifyEnabled = false`). The APK is small
enough that R8 is not worth the reflection risk yet; `app/proguard-rules.pro` documents
the starting rules for the day minification is enabled.

## 3. Versioning

- `versionCode` must increase with every artifact uploaded anywhere (Google Play requires
  strictly monotonic values per application).
- `versionName` is the human-readable label (currently `1.0`).
- Both live in `app/build.gradle.kts` → `defaultConfig`.

## 4. Saves, backup and transfer verification

The game is fully offline: progress lives in a single device-local save slot.

- OS backup: Android Auto Backup and device-to-device transfer now include the app's
  save preferences. Verify on a physical device that progress survives each path (#6).
- Manual export/import: the Options menu exports the save as a portable JSON file and
  re-imports it. Verify the round trip on a device (#6).
- Legacy adoption: a device upgrading from the cloud-sync era keeps its freshest stored
  snapshot automatically; `UserSaveRepositoryLocalTest` pins this behavior.

## 5. Pre-publish decisions (tracked in #6)

- **Namespace/package**: the source tree, Gradle namespace, manifest, and the
  published application ID all live under `com.skalagrimmo.crucibleofruin` now —
  the placeholder `com.example` package was retired before the first public
  release (#6).
- **License**: the project is MIT-licensed (`LICENSE`); keep the copyright year in sync
  with new releases.
- **App metadata**: store listing text, icons, content rating, and the privacy policy link
  are set in Play Console, not in the repository.
- The rest of #6 (on-device save, export/import and backup verification, and gameplay
  checks on a physical device) is manual and cannot be replaced by CI.
