# Implementation Plan: Expanded Action Platformer Combat System with 3-Hit Melee Combo, Knockback & Floating Combat Numbers

Expand the real-time action combat system in *Crucible of Ruin* with a fluid 3-hit melee combo sequence, directional knockback and body collision physics, floating combat text (FCT) with critical strike indicators, enemy and boss poise break mechanics, and reactive health state management in Jetpack Compose.

---

## User Review Required

> [!IMPORTANT]
> **Combat Timing & Controls**: The 3-hit combo sequence will map to the existing primary Attack button (⚔️). Rapidly tapping Attack executes Slash 1 &rarr; Slash 2 &rarr; Slash 3 (Crucible Heavy Finisher) within a generous 0.65-second combo buffer window.

- **Combo Sequence Scaling**:
  - **Hit 1 (Light Quick Jab)**: 0.22s duration, 22 base damage, low knockback (vx &plusmn;90f), cyan particle arc (`#00E5FF`).
  - **Hit 2 (Horizontal Cleave)**: 0.26s duration, 30 base damage, medium knockback (vx &plusmn;150f), forward step surge, amber particle arc (`#FFB300`).
  - **Hit 3 (Crucible Heavy Finisher)**: 0.35s duration, 52 base damage (&times;1.75 multiplier), heavy directional knockback (vx &plusmn;260f, vy -180f), screen shake (9.0f), ground impact crater ring, and triple poise damage (`#FF2040`).

---

## Gaps & Blind Spots

- **Hitbox Overlap & Multi-Hit Deduplication**: Each attack in the combo must generate a unique monotonic `attackId` to ensure an enemy or boss only takes damage once per swing, even if their collider remains within the active hitbox across multiple frames.
- **Airborne vs Grounded Combos**: The combo step should be maintainable mid-air or seamlessly transition to a downward plunging slash if jumping.
- **Floating Combat Text Performance**: Floating damage numbers must use a pre-allocated object pool (`DamageNumberPool`) with zero runtime garbage collection pressure to maintain locked 60/30 FPS gameplay.
- **Enemy Stagger State**: When regular enemies' poise drops to zero from repeated strikes or combo finishers, they should enter a brief Stunned state (0.8s) displaying dazed spiral particles, interrupting their attacks.

---

## Section 1: Functional Logic & Core Architecture

### 1.1 Data Models & Pools (`GameModels.kt`)
- **`DamageNumber` & `DamageNumberPool`**:
  ```kotlin
  data class DamageNumber(
      var active: Boolean = false,
      var x: Float = 0f,
      var y: Float = 0f,
      var text: String = "",
      var color: Color = Color.White,
      var scale: Float = 1.0f,
      var isCrit: Boolean = false,
      var life: Float = 0f,
      var maxLife: Float = 0.65f,
      var vx: Float = 0f,
      var vy: Float = -60f
  )
  ```
  Pre-allocated pool of 32 reusable floating damage numbers rendered directly in world space by `GameRenderer.kt`.
- **Player Combat Combo State**:
  - `comboIndex: Int` (0 = ready/Hit 1, 1 = Hit 2, 2 = Hit 3 Finisher)
  - `comboResetTimer: Float` (counts down from 0.65s; on expiry, resets `comboIndex` to 0)
  - `comboFinisherLunge: Float` (brief burst of horizontal impulse on Hit 2 and Hit 3)

### 1.2 Enemy Poise & Collision Physics (`GameEngine.kt`)
- **Enemy Poise**:
  - Add `poise: Float` and `maxPoise: Float` to `EnemyEntity` (e.g., Skeleton: 30f, Cherub: 20f, Golem: 80f).
  - Normal hits deal 12–15 poise damage; combo finishers deal 40 poise damage.
  - When `poise <= 0f`, enemy is `staggered = true`, resetting after 0.8s with a daze visual.
- **Player & Enemy Body Collision**:
  - If player body overlaps enemy collider, apply contact damage and outward knockback unless player is dashing (i-frames active) or the enemy is staggered.

### 1.3 Combo Input & Hit Processing (`GameEngine.kt`)
- Update `onAttackPressed()`:
  - Check if player can attack (not stunned, not dead).
  - Advance `comboIndex` if within the `comboResetTimer` window; otherwise start at 0.
  - Set attack duration and hitbox active frames tailored to each combo stage.
  - Trigger camera shake: Hit 1 = 3f, Hit 2 = 5f, Hit 3 = 9f.
  - Spawn floating damage numbers at impact location with jitter offset.

---

## Section 2: UI/UX & Design Guidelines Alignment

### 2.1 On-Screen Combo Meter & Feedback (`GameScreen.kt`)
- **Dynamic Combo Pip Indicator**:
  - Positioned adjacent to the Attack touch button:
    - 3 gothic diamond pips representing Combo Stage 1, 2, and 3.
    - Active combo pip glows brightly with matching stage color (Cyan &rarr; Amber &rarr; Crimson).
    - A miniature circular/linear expiration timer bar displaying remaining window to chain into the finisher.
- **Poise Break & Critical Hit Banners**:
  - When an enemy or boss weak-point poise breaks:
    - Spawn a bold golden floating text `"POISE BREAK!"` or `"CRITICAL!"` with an explosive particle burst.

### 2.2 Floating Combat Text Renderer (`GameRenderer.kt`)
- Render floating damage numbers in world space:
  - Text scales up during the first 0.1s and drifts upwards with gravitational arc.
  - Normal damage in crisp yellow-white (`#FFF275`).
  - Critical hits / weak points in large golden amber (`#FFD700`).
  - Finisher strikes in fiery vermilion (`#FF3B30`).
  - Alchemy DoT ticks in corrosive neon green or burning pyre orange.

---

## Section 3: Verification & Test Plan

### Automated Testing (`BossUnitTest.kt` & Unit Tests)
- `testPlayerComboProgressionAndReset()`:
  - Verify `onAttackPressed()` advances combo from Hit 1 &rarr; Hit 2 &rarr; Hit 3.
  - Verify combo resets to Hit 1 if `comboResetTimer` expires.
  - Verify damage multipliers and active hitbox timings for each combo step.
- `testEnemyKnockbackAndPoiseBreak()`:
  - Verify Hit 1, 2, and 3 impart increasing horizontal and vertical knockback velocities.
  - Verify enemy enters staggered state when poise drops to zero.
- `testFloatingDamageNumberPoolLifecycle()`:
  - Verify damage numbers spawn, update lifetime, drift upwards, and recycle back to the pool without memory allocations.
- Verification command: `gradle :app:testDebugUnitTest`
- Compilation check: `compile_applet`
