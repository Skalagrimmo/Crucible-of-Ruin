package com.example.game

/**
 * Small adapter between Compose touch callbacks and the engine's input API.
 *
 * It intentionally preserves the existing semantics: directional/jump buttons
 * update held flags, a jump press also fills the engine's jump buffer, and action
 * taps call the same engine methods previously invoked directly by GameScreen.
 */
internal class GameInputController(private val engine: GameEngine) {

    private fun canAcceptGameplayInput(): Boolean = engine.state == "playing"

    fun setLeftHeld(isHeld: Boolean) {
        engine.inputLeft = isHeld && canAcceptGameplayInput()
    }

    fun setRightHeld(isHeld: Boolean) {
        engine.inputRight = isHeld && canAcceptGameplayInput()
    }

    fun setJumpHeld(isHeld: Boolean) {
        val acceptInput = canAcceptGameplayInput()
        engine.inputJump = isHeld && acceptInput
        if (isHeld && acceptInput) engine.onJumpPressed()
    }

    /**
     * Cancel pointer-driven state when pausing or leaving gameplay.
     * A buffered jump is also cleared so it cannot unexpectedly fire on resume.
     */
    fun cancelHeldInputs() {
        engine.inputLeft = false
        engine.inputRight = false
        engine.inputJump = false
        engine.player.jumpBuffer = 0f
    }

    fun tapFlask() {
        if (canAcceptGameplayInput()) engine.onSubPressed()
    }

    fun tapDash() {
        if (canAcceptGameplayInput()) engine.onDashPressed()
    }

    fun tapAttack() {
        if (canAcceptGameplayInput()) engine.onAttackPressed()
    }
}
