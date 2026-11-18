# First Location — Layout and Traversal Audit

**Baseline:** 2026-11-18  
**Source of truth:** `app/src/main/assets/levels/crucible_of_ruin.tmx`  
**Working branch:** `first-location-audit`  
**Scope:** audit and complete the current first level before expanding into a later location. This document records source-level findings; it does not claim that a device playtest has been completed.

## 1. Current live level inventory

The game screen installs `LiveLevelLoader.loadFromAssets(context.assets)` as the `GameEngine` level source. The live TMX is therefore authoritative for normal gameplay. `GameLevelFactory.create()` is the fallback if the TMX fails to load and should be kept in parity when layout data changes.

| Item | Current count |
|---|---:|
| World bounds | 6200 × 1080 px |
| Tiled object layers | 8 |
| Platforms | 65 |
| Hazards | 13 |
| Enemies | 20 |
| Breakables | 17 |
| Rooms | 15 |
| Directed door links | 32 |
| Gate triggers | 1 |
| Boss encounters | 2 |

The world is arranged as an upper band (`y=0..540`) and a lower band (`y=540..1080`). The lower band exists from approximately `x=3100` to the eastern world boundary at `x=6200`.

## 2. Confirmed structural findings

### 2.1 The lower route currently reads as one long floor

Four main floor objects are edge-to-edge at the same top coordinate:

| Platform | X range | Top Y | Height |
|---|---:|---:|---:|
| platform_36 | 3100–3500 | 1000 | 80 |
| platform_37 | 3500–4000 | 1000 | 80 |
| platform_42 | 4000–4360 | 1000 | 80 |
| platform_44 | 4360–6200 | 1000 | 80 |

There are architectural blocks and ledges above this floor, but the base walking surface is continuous from `x=3100` to `x=6200`. This is a strong source-level explanation for the lower route feeling like a long corridor rather than several distinct spaces.

**Design direction:** keep a continuous traversal option only where it is intentional. Break the visual and spatial rhythm with real structural changes: recessed service bays, elevated catwalks, broken floor spans, support piers, machine pits, short vertical transitions, bridges, and occasional larger chambers. Any gap must have a readable route and a tested recovery/landing path.

### 2.2 Lower hazards occupy the walking-surface band

The lower floor top is `y=1000`. The lower hazards are placed immediately above it:

| Hazard | X range | Y range | Type |
|---|---:|---:|---|
| hazard_7 | 3120–3180 | 980–1000 | SPIKES |
| hazard_8 | 3900–3980 | 970–1000 | ACID |
| hazard_9 | 4040–4100 | 980–1000 | SPIKES |
| hazard_10 | 4950–5030 | 980–1000 | ACID |
| hazard_11 | 5210–5270 | 980–1000 | SPIKES |
| hazard_12 | 5470–5540 | 980–1000 | ACID |
| hazard_13 | 6160–6200 | 980–1000 | SPIKES |

These are not simply pits below a platform: their rectangles reach 20–30 px above the floor top, into the player's walking-body band. This can be a valid floor hazard, but it needs deliberate encounter and enemy placement around it.

### 2.3 Hazard rules are asymmetric for player and ordinary enemies

In `GameEngine`, the player update performs an AABB test against every hazard and applies damage/knockback. The ordinary enemy update loop performs AI and platform resolution, then player-contact checks, but it does not perform an equivalent hazard test.

Consequently, an enemy can appear to walk through a floor hazard that damages the player. The first response should not automatically be “make all enemies take the same damage”: different archetypes may need different resistances or behaviors. For the first pass, treat this as a layout and encounter-design defect to resolve explicitly.

### 2.4 Enemy placement is close to several lower hazards

The following pairs deserve in-game inspection. These are proximity flags, not claims that their collision boxes definitely overlap; enemy dimensions and animation offsets must be confirmed in the running build.

| Hazard | Nearby enemy | Source coordinates | Why inspect |
|---|---|---|---|
| hazard_7 spikes | enemy_10 LURKER | hazard x=3120–3180; enemy x=3250, patrolRange=60 | Hazard ends only 10 px before the nominal left patrol limit |
| hazard_8 acid | enemy_12 CULTIST | hazard x=3900–3980; enemy x=3860, patrolRange=0 | Static ranged enemy is placed immediately beside the pool |
| hazard_9 spikes | enemy_13 GOLEM | hazard x=4040–4100; enemy x=4200, patrolRange=90 | Hazard ends 10 px before the nominal left patrol limit |
| hazard_13 spikes | enemy_18 GOLEM | hazard x=6160–6200; enemy x=6080, patrolRange=30 | Hazard sits near the world edge and the golem's patrol envelope |

The hazard sequence at `x=4950..5540` also breaks up the same flat floor into repeated acid/spike strips. It needs to be designed as a deliberate traversal/arena sequence, rather than a row of damage rectangles on an otherwise continuous plane.

### 2.5 The room graph is already connected

All 15 room records have valid references and form one connected graph. The 32 directed door records form 16 reciprocal passage pairs. One room pair (Garrison Street ↔ Garrison Keep) has two different connector positions, so the metadata already describes both the street-level route and an elevated/alternate connection.

This is a useful foundation: the first location does not need an arbitrary new maze. Its existing room graph and loop should be made legible in the actual platforms, shafts, bridges, and return paths.

## 3. Traversal areas requiring a device playtest

Static coordinates cannot prove a jump is fair. The following segments need measured playtesting against the actual player physics, camera, enemy behavior, and touch controls.

| Segment | Approx. X span | Current geometry to check |
|---|---:|---|
| Bastion Entry / Ascent | 0–1200 | 80 px acid gap, vertical lift at x=610, ledges rising in 70 px steps, narrow upper ledges, wall at x=1140 |
| Bastion Ramparts | 1200–1920 | 50 px spike gap between floor sections, vertical ledges and the wall at x=1880 |
| Crucible Laboratories | 1920–2940 | isolated floor section, acid channel x=2480–2830, moving horizontal girder near x=2800 |
| Golem Gallery / shaft | 3100–3750 | boss-arena floor split, open shaft, lower elevator at x=3370 with 500 px vertical travel |
| Vitriol Pools / Sanctum Approach | 3750–5270 | acid crossing at x=4000, separated floor sections, stair/ledge sequence over the lower route |
| Grand Sanctum / Garrison | 5270–6200 | long boss arena, lower street hazards, raised blocks, shortcut gate and upper return route |

For every jump or drop, record:
- take-off platform and approximate player position;
- target platform and usable landing width;
- horizontal gap and vertical difference;
- ceiling or wall interference;
- whether the landing is possible without a frame-perfect input;
- whether failure is recoverable or forces a long replay.

Do not widen every gap by default. First distinguish intended precision challenges from accidental geometry and camera/readability problems.

## 4. Tearscape reference — principles to adapt

Tearscape is useful here as a world-structure reference, not as a visual template. The useful principles are:

- **Density over raw size:** a location feels larger when spaces connect meaningfully, not merely because the corridor is longer.
- **Distinct regional identity:** adjacent areas need different spatial signatures and landmarks, even when they reuse a modest set of building blocks.
- **Useful interconnectivity:** combine direct paths, gated routes, short loops, and vertical connections; avoid forcing all exploration through one linear hallway.
- **Recognizable grid/room rhythm:** use room-sized spatial units as planning aids, but make each room distinct through height, floor shape, machinery, sightlines, and traversal purpose.
- **Exploration information:** show inaccessible ledges, sealed service doors, lift shafts, and alternate routes before they become reachable, so the player can form a mental map.

For Crucible of Ruin, translate these ideas into a side-scrolling Gothic-industrial complex: cathedral-height spaces, foundry bays, maintenance galleries, pressure chambers, vertical shafts, bridge spans, and short service routes. Do not copy Tearscape's top-down camera, Game Boy palette, screen transitions, or exact room compositions.

## 5. Work order

1. **Baseline playthrough:** test the current APK without changing level geometry. Capture short videos or screenshots at every suspected issue.
2. **Traversal audit:** verify jump reach, landing width, headroom, collision edges, lift timing, and camera readability.
3. **Lower-route redesign:** resolve hazard/enemy asymmetry in encounter placement, then reshape the continuous lower floor into a sequence of readable spaces.
4. **Area identity and length:** create distinct room silhouettes and meaningful route variations. Increase playable space only where it adds a new traversal or exploration decision.
5. **Full replay:** retest the entire first level at normal difficulty, including both bosses and the lower route.
6. **Only then move to the next location.**

## 6. Scope guardrails

- Preserve the present combat complexity and boss behavior unless playtesting identifies a specific balance defect.
- Do not reduce difficulty merely to make the route easier to traverse.
- Do not start a major art overhaul while the collision and room geometry are still unsettled.
- Keep the live TMX and fallback factory aligned when changing level layout.
- Treat visible decoration and collision geometry as separate layers: bridges, pipes, braces, and machinery may enrich the silhouette without silently changing a landing surface.
- Any new route must have a clear entry, exit, purpose, and camera-readable landing.

## 7. Additional player-reported defects and source-level verification (2026-11-18)

The current playthrough has also exposed three high-priority problems beyond the general platform spacing audit:
- one checkpoint appears embedded in / misaligned with its platform;
- the first boss occupies the same choke-point complex as the lift that leads to the lower level;
- the lower tier is spatially chaotic and does not yet read as a deliberately designed route.

### 7.1 Checkpoint anchor and sprite overlap

The engine currently hardcodes five checkpoints, rather than loading their coordinates from TMX. The lower-tier checkpoints are:
- Undercroft: `Checkpoint(3, x=3560, y=956)`
- Iron Garrison: `Checkpoint(4, x=5850, y=956)`

The player collision body is `26 × 44 px`. Both lower checkpoints therefore put the player's feet exactly at `y=1000`, matching the top of the lower floor. That is not a physical AABB penetration by itself.

However, `GameRenderer` draws a `24 × 50 px` checkpoint texture at `(cp.x - 12, cp.y - 38)`. Its rectangle is thus `x=[cp.x-12, cp.x+12]`, `y=[cp.y-38, cp.y+12]`. The player respawns at `(cp.x, cp.y)`, with a 26 × 44 px body, so the checkpoint image and player body overlap by 12 px vertically and 12 px horizontally. The player is drawn later, so the checkpoint marker can be partially obscured by the respawning character.

**Audit action:** treat the player-reported checkpoint/platform issue as confirmed UX/anchor misalignment, but do not yet claim the player collision body is buried in the floor. Check the marker's intended ground-contact point and re-anchor it relative to the platform top. Also move the checkpoint respawn position to a safe, tested standing point if its current spot is visually or mechanically obstructed. Because checkpoint coordinates live in `GameEngine.checkpoints`, a TMX layout revision alone will not fix them.

### 7.2 The first boss and the lower-level lift share one choke-point complex

Live TMX and engine data show:
- First boss: `siege_golem` spawn `(3500, 260)`, dimensions `130 × 170`; its feet align with the upper Gallery floor at `y=430`.
- Upper Gallery floor is split into `x=3100..3360` and `x=3450..3750`, leaving a 90 px shaft opening.
- The vertical lift is `x=3370, y=960, w=70, h=20`, with `dy=-500` and speed `110 px/s`. It travels between `y=960` and `y=460`, a 500 px vertical stroke.
- The lift is horizontally inside that 90 px gap, with 10 px clearance to either upper floor section.
- At its upper endpoint the lift top is `y=460`, 30 px below the upper floor top `y=430`. At its lower endpoint its top is `y=960`, 40 px above the lower floor top `y=1000`.
- The Golem encounter trigger is `x=3200` with `triggerMaxY=540`, so it arms while the player is on the upper band, before the player reaches the lift shaft.

This means the first boss, its arena, and the vertical transition into the lower tier all occupy one shared spatial choke point. The boss is placed immediately to the right of the lift and the split floor; the lift's upper landing does not align flush with the adjacent floor. The 10 px lateral clearances and 30 px height step may be intentional traversal challenges, but their combination with a boss encounter can make the route feel like the boss has been inserted into the elevator entrance.

**Audit action:** redesign and test this as one connected set piece, not as three unrelated objects. Decide what the boss is meant to guard, where the player stands during the fight, whether the lift can be used during the encounter, and how the player safely reaches the lift after the fight. Make the route into the Undercroft readable before and after the encounter. Do not simply move the boss or lift by intuition before testing the current build.

### 7.3 Lower-tier layout needs a room-by-room reconstruction

The lower tier contains five named rooms but currently has:
- one shared floor line across most of the tier;
- a 500 px lift stroke entering the tier near the western end;
- one vertical climb of isolated girders/ledges;
- a few large rectangular blocks acting as walls or raised masses;
- repeated floor-level hazard strips and enemies placed near some of those strips.

The area currently has a room graph, but its physical floor plan does not yet express five distinct spatial identities. Rebuild it from the room roles rather than moving individual hazards in isolation:

1. **Sunken Descent:** arrival chamber and lift landing, with a safe space to orient and recover.
2. **Undercroft Halls:** a large structural hall with a readable main route and one elevated maintenance route.
3. **Reliquary Vault:** a distinct chamber with its own traversal/combat logic, not another segment of the same floor.
4. **Garrison Descent:** a true transition/shaft space linking the upper Sanctum Approach, Vault, and lower Street.
5. **Garrison Street / Keep:** a different spatial rhythm with intentional cover, elevated access, and gated return links.

Keep the graph's existing connections unless a playtest demonstrates that a connection is redundant or confusing. Every new gap, hazard, bridge, or vertical shaft must have an explicit traversal role and a clear safe landing/recovery route.


## 8. Boss arena escape exploit (player reproduction, 2026-11-18)

**Observed in play:** during the Siege Golem encounter, the player can step onto the vertical lift and ride it down to the lower tier. The player can later return to the Gallery and find the Golem already dead.

### 8.1 Confirmed code path

- The upper arena's two `BOSS_GATE` objects seal the horizontal exits when the boss activates, but the vertical lift shaft remains usable. There is no hatch/lock or encounter-state check on the lift.
- `GameEngine.step()` calls `updateBoss(dt)` every simulation tick, regardless of the player's distance, room, camera visibility, or vertical tier.
- `BaseBoss.update()` continues to tick the boss's `statusController` while the boss is active. Periodic status damage is subtracted directly from boss HP, and calls `onDefeated()` when HP reaches zero.
- The player can apply Alchemy-Burn and Corrosion during melee; a weak-point hit applies both. Their timers are not suspended when the player leaves the arena.
- A generic boss does not die merely from falling out of bounds: the examined `BaseBoss` has no world-bound death rule. HP reaching zero is the relevant death condition in this path.
- When an intermediate boss is dead and another encounter remains queued, `GameEngine.updateBoss()` immediately advances the encounter and replaces the active boss with the next one. The Siege Golem can therefore disappear from his arena while the Chimera is created at its later spawn.
- If the player dies during an active boss fight, a separate respawn path explicitly calls `resetBossEncounter()`; that is not the behavior observed in a living retreat.

**Most likely explanation for the reported sequence:** after the player retreats down the lift, one or both applied damage-over-time effects continue draining the Golem's remaining HP. Once it reaches zero, the engine advances the encounter to the Chimera. On returning to the Gallery, the player no longer sees the Golem. This is strongly supported by the source flow, although the exact historical HP/status state from the reported run cannot be reconstructed from the repository.

### 8.2 Why this is a real encounter-design defect

The player has found a way to leave an encounter whose exits appear sealed but whose vertical route is not. This can:
- turn retreat into an unintended damage-over-time kill strategy;
- make the boss vanish without the player witnessing the defeat or receiving clear encounter feedback;
- make the arena's gate logic inconsistent, because the side exits lock while the lift shaft remains open;
- leave the player uncertain whether the boss was killed, reset, despawned, or skipped.

### 8.3 Required design decision before patching

Pick one explicit encounter rule; do not mix the following behaviors:

**Preferred for this set piece — hard-lock the shaft during the boss fight.**
- Add a physical, readable shaft hatch/bridge at the upper Gallery floor gap, activated by the same boss-encounter state as the two arena gates.
- Keep the lift and lower tier inaccessible until the Golem is defeated; then open the hatch and restore lift access.
- Ensure the player cannot become trapped below the hatch when the encounter starts: the encounter trigger and hatch transition must be spatially ordered so the player enters the arena before the lock closes, or the lock must have a safe, one-way return behavior.
- The hatch must be part of the environment (visible machinery/gate), not an invisible collision patch.

**Alternative if retreat is intentional — boss leash/reset.**
- Define a clear arena boundary and exit condition.
- On leaving the arena, pause/clear the boss's temporary status effects and reset the encounter (HP, components, phase, attacks, projectiles, gates) to a known state, with player feedback.
- Do not allow the boss to die off-screen from previously applied damage-over-time unless that is an explicitly designed, communicated feature.

The current preferred direction is to treat the lift as a post-Golem progression route and seal it during the encounter. Confirm its staging during the full playthrough before choosing exact hatch coordinates.


## 9. Implemented prototype — boss-controlled shaft hatch (2026-11-18)

A first implementation is now on `first-location-audit` (not merged into `main`):
- Added `PlatformStyle.BOSS_HATCH` as a separate platform style.
- Added a 90 × 20 px Tiled object `golem_shaft_hatch` at `(3360, 430)`, filling the Gallery floor opening between `x=3360` and `x=3450`. Its initial collision state is `OPEN`.
- Added the same platform to `GameLevelFactory` so the fallback geometry stays in parity with the live TMX.
- Extended the existing boss-gate state switch so the hatch becomes `SOLID` when the boss encounter seals the arena, and `OPEN` again when the gates reopen (boss defeated or player-death reset).
- Added distinct Canvas rendering: an open shaft mouth keeps visible side rails; the sealed state draws a riveted flush iron cover. No custom physics path was added; existing platform collision and tile-grid synchronization handle it.

This is a deliberate first pass at the encounter rule, not final level polish. Validation still required:
1. Android CI / unit tests and debug APK build.
2. On-device check that the hatch closes immediately after the Golem trigger, forms a flush walkable surface, and does not trap the player or interfere with the lift.
3. Confirm the hatch opens after Golem defeat and after a player death/reset.
4. Confirm the live TMX and factory fallback parity test passes.

The boss continues to receive status damage while active. The hatch prevents the observed normal retreat route; a future explicit retreat mechanic would need a defined boss-leash/reset rule rather than relying on off-screen damage-over-time.
