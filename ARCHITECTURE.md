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
| `GameEngine.kt` | World initialization, simulation update order, player movement/physics, platform collision, combat, enemy updates, status ticking, projectiles/pickups, checkpoint and camera updates | Models/pools, boss classes, status controller, audio/haptics, callbacks |
| `GameRenderer.kt` | Draws the current world and combat state on a Compose Canvas | Reads `GameEngine` and its entities |
| `GameModels.kt` | Platform/hazard/checkpoint/breakable/pickup/projectile/particle/damage-number models and object pools | Engine creates/updates; renderer draws |
| `boss/` | Boss types, phases, attack/weak-point behavior, boss state; a dedicated health-bar composable | Engine updates boss; UI displays boss state |
| `alchemy/` | Reagent pouch, recipe matching/crafting, temporary buffs, permanent ranks | Screen handles UI and save integration; engine receives derived multipliers/resource callbacks |
| `status/CombatStatus.kt` | Status definitions, duration/stacks, periodic damage ticks, corrosion stat modifiers | Player/enemy controllers are updated by the engine; UI reads active effects |
| `data/` | Account/authentication and local-first save/cloud synchronization | Menu and game screen |
| `AssetCache.kt`, `GameAudio.kt`, `GameHaptics.kt` | Preloaded/generated audio and textures plus sound/vibration helpers | Activity lifecycle and gameplay callbacks |

A small naming detail worth knowing: `PlayerEntity` and `EnemyEntity` currently live in `GameEngine.kt`; `GameModels.kt` holds many of the other world/effect models and pools.

## Important data paths

### Input to simulation

Compose touch controls set the engine's input flags or call actions such as `onJumpPressed()`, `onAttackPressed()`, `onSubPressed()`, and `onDashPressed()`. The frame loop then calls `GameEngine.update(dt)`, which updates the simulation. The renderer reads the resulting state; it should remain a presentation step, not the place where physics or combat rules are decided.

### Combat status effects

A hit/skill applies a status to an entity's `StatusEffectController`. Each engine update advances effect duration and periodic ticks. Player and enemy update paths apply tick damage and spawn feedback particles; the Compose HUD reads active player debuffs for their labels and timers. This is why damage-over-time can continue after the original hit animation has finished.

### Alchemy and saves

`AlchemyViewModel` owns the pouch, recipes, temporary buffs, and permanent ranks. The screen bridges those values to engine multipliers and routes collected resources back into the view model. Checkpoint/victory events trigger local/cloud saves through `UserSaveRepository`; device-only settings use the local snapshot path.

## Current architectural pressure points

These are observations, not immediate change requests:

- `GameEngine.kt` is a large coordinator as well as the home of world setup, physics/collision, and much of combat/AI. Changes in those areas can therefore affect one another.
- `GameScreen.kt` combines several roles: frame-loop host, touch-input adapter, HUD/overlay composition, alchemy bridge, and save orchestration.
- The screen and renderer read mutable engine/entity state directly. The event callbacks help with some outward events, but there is not yet a separate immutable render snapshot or narrow engine API.
- `CombatStatusType` contains Compose `Color` values alongside gameplay parameters. That is practical for the current app, but it ties part of the status definition to presentation types.
- Existing tests cover significant boss/combat/status and save behavior, but the current test set does not show a dedicated suite for platform collision/landing, falling between platforms, or touch-to-engine input wiring.

## Safe order for future structural work

1. Keep this map current and use actual gameplay recordings to choose the next issue.
2. Add focused regression tests around the behavior that will be moved before changing ownership: start with movement/collision/fall handling and status-effect ticking/death accounting.
3. Extract one responsibility at a time from `GameEngine` (world definitions and collision/physics are clearer candidates than a broad rewrite). Preserve the existing update order until tests make changes safe.
4. Reduce the responsibilities in `GameScreen` incrementally: separate presentation/input wiring from persistence orchestration only when the boundaries are clear.
5. Consider decoupling gameplay data from Compose presentation types after the existing behavior is covered by tests.

Do not add new movement mechanics (for example, a grappling hook) as part of this mapping step. First establish the ownership and data flow of the current systems, then add mechanics behind clear interfaces.
