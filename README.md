# Crucible of Ruin

> A gothic dark-fantasy action platformer for Android. Oldschool vibes.

Melee-combat focused side-scroller built natively with Kotlin and Jetpack Compose. Fight through the ruined cathedral, brew alchemical buffs, and break the Crucible Chimera.

## Features

- **Combat**: 3-hit melee combo with finisher, shadow dash with i-frames, perfect riposte counter (slow-mo rupture), poise/stagger system on enemies
- **Boss encounter**: 3-phase Crucible Chimera with weak-point staggers and telegraphed attacks
- **Alchemy**: reagent gathering (Sulfur Ash / Quicksilver / Vitriol), combat buffs, permanent transmutations, status effects (burn DoT, corrosion)
- **Persistence**: device-local saves with manual JSON export/import; progress is included in Android's OS-level backup and device transfers
- **Feel**: object-pooled particles/projectiles/damage numbers (zero-alloc game loop), haptics, frame-rate cap (30/60 FPS), camera look-ahead & shake, coyote time + jump buffering

## Tech stack

- Kotlin 2.2, Jetpack Compose (custom Canvas 2D renderer)
- No cloud services: the game is fully offline and stores saves on the device
- JUnit + Robolectric for unit tests of combat, boss and save systems

**Android application ID:** `com.skalagrimmo.crucibleofruin`. The game is fully offline: no accounts, no cloud services.

## Project structure

```
app/src/main/java/com/skalagrimmo/crucibleofruin/
├── MainActivity.kt          # Screen routing, edge-to-edge setup
├── game/
│   ├── GameEngine.kt        # Simulation coordinator, combat, enemy AI, events and camera
│   ├── GamePhysics.kt       # Player velocity integration and platform collision resolution
│   ├── GameLevelFactory.kt  # Current full level layout and encounter setup
│   ├── TiledLevelLoader.kt  # TMX object-layer importer (prototype, not live yet)
│   ├── GameRenderer.kt      # Canvas rendering
│   ├── GameScreen.kt        # HUD, touch controls, game loop host
│   ├── GameInputController.kt # Testable adapter from touch controls to engine inputs
│   ├── MainMenuScreen.kt    # Menu, settings, save export/import
│   ├── AssetCache.kt        # Sound/texture preloading
│   ├── boss/                # Boss AI, models, health bar
│   ├── alchemy/             # Crafting workbench, buffs, reagents
│   ├── status/              # Combat status effects (burn, corrosion)
│   ├── codex/               # Grimoire bestiary UI
│   └── data/                # Device-local save repository + JSON export codec
└── ui/theme/

app/src/main/assets/levels/ruined_bastion.tmx  # First editable Tiled section
```

## Building

Requires JDK 21 and Android SDK 36.

```bash
./gradlew :app:assembleDebug
```

> **Note:** the Gradle wrapper is committed. No accounts or secrets are needed — the game is fully offline.

## CI debug APK

Successful GitHub Actions runs upload `app-debug.apk` as the `crucible-of-ruin-debug-apk` artifact for 14 days. The CI APK is a complete offline build suitable for gameplay smoke tests; its SHA-256 is published in the run summary and inside the artifact.

## Level editing (Tiled prototype)

The first editable slice of the level is `app/src/main/assets/levels/ruined_bastion.tmx`. Open this file in Tiled Map Editor 1.12 or newer. Its object layers are `Platforms`, `Hazards`, `Enemies`, and `Breakables`; object coordinates and sizes are expressed in world pixels, and gameplay types are stored as object properties.

This is deliberately a prototype, not yet the live level source: `GameEngine` still loads the full world from `GameLevelFactory`. `TiledLevelLoader` converts the TMX objects into existing game models, and `TiledLevelLoaderTest` checks key coordinates and object counts. Finish mapping the remaining sections and add full-level parity checks before switching gameplay to imported maps.

## Testing

```bash
./gradlew :app:testDebugUnitTest
```

`BossUnitTest` covers boss phases, weak-point stagger, status-effect DoT, dash/riposte mechanics, combo progression, poise/knockback, and the damage-number pool. `GameEnginePhysicsTest` covers starting-platform landing, pit-fall death/respawn, walking off a platform edge, coyote-time jumping, jump buffering, and hazard invulnerability. `GameInputControllerTest` covers touch hold/tap routing into the engine. `GameLevelLayoutTest` checks representative level landmarks and current world-object counts. `TiledLevelLoaderTest` validates the editable Ruined Bastion TMX prototype and its geometry.

## Save and backup behavior

- Progress lives in a single device-local save slot (SharedPreferences). There are no accounts and no cloud sync.
- Android's OS-level backup (Auto Backup and device-to-device transfer) includes the app's save data, so progress survives reinstall and device migration subject to the OS backup policy.
- For full control, the Options menu offers manual export/import: export writes a portable JSON save file, import restores it on any device.
- Upgrading from an older cloud-sync build keeps device progress: the freshest previously stored snapshot is adopted automatically the first time the new build runs.

## License

Released under the [MIT License](LICENSE).
