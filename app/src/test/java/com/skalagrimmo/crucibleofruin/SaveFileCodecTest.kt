package com.skalagrimmo.crucibleofruin

import com.skalagrimmo.crucibleofruin.game.data.SaveFileCodec
import com.skalagrimmo.crucibleofruin.game.data.UserSave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SaveFileCodecTest {

    @Test
    fun encodeDecodeRoundTripsEveryField() {
        val save = UserSave(
            displayName = "Iron Alchemist",
            highScore = 4200,
            bestTimeSeconds = 187.5f,
            foesSlain = 34,
            lastCheckpoint = 5,
            savedAt = 1725000000000L,
            targetFps = 60,
            soundMuted = true,
            hapticsEnabled = false,
            permanentVitality = 2,
            permanentDamage = 3,
            permanentSpeed = 1,
            permanentFlasks = 4,
            permanentDefense = 5,
            permanentVitriol = 6,
            savedSulfur = 11,
            savedMercury = 22,
            savedVitriol = 33,
            perfectDodges = 47
        )

        val decoded = SaveFileCodec.decode(SaveFileCodec.encode(save))

        assertNotNull(decoded)
        assertEquals(save, decoded)
    }

    @Test
    fun decodeMalformedJsonReturnsNull() {
        assertNull(SaveFileCodec.decode("not json at all {{{"))
    }

    @Test
    fun decodeForeignFormatReturnsNull() {
        val foreign = """ + '"' + """{"format": "someone-elses-save", "version": 1}""" + '"' + """
        assertNull(SaveFileCodec.decode(foreign))
    }

    @Test
    fun decodeNewerFormatVersionReturnsNull() {
        val newer = SaveFileCodec.encode(UserSave())
            .replace("\"version\": 1", "\"version\": 2")
        assertNull(SaveFileCodec.decode(newer))
    }

    @Test
    fun decodeAppliesDefaultsForMissingFields() {
        val minimal = """ + '"' + """{"format": "crucible-of-ruin-save", "version": 1}""" + '"' + """
        val decoded = SaveFileCodec.decode(minimal)

        assertNotNull(decoded)
        assertEquals(UserSave(displayName = "Alchemist", savedAt = 0L), decoded)
    }
}
