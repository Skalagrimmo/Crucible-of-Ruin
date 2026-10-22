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

## Project structure

```
app/src/main/java/com/example/
├── MainActivity.kt          # Screen routing, edge-to-edge setup
├── game/
│   ├── GameEngine.kt        # Core simulation (player, enemies, physics, camera)
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
```

## Building

Requires JDK 21 and Android SDK 36.

```bash
./gradlew :app:assembleDebug
```

> **Note:** the Gradle wrapper is committed. Before building locally, place your `google-services.json` (Firebase console → Project settings → Android app) under `app/`. CI uses a placeholder file for compilation because unit tests do not touch live Firebase.

## Testing

```bash
./gradlew :app:testDebugUnitTest
```

`BossUnitTest` covers boss phases, weak-point stagger, status-effect DoT, dash/riposte mechanics, combo progression, poise/knockback, and the damage-number pool.

## Save and backup behavior

- Without Google Sign-In, progress is stored locally on the device.
- Signing in enables Firebase cloud saves. On a first sign-in, guest progress is imported only when the signed-in account has no existing cloud save.
- Android system backup and device-transfer rules intentionally exclude the app's local save preferences. A signed-in player should rely on the cloud save to restore progress after reinstalling or switching devices. Guest-only progress is not included in system backups and may be lost if app data is cleared, the app is uninstalled, or the device is replaced.
