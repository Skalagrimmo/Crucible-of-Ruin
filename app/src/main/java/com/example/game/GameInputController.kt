package com.example.game

/**
 * Small adapter between Compose touch callbacks and the engine's input API.
 *
 * It intentionally preserves the existing semantics: directional/jump buttons
 * update held flags, a jump press also fills the engine's jump buffer, and action
 * taps call the same engine methods previously invoked directly by GameScreen.
 */
internal class GameInputController(private val engine: GameEngine) {

    fun setLeftHeld(isHeld: Boolean) {
        engine.inputLeft = isHeld
    }

    fun setRightHeld(isHeld: Boolean) {
        engine.inputRight = isHeld
    }

    fun setJumpHeld(isHeld: Boolean) {
        engine.inputJump = isHeld
        if (isHeld) engine.onJumpPressed()
    }

    fun tapFlask() {
        engine.onSubPressed()
    }

    fun tapDash() {
        engine.onDashPressed()
    }

    fun tapAttack() {
        engine.onAttackPressed()
    }
}
