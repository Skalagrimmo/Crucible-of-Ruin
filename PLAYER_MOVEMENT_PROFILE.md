# Player Movement Profile

This is the movement contract for the current side-scrolling character controller. Values are expressed in the game's 960x540 world-space pixels and are intentionally independent of display resolution.

## Current baseline

| Property | Value |
|---|---:|
| Player collision box | 26 x 44 px |
| Ground run speed | 190 px/s |
| Air speed target | 160 px/s |
| Jump impulse | -500 px/s |
| Gravity | 1050 px/s² |
| Maximum fall speed | 750 px/s |
| Jump buffer | 0.15 s |
| Coyote time after landing | 0.12 s |
| Variable-jump release gravity | +700 px/s² while rising faster than 120 px/s |

Releasing jump early increases upward-phase gravity. This is intentional: a short press produces a lower hop, while holding jump produces the full arc.

## Practical jump envelope

The continuous, full-hold estimate is:

- Maximum rise: approximately 119 px, from (v^2 / 2g).
- Same-height airtime: approximately 0.95 s, from (2v / g).

The actual game updates in discrete frames. At the default 30 FPS, a frame-by-frame estimate from a standing start, holding right while jumping, is closer to:

| Jump input | Peak rise | Horizontal travel to same height | Travel when landing 70 px higher |
|---|---:|---:|---:|
| One-frame tap | about 72 px | about 102 px | about 54 px |
| Sustained hold | about 111 px | about 139 px | about 112 px |

These are planning estimates, not guarantees. A run-up, air-control timing, alchemy speed modifiers, and the exact frame at which jump is released change the result. Treat 111 px as the practical baseline ceiling at 30 FPS, not as a normal design target.

## Level-design rules

1. Build required routes around repeatable jumps, not the theoretical maximum. A rise of 48-72 px is a good normal step; 80-96 px is a demanding jump; anything above roughly 100 px should be optional or require a defined ability / launch surface.
2. For a 70 px rise, keep the horizontal edge-to-edge gap around 80 px or less for a sustained jump. A quick tap only covers roughly 54 px in the current baseline.
3. On same-height landings, use about 100 px as a comfortable gap target. Larger gaps should have a run-up, a lower intermediate surface, or an explicit movement ability.
4. Keep surface heights and widths on a consistent authoring grid when laying out new areas. A 16 px grid is a useful art/layout convention; it is not a collision requirement and existing world coordinates do not need to be shifted merely to imitate GBA tile dimensions.
5. Use `ONE_WAY` for ledges and girders intended to be jumped through from below. Use `SOLID` for floors, walls, pillars, and gates.
6. Every required vertical route should be checked as a sequence of reachable surfaces. A platform that can be reached only with the exact peak-height jump is too fragile for a required route.

## Current level audit notes

- The first Ruined Bastion stair-step ledges rise by 70 px at a time, with 30-40 px horizontal gaps. Those dimensions are within the current normal jump envelope once the ledges behave as one-way surfaces.
- The boss-arena girders have their tops at y=330 while the arena floor is y=450: a 120 px rise. That exceeds the practical unbuffed 30 FPS jump ceiling, and the two girders are separated by a 340 px edge gap. Treat them as encounter/decorative geometry for now, not as a required traversal route. If they are meant to be reachable, they need an intermediate launch surface or a deliberate ability.

## Implementation decisions

- Collision resolution remains axis-separated: X first, then Y. A landing snaps the player's feet to the exact platform top and sets `grounded` in that same vertical-resolution pass.
- `Float` remains the internal position type. At this level's 6,200 px world width, its subpixel precision is ample; exact collision snapping addresses edge drift without quantizing every movement step. Fixed-point can be reconsidered only if deterministic replays or network lockstep become requirements.
- The game remains a continuous side-scroller. AoS-style room transitions are an optional level/camera architecture, not a prerequisite for reliable character physics and not a change to introduce during movement tuning.
