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
