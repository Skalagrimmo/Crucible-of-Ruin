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

## CI debug APK

Successful GitHub Actions runs upload `app-debug.apk` as the `crucible-of-ruin-debug-apk` artifact for 14 days. The CI APK uses a placeholder Firebase configuration when the real `google-services.json` is absent. It can be used for an offline/gameplay smoke test, but Google Sign-In and cloud saves require the real Firebase configuration.

## Testing

```bash
./gradlew :app:testDebugUnitTest
```

`BossUnitTest` covers boss phases, weak-point stagger, status-effect DoT, dash/riposte mechanics, combo progression, poise/knockback, and the damage-number pool.
