# Crucible of Ruin

> A gothic dark-fantasy action platformer for Android. Oldschool vibes.

Melee-combat focused side-scroller built natively with Kotlin and Jetpack Compose. Fight through the ruined cathedral, brew alchemical buffs, and break the Crucible Chimera.

## Features

- **Combat**: 3-hit melee combo with finisher, shadow dash with i-frames, perfect riposte counter (slow-mo rupture), poise/stagger system on enemies
- **Boss encounter**: 3-phase Crucible Chimera with weak-point staggers and telegraphed attacks
- **Alchemy**: reagent gathering (Sulfur Ash / Quicksilver / Vitriol), combat buffs, permanent transmutations, status effects (burn DoT, corrosion)
- **Persistence**: local-first saves with cloud sync (Firebase Firestore) and Google Sign-In; best stats merge across devices
- **Feel**: object-pooled particles/projectiles/damage numbers (zero-alloc game loop), haptics, frame-rate cap (30/60 FPS), camera look-ahead & shake, coyote time + jump buffering

## Tech stack

- Kotlin 2.2, Jetpack Compose (custom Canvas 2D renderer)
- Firebase Auth (Credential Manager / Google ID) + Firestore
- JUnit + Robolectric for unit tests of combat, boss and save systems

**Android application ID:** `com.skalagrimmo.crucibleofruin`. Register an Android app with this exact package name in Firebase Console, then place its `google-services.json` under `app/` for real Google Sign-In and cloud saves. The CI placeholder is only for compilation/tests and does not enable real authentication or cloud synchronization.

## Project structure

```
app/src/main/java/com/example/
├── MainActivity.kt          # Screen routing, edge-to-edge setup
├── game/
│   ├── GameEngine.kt        # Simulation coordinator, combat, enemy AI, events and camera
│   ├── GamePhysics.kt       # Player velocity integration and platform collision resolution
│   ├── GameLevelFactory.kt  # Current full level layout and encounter setup
│   ├── TiledLevelLoader.kt  # TMX object-layer importer (prototype, not live yet)
│   ├── GameRenderer.kt      # Canvas rendering
│   ├── GameScreen.kt        # HUD, touch controls, game loop host
│   ├── MainMenuScreen.kt    # Menu, settings, cloud sign-in
│   ├── AssetCache.kt        # Sound/texture preloading
│   ├── boss/                # Boss AI, models, health bar
│   ├── alchemy/             # Crafting workbench, buffs, reagents
│   ├── status/              # Combat status effects (burn, corrosion)
│   ├── codex/               # Grimoire bestiary UI
│   └── data/                # Auth + save repositories (local & Firestore)
└── ui/theme/

app/src/main/assets/levels/ruined_bastion.tmx  # First editable Tiled section
```

## Building

Requires JDK 21 and Android SDK 36.

```bash
./gradlew :app:assembleDebug
```

> **Note:** the Gradle wrapper is committed. Before building locally, place your `google-services.json` (Firebase console → Project settings → Android app) under `app/`. CI uses a placeholder file for compilation because unit tests do not touch live Firebase.

## CI debug APK

Successful GitHub Actions runs upload `app-debug.apk` as the `crucible-of-ruin-debug-apk` artifact for 14 days. The CI APK uses a placeholder Firebase configuration when the real `google-services.json` is absent. It can be used for an offline/gameplay smoke test, but Google Sign-In and cloud saves require the real Firebase configuration.

## Level editing (Tiled prototype)

The first editable slice of the level is `app/src/main/assets/levels/ruined_bastion.tmx`. Open this file in Tiled Map Editor 1.12 or newer. Its object layers are `Platforms`, `Hazards`, `Enemies`, and `Breakables`; object coordinates and sizes are expressed in world pixels, and gameplay types are stored as object properties.

This is deliberately a prototype, not yet the live level source: `GameEngine` still loads the full world from `GameLevelFactory`. `TiledLevelLoader` converts the TMX objects into existing game models, and `TiledLevelLoaderTest` checks key coordinates and object counts. Finish mapping the remaining sections and add full-level parity checks before switching gameplay to imported maps.

## Testing

```bash
./gradlew :app:testDebugUnitTest
```

`BossUnitTest` covers boss phases, weak-point stagger, status-effect DoT, dash/riposte mechanics, combo progression, poise/knockback, and the damage-number pool. `GameEnginePhysicsTest` covers starting-platform landing, pit-fall death/respawn, walking off a platform edge, coyote-time jumping, and jump buffering. `GameLevelLayoutTest` checks representative level landmarks and current world-object counts. `TiledLevelLoaderTest` validates the editable Ruined Bastion TMX prototype and its geometry.

## Save and backup behavior

- Without Google Sign-In, progress is stored locally on the device.
- Signing in enables Firebase cloud saves. On a first sign-in, guest progress is imported only when the signed-in account has no existing cloud save.
- Android system backup and device-transfer rules intentionally exclude the app's local save preferences. A signed-in player should rely on the cloud save to restore progress after reinstalling or switching devices. Guest-only progress is not included in system backups and may be lost if app data is cleared, the app is uninstalled, or the device is replaced.
