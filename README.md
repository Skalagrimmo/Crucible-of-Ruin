# Crucible of Ruin

> A gothic dark-fantasy action platformer for Android. Oldschool vibes.

Melee-combat focused side-scroller built natively with Kotlin and Jetpack Compose. Fight through the ruined cathedral, brew alchemical buffs, and break the Crucible Chimera.

## Features

- **Combat**: 3-hit melee combo with finisher, shadow dash with i-frames, perfect riposte counter (slow-mo rupture), poise/stagger system on enemies
- **Boss encounters**: encounter chain — the Siege Golem mid-boss in the Foundry Yard, then the 3-phase Crucible Chimera finale with weak-point staggers and telegraphed attacks
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
│   ├── ActorPhysics.kt      # Shared axis-separated physics resolver for all archetypes
│   ├── GameLevelFactory.kt  # Legacy level layout — test/fallback since the live TMX switch
│   ├── TiledLevelLoader.kt  # TMX object-layer importer (live level pipeline)
│   ├── LiveLevelLoader.kt   # Live TMX asset -> GameLevelLayout (boss factory map)
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

app/src/main/assets/levels/crucible_of_ruin.tmx  # The live full world
app/src/main/assets/levels/ruined_bastion.tmx    # Editable prototype section
```

## Building

Requires JDK 21 and Android SDK 36.

```bash
./gradlew :app:assembleDebug
```

> **Note:** the Gradle wrapper is committed. No accounts or secrets are needed — the game is fully offline.

## CI debug APK

Successful GitHub Actions runs upload `app-debug.apk` as the `crucible-of-ruin-debug-apk` artifact for 14 days. The CI APK is a complete offline build suitable for gameplay smoke tests; its SHA-256 is published in the run summary and inside the artifact.

## Level editing (Tiled)

The live level is `app/src/main/assets/levels/crucible_of_ruin.tmx` — the entire world (Bastion, Laboratories, Gallery, Vitriol Pools, Sanctum, Undercroft, Iron Garrison) authored in Tiled Map Editor 1.12 or newer. `GameEngine` loads it at startup through `LiveLevelLoader`, which converts the TMX object layers into the game's models and resolves the `Bosses` layer's encounter chain (`siege_golem`, `chimera`) through a closed factory map. An unknown boss id or a broken room/door graph fails fast at load time.

`ruined_bastion.tmx` remains as the editable prototype slice that predates the full world. Object coordinates and sizes are expressed in world pixels, and gameplay types are stored as object properties (see `TiledLevelLoader`; `TiledLevelLoaderTest` checks the prototype's geometry).

`FullLevelTmxParityTest` pins the TMX and the legacy `GameLevelFactory` to identical content, so the asset can never silently drift from the tuned layout.

## Testing

```bash
./gradlew :app:testDebugUnitTest
```

`BossUnitTest` covers boss phases, weak-point stagger, status-effect DoT, dash/riposte mechanics, combo progression, poise/knockback, and the damage-number pool. `GameEnginePhysicsTest` covers starting-platform landing, pit-fall death/respawn, walking off a platform edge, coyote-time jumping, jump buffering, and hazard invulnerability. `GameInputControllerTest` covers touch hold/tap routing into the engine. `GameLevelLayoutTest` checks representative level landmarks and current world-object counts. `TiledLevelLoaderTest` validates the editable Ruined Bastion TMX prototype and its geometry. `FullLevelTmxParityTest` pins the live full-world TMX to the legacy factory, and `LiveLevelLoaderTest` covers the live asset -> layout conversion, the boss factory map, and the engine switch.

## Save and backup behavior

- Progress lives in a single device-local save slot (SharedPreferences). There are no accounts and no cloud sync.
- Android's OS-level backup (Auto Backup and device-to-device transfer) includes the app's save data, so progress survives reinstall and device migration subject to the OS backup policy.
- For full control, the Options menu offers manual export/import: export writes a portable JSON save file, import restores it on any device.
- Upgrading from an older cloud-sync build keeps device progress: the freshest previously stored snapshot is adopted automatically the first time the new build runs.

## License

Released under the [MIT License](LICENSE).
