# Release runbook

This is the repository-side companion to the manual release checklist in issue #6.
CI proves compilation and unit tests; the steps here prepare a real Firebase
configuration and a signed release artifact on your machine.

## 1. Firebase configuration (one-time per app)

1. In the [Firebase Console](https://console.firebase.google.com/) create (or pick) a project.
2. Register an Android application with the exact package name `com.skalagrimmo.crucibleofruin`.
3. Add the SHA-1 (and SHA-256) fingerprints of every key that will sign the app —
   debug and release — under *Project settings → Your apps → App fingerprint*:

   ```bash
   # Debug keystore (the CI artifact is signed with the cached CI debug key)
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android

   # Release keystore (created in step 2 below)
   keytool -list -v -keystore keys/crucible-release.jks -alias crucible-release
   ```

4. Download the resulting `google-services.json` and place it at `app/google-services.json`.
5. `app/google-services.json` is **.gitignored on purpose** — it is generated per project
   and never committed. CI writes its own placeholder when the file is absent.

Google Sign-In and cloud saves only work in a build whose `google-services.json` matches
its signing certificate. An APK signed with a key whose SHA-1 is missing from the Firebase
app record will fail sign-in.

## 2. Release signing (outside the repository)

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

## 3. Building the artifact

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

## 4. Versioning

- `versionCode` must increase with every artifact uploaded anywhere (Google Play requires
  strictly monotonic values per application).
- `versionName` is the human-readable label (currently `1.0`).
- Both live in `app/build.gradle.kts` → `defaultConfig`.

## 5. Pre-publish decisions (tracked in #6)

- **Namespace/package**: code still lives under the `com.example` Java package with
  namespace `com.example`, while the published application ID is
  `com.skalagrimmo.crucibleofruin`. This is cosmetic for publishing, but renaming is a
  good hygiene step before the first public release.
- **App metadata**: store listing text, icons, content rating, and the privacy policy link
  are set in Play Console, not in the repository.
- The rest of #6 (Firebase account checks, device gameplay verification, backup behavior
  on a physical device) is manual and cannot be replaced by CI.
