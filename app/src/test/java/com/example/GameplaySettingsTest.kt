package com.example

import com.example.game.nextTargetFps
import org.junit.Assert.assertEquals
import org.junit.Test

class GameplaySettingsTest {

    @Test
    fun pauseFrameRateToggleSwitchesBetweenThirtyAndSixtyFps() {
        assertEquals(60, nextTargetFps(30))
        assertEquals(30, nextTargetFps(60))
    }
}
