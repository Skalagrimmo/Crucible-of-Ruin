# Architecture Map

This document describes the code as it exists today. It is a map of current responsibilities and dependencies, not a claim that the project already follows a strict layered architecture. The goal is to make future changes safer without changing gameplay behavior.

## Runtime flow

1. `MainActivity` initializes and releases `AssetCache`, requests landscape orientation, hides system bars, and switches between the main menu and the game screen.
2. `MainMenuScreen` owns menu presentation, settings, sign-in actions, and the choice of checkpoint/frame-rate/haptics passed to the game.
3. `GameScreen` creates and retains a `GameEngine` for the screen's lifetime. Its frame loop clamps frame delta, advances the alchemy view model, copies current combat multipliers into the engine, and calls `engine.update(dt)`.
4. The game's main Canvas calls `GameRenderer.render(drawScope, engine)`. Compose draws the HUD, status overlays, touch controls, crafting/codex UI, and pause UI around that canvas.
5. The engine reports selected events to the screen through callbacks such as resource collection and checkpoint activation. The screen coordinates persistence through `UserSaveRepository`.

## Component responsibilities

| Component | Responsibility today | Main connections |
|---|---|---|
| `MainActivity.kt` | Android lifecycle, orientation, immersive mode, menu/game routing | `AssetCache`, `MainMenuScreen`, `GameScreen` |
| `MainMenuScreen.kt` | Main menu, local options, account state, stats/controls/codex dialogs | `AuthRepository`, `UserSaveRepository`, `AssetCache` |
| `GameScreen.kt` | Frame scheduling, touch controls, HUD and overlays, alchemy-to-engine integration, checkpoint/victory saves | `GameEngine`, `GameRenderer`, `AlchemyViewModel`, repositories |
| `GameEngine.kt` | Runtime simulation/update order, movement intent/input flags, combat, enemy updates, status ticking, projectiles/pickups, hazard handling, checkpoint and camera updates; loads static content from `GameLevelFactory` | Models/pools, `GamePhysics`, level factory, boss classes, status controller, audio/haptics, callbacks |\n| `GamePhysics.kt` | Integrates player vertical velocity and resolves horizontal then vertical AABB collisions with platforms; sets grounded state and coyote time on landing | Called from `GameEngine.updatePlayer()` after movement intent is calculated; keeps the current collision/update order |
| `GameLevelFactory.kt` | Creates fresh lists of level platforms, hazards, breakables, enemies, and a new boss instance | Called by `GameEngine.initLevel()`; its results populate the engine's runtime collections |
| `TiledLevelLoader.kt` + `assets/levels/ruined_bastion.tmx` | Prototype import of Tiled object layers into existing world models | Covered by `TiledLevelLoaderTest`; not yet wired into the live engine, and only section one is mapped |
| `GameRenderer.kt` | Draws the current world and combat state on a Compose Canvas | Reads `GameEngine` and its entities |
| `GameModels.kt` | Platform/hazard/checkpoint/breakable/pickup/projectile/particle/damage-number models and object pools | Engine creates/updates; renderer draws |
| `boss/` | Boss types, phases, attack/weak-point behavior, boss state; a dedicated health-bar composable | Engine updates boss; UI displays boss state |
| `alchemy/` | Reagent pouch, recipe matching/crafting, temporary buffs, permanent ranks | Screen handles UI and save integration; engine receives derived multipliers/resource callbacks |
| `status/CombatStatus.kt` | Status definitions, duration/stacks, periodic damage ticks, corrosion stat modifiers | Player/enemy controllers are updated by the engine; UI reads active effects |
| `data/` | Account/authentication and local-first save/cloud synchronization | Menu and game screen |
| `AssetCache.kt`, `GameAudio.kt`, `GameHaptics.kt` | Preloaded/generated audio and textures plus sound/vibration helpers | Activity lifecycle and gameplay callbacks |

A small naming detail worth knowing: `PlayerEntity` and `EnemyEntity` currently live in `GameEngine.kt`; `GameModels.kt` holds many of the other world/effect models and pools.

## Tiled level-editor prototype

`app/src/main/assets/levels/ruined_bastion.tmx` is an editable first-section prototype. `TiledLevelLoader` maps object-layer rectangles and custom properties into existing Kotlin models. The TMX uses world-pixel coordinates so current collision boxes can be preserved without introducing tile-based collision.

**The live game does not load this file yet.** The authoritative full level remains `GameLevelFactory`; the TMX currently covers only Ruined Bastion, with no checkpoint or boss metadata. Keep the factory unchanged until all sections and required encounter metadata have been migrated and automated parity tests prove the imported layout matches the existing world.

## Important data paths

### Input to simulation

Compose touch controls set the engine's input flags or call actions such as `onJumpPressed()`, `onAttackPressed()`, `onSubPressed()`, and `onDashPressed()`. The frame loop then calls `GameEngine.update(dt)`, which updates the simulation. The renderer reads the resulting state; it should remain a presentation step, not the place where physics or combat rules are decided.

### Combat status effects

A hit/skill applies a status to an entity's `StatusEffectController`. Each engine update advances effect duration and periodic ticks. Player and enemy update paths apply tick damage and spawn feedback particles; the Compose HUD reads active player debuffs for their labels and timers. This is why damage-over-time can continue after the original hit animation has finished.

### Alchemy and saves

`AlchemyViewModel` owns the pouch, recipes, temporary buffs, and permanent ranks. The screen bridges those values to engine multipliers and routes collected resources back into the view model. Checkpoint/victory events trigger local/cloud saves through `UserSaveRepository`; device-only settings use the local snapshot path.

## Current architectural pressure points

These are observations, not immediate change requests:

- `GameEngine.kt` remains a large coordinator for physics/collision, combat, and AI. Static level content is now constructed in `GameLevelFactory`, while the engine still owns the mutable runtime collections and update order.
- `GameScreen.kt` combines several roles: frame-loop host, touch-input adapter, HUD/overlay composition, alchemy bridge, and save orchestration.
- The screen and renderer read mutable engine/entity state directly. The event callbacks help with some outward events, but there is not yet a separate immutable render snapshot or narrow engine API.
- `CombatStatusType` contains Compose `Color` values alongside gameplay parameters. That is practical for the current app, but it ties part of the status definition to presentation types.
- Existing tests cover significant boss/combat/status and save behavior. `GameEnginePhysicsTest` verifies starting-platform landing and pit-fall handling; `GameLevelLayoutTest` protects representative level landmarks and object counts. Targeted coverage is still missing for platform-edge/gap transitions, hazard/knockback interactions, and touch-to-engine input wiring.

## Safe order for future structural work

1. Keep this map current and use actual gameplay recordings to choose the next issue.
2. Extend the baseline regression tests before changing ownership, especially around platform-edge/gap transitions and input-to-engine wiring; retain tests for movement, fall handling, and status-effect death accounting.
3. Static world construction lives in `GameLevelFactory`; a separate Tiled importer is being validated against the existing models. Player integration and platform collision now live in `GamePhysics`, with landing, gap, coyote-time, jump-buffer, and pit-fall regression tests guarding behavior. Next, cover hazard/knockback interactions and touch-to-engine input wiring before considering further extraction. Preserve the existing update order.
4. Reduce the responsibilities in `GameScreen` incrementally: separate presentation/input wiring from persistence orchestration only when the boundaries are clear.
5. Consider decoupling gameplay data from Compose presentation types after the existing behavior is covered by tests.

Do not add new movement mechanics (for example, a grappling hook) as part of this mapping step. First establish the ownership and data flow of the current systems, then add mechanics behind clear interfaces.
