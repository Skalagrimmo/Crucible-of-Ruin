package com.skalagrimmo.crucibleofruin

import com.skalagrimmo.crucibleofruin.game.EnemyArchetype
import com.skalagrimmo.crucibleofruin.game.GameEngine
import com.skalagrimmo.crucibleofruin.game.Hazard
import com.skalagrimmo.crucibleofruin.game.HazardType
import com.skalagrimmo.crucibleofruin.game.Platform
import com.skalagrimmo.crucibleofruin.game.PlatformCollision
import com.skalagrimmo.crucibleofruin.game.PlatformStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterization tests for the current hard-coded world layout.
 *
 * These checks protect representative landmarks and object counts before level
 * construction is moved out of GameEngine.initLevel().
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GameLevelLayoutTest {

    @Test
    fun initialWorldContainsExpectedLandmarksAcrossSections() {
        val engine = GameEngine()

        assertTrue(
            "Starting floor should remain at the start of section one",
            engine.platforms.contains(Platform(0f, 460f, 600f, 80f, PlatformStyle.STONE))
        )
        assertTrue(
            "The first platform gap should retain its acid hazard",
            engine.hazards.contains(Hazard(600f, 500f, 80f, 40f, HazardType.ACID))
        )
        assertTrue(
            "Section two should remain present",
            engine.platforms.contains(Platform(1920f, 420f, 380f, 120f, PlatformStyle.STONE))
        )
        assertTrue(
            "Section three should keep its split approach floor (Stage 8b, #56)",
            engine.platforms.contains(Platform(4360f, 420f, 140f, 120f, PlatformStyle.STONE)) &&
                engine.platforms.contains(Platform(4600f, 420f, 80f, 120f, PlatformStyle.STONE))
        )
        assertTrue(
            "Boss arena gates should remain part of the level geometry, open before the fight",
            engine.platforms.contains(Platform(5270f, 100f, 30f, 350f, PlatformStyle.BOSS_GATE, PlatformCollision.OPEN))
        )
        assertTrue(
            "The final approach should still contain its golem encounter",
            engine.enemies.any { it.x == 5120f && it.type == EnemyArchetype.GOLEM }
        )
        assertTrue(
            "The bastion elevator should be a moving platform over the first acid pit",
            engine.platforms.any { it.motion != null && it.x == 610f && it.y == 330f }
        )
        assertTrue(
            "The laboratory ferry should be a moving platform near the cultist tower",
            engine.platforms.any { it.motion != null && it.x == 2800f && it.y == 250f }
        )
    }

    @Test
    fun initialWorldObjectCountsRemainStable() {
        val engine = GameEngine()

        // Stage 6c (#38): the bastion elevator and the laboratory ferry are
        // moving platforms on top of the 28 static corridor ones.
        // Stage 7b (#51): the gallery floor splits in two around the shaft, and
        // the Undercroft adds three floors, the shaft lift, a four-surface
        // girder climb, and a vault perch.
        // Stage 9c (#62): the Foundry Yard arena adds two boss gates and a
        // health urn, and clears the yard's regular garrison for the golem.
        // Stage 11a (#77): three anti-skip barriers add 8 platforms (3 walls
        // plus 5 ledges), 2 cherub guards, and 3 wall-cap urns.
        assertEquals("Unexpected number of platforms", 65, engine.platforms.size)
        assertEquals("Unexpected number of hazards", 13, engine.hazards.size)
        assertEquals("Unexpected number of enemies", 20, engine.enemies.size)
        assertEquals("Unexpected number of breakable urns", 17, engine.breakables.size)
        // Stage 6d (#38) + Stage 7b (#51) + Stage 8b (#56): nine corridor
        // rooms plus three Undercroft rooms plus three Iron Garrison rooms.
        assertEquals("Unexpected number of rooms", 15, engine.roomGraph.rooms.size)
    }

    /**
     * Stage 7b (#51): the Undercroft — a vertical district below the Gallery
     * of the Golem, reached through a shaft in the gallery floor and served
     * by a lift riding back up to the corridor.
     */
    @Test
    fun theUndercroftDistrictHangsBelowTheGallery() {
        val engine = GameEngine()

        assertTrue(
            "The gallery floor must split around the Undercroft shaft",
            engine.platforms.contains(Platform(3100f, 430f, 260f, 110f, PlatformStyle.STONE)) &&
                engine.platforms.contains(Platform(3450f, 430f, 300f, 110f, PlatformStyle.STONE))
        )
        assertTrue(
            "No corridor geometry may block the shaft drop (x 3360-3450)",
            engine.platforms.none {
                it.y < 540f && it.y + it.h > 430f &&
                    it.x < 3450f && it.x + it.w > 3360f &&
                    it.collision != PlatformCollision.OPEN
            }
        )
        assertTrue(
            "The Undercroft Halls floor must exist one corridor below the gallery",
            engine.platforms.contains(Platform(3500f, 1000f, 500f, 80f, PlatformStyle.STONE))
        )
        assertTrue(
            "The shaft lift must ride the shaft upward from its base",
            engine.platforms.any { it.motion != null && it.x == 3370f && it.y == 960f && it.motion!!.dy < 0f }
        )
        assertTrue(
            "The Reliquary Vault should contain its golem guardian",
            engine.enemies.any { it.x == 4200f && it.type == EnemyArchetype.GOLEM }
        )
        assertTrue(
            "Checkpoint 4 must stand on the Undercroft Halls floor",
            engine.checkpoints.any { it.id == 3 && it.x == 3560f && it.y == 956f }
        )
    }

    /**
     * Stage 8b (#56): the Iron Garrison — the fortress district below the
     * Sanctum Approach, reached through a shaft in the approach floor and
     * sealed behind an iron keep gate that only the elevated route opens.
     */
    @Test
    fun theIronGarrisonHangsBelowTheApproach() {
        val engine = GameEngine()

        assertTrue(
            "The approach floor must split around the garrison shaft",
            engine.platforms.contains(Platform(4360f, 420f, 140f, 120f, PlatformStyle.STONE)) &&
                engine.platforms.contains(Platform(4600f, 420f, 80f, 120f, PlatformStyle.STONE))
        )
        assertTrue(
            "No corridor geometry may block the shaft drop onto the stair (x 4500-4600)",
            engine.platforms.none {
                it.y < 520f && it.y + it.h > 430f &&
                    it.x < 4600f && it.x + it.w > 4500f &&
                    it.collision != PlatformCollision.OPEN
            }
        )
        assertTrue(
            "The district street must bridge the vault into the keep",
            engine.platforms.contains(Platform(4360f, 1000f, 1840f, 80f, PlatformStyle.STONE))
        )
        assertTrue(
            "The keep gate must start sealed",
            engine.platforms.any {
                it.style == PlatformStyle.IRON_GATE &&
                    it.gateId == "garrison_gate" &&
                    it.collision == PlatformCollision.SOLID
            }
        )
        assertTrue(
            "Checkpoint 5 must stand on the keep's doorstep",
            engine.checkpoints.any { it.id == 4 && it.x == 5850f && it.y == 956f }
        )
    }
}
