# Level Design Audit — Crucible of Ruin

Stage 11a (#77). The audit behind the first location's deepening pass:
what each room is for, where the world could be skipped, what changes in
this stage, and the gating/pacing plan the later sub-stages build on.

## Reference frames

- **Dead Cells (Motion Twin, hybrid level design):** a fixed hand-made
  frame (world map, district connections, key locations) filled with
  purpose-built rooms — a combat arena is not a treasure room, and neither
  is a rest alcove. Pacing alternates pressure peaks with quiet breaks.
- **Classic metroidvania gating:** ability/key gates sit at the fork
  leading to an area, never at a dead end; zone order is driven by gates,
  not distance; backtracking loops are deliberate.
- **Dead Cells on mobile (Playdigious):** short session loops, checkpoints
  within reach, controls-as-UI. Sub-stage 11c borrows the rhythm.

## World anatomy

The world is 6200x1080: an upper corridor band (y 0..540) running
Bastion Entry -> Grand Sanctum, and a lower district band (y 540..1080)
holding the Undercroft (x 3100..4360) and the Iron Garrison
(x 4360..6200). Two boss arenas seal themselves: the Foundry Yard
(Siege Golem) and the Grand Sanctum (Chimera). One shortcut gate
(garrison_gate) opens only from the elevated rooftop route.

## Room inventory

| Room | Purpose | Threats | Rewards | Gating |
| --- | --- | --- | --- | --- |
| bastion_entry | tutorial ground + optional high route | lurker, cherub | health urn | open |
| bastion_ascent | girder ladder over the acid; 11a barrier | lurker, cherub | flask (wall cap) | 11a wall |
| bastion_ramparts | 11a climb into the labs | lurker, cherub guard | health urn (wall cap) | 11a wall |
| crucible_labs | acid crossing + ferry ride | cultist | — | open |
| cultist_tower | vertical choke | cultist | — | open |
| golem_gallery | mid-boss arena approach + shaft down | Siege Golem | health/flask urns | boss gate |
| vitriol_pools | 11a climb over the acid | cherub guard, cultist | flask (wall cap) | 11a wall |
| sanctum_approach | shaft down to the garrison; ledge hops | cherub, golem | health urn | open |
| grand_sanctum | final arena | Chimera | Stage Cleared | boss gate |
| sunken_descent | district landing | lurker, spikes | flask urn | shaft |
| undercroft_halls | zigzag climb | cherub, cultist, acid | health urn | open |
| reliquary_vault | district treasure room | golem, spikes | 2 urns | open |
| garrison_descent | street access | lurker | health urn | shaft |
| garrison_street | rooftop route + pockets | cherubs, cultist, golem, acid/spikes | 3 urns | keep gate (trigger) |
| garrison_keep | keep pocket + elevated door | golem, spikes | flask urn | gate |

## The skip problem (pre-11a)

The corridor's ground line was a free run: from the spawn to each boss
gate a player holding right only had to hop the girder/ledge hops already
on the path — every ground fight was avoidable, and nothing made the run
long. Three barriers now break it (Stage 11a):

1. **Ascent barrier (x 1140)** — ends the ground run through the Bastion
   Ascent; the girder ladder (800 -> 1000) plus a jump onto the wall cap
   is the only way over, through the lurker and the cherub holding the
   climb.
2. **Ramparts barrier (x 1880)** — the ramparts/laboratories boundary is
   a solid wall; the one-way ledge ladder on its left face is the way
   over, a cherub haunts the climb, and the right-face ledge keeps
   backtracking open.
3. **Vitriol barrier (x 3960)** — the acid crossing stops being a hop: a
   ledge and a wall cap force a climb under the added cherub's patrol
   before the jump over the acid to the far floor.

Every barrier satisfies the reachability ruler (rise <= 119 px, gap <=
152 px) on both faces, so the route stays base-stats legal in both
directions, and LevelReachabilityTest passes unchanged.

## Forward plan

- **11b — keys + locked doors:** Tiled key pickups and keyed door objects
  wired through the loader into the engine; the first keyed door seals
  the vitriol fork (the last un-gated room boundary on the main line),
  the key sits in the Undercroft's reliquary vault — forcing the district
  loop.
- **11c — pacing:** checkpoint density pass (the mid-corridor stretch
  between the gallery and the approach runs ~1900 px with no checkpoint),
  short-loop structure, detour rewards.
- **11d — combat balance:** flyer pressure (cherub patrol bands vs the
  new climbs), status-effect tuning, boss numbers from device playtests.
