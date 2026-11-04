package com.example

import com.example.game.GameEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 6d (#38): the room/door graph is the world map's source of truth.
 *
 * These tests pin the partition (rooms tile the whole world without gaps or
 * overlaps), the graph (doors are bidirectional and every room is reachable),
 * the geometry (every door opens onto standable ground in the tile grid), and
 * the engine's runtime tracking of charted rooms.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomGraphTest {

    @Test
    fun roomsTileTheBandsWithoutGapsOrOverlaps() {
        val engine = GameEngine()
        val rooms = engine.roomGraph.rooms

        // No room may leave the world's bounds.
        assertTrue(
            "Every room must fit inside the world bounds",
            rooms.all {
                it.x >= 0f && it.y >= 0f &&
                    it.x + it.w <= engine.levelWidth && it.y + it.h <= engine.levelHeight
            }
        )
        // No two rooms overlap (rooms are half-open rectangles; a shared edge is fine).
        for (i in rooms.indices) {
            for (j in i + 1 until rooms.size) {
                val a = rooms[i]
                val b = rooms[j]
                assertTrue(
                    "Rooms may not overlap: " + a.id + " / " + b.id,
                    a.x + a.w <= b.x || b.x + b.w <= a.x || a.y + a.h <= b.y || b.y + b.h <= a.y
                )
            }
        }
        // Stage 7b (#51): the world is charted in bands. The corridor band covers
        // the full world width; the Undercroft band stacks below it and tiles its
        // own span without gaps.
        val corridor = rooms.filter { it.y == 0f }.sortedBy { it.x }
        assertTrue("The world must start with a room at x = 0", corridor.first().x == 0f)
        var x = 0f
        for (room in corridor) {
            assertEquals("The corridor band must be contiguous at " + room.id, x, room.x, 0f)
            x += room.w
        }
        assertEquals("The corridor band must close the world at levelWidth", engine.levelWidth, x, 0f)

        val undercroft = rooms.filter { it.y > 0f }
        assertTrue("The Undercroft band must exist below the corridor", undercroft.isNotEmpty())
        assertTrue(
            "The Undercroft band must be one full-height strip below the corridor",
            undercroft.all { it.y == corridor.first().h && it.h == engine.levelHeight - it.y }
        )
        var ux = undercroft.minOf { it.x }
        for (room in undercroft.sortedBy { it.x }) {
            assertEquals("The Undercroft band must be contiguous at " + room.id, ux, room.x, 0f)
            ux += room.w
        }
    }

    @Test
    fun everyRoomIsReachableThroughDoors() {
        val engine = GameEngine()
        val start = engine.roomGraph.roomById("bastion_entry")
        assertNotNull("The graph must declare the start room", start)
        val reachable = engine.roomGraph.reachableFrom(start!!)
        assertEquals(
            "Every room must be walkable from the entry hall",
            engine.roomGraph.rooms.size, reachable.size
        )
    }

    @Test
    fun doorsAreBidirectionalAndSitInsideTheirRoom() {
        val engine = GameEngine()
        for (room in engine.roomGraph.rooms) {
            for (door in room.doors) {
                val target = engine.roomGraph.roomById(door.toRoomId)
                assertNotNull(
                    "Door from " + room.id + " must name an existing room",
                    target
                )
                assertTrue(
                    "The door on " + room.id + " must sit inside the room's bounds",
                    door.x >= room.x && door.x <= room.x + room.w &&
                        door.y >= room.y && door.y <= room.y + room.h
                )
                // Stage 7b (#51): a vertical transition (the Undercroft shaft)
                // anchors both ends in the same world column — the shaft mouth in
                // the upper room, the landing below — so the pair stays aligned
                // on the map's x axis. Level corridor doors keep the same exact
                // spot, which the same-column rule also covers.
                assertTrue(
                    "The door back from " + door.toRoomId + " must align in the same world column",
                    target!!.doors.any { it.toRoomId == room.id && it.x == door.x }
                )
            }
        }
    }

    @Test
    fun everyDoorStandsOnASurfaceInTheGrid() {
        val engine = GameEngine()
        for (room in engine.roomGraph.rooms) {
            for (door in room.doors) {
                assertTrue(
                    "The door at (" + door.x + ", " + door.y + ") must open onto standable ground",
                    engine.levelGrid.hasStandableSurfaceNear(door.x, door.y)
                )
            }
        }
    }

    @Test
    fun engineChartsTheEntryHallFromTheFirstTick() {
        val engine = GameEngine()
        engine.update(1f / 60f)
        assertNotNull("The engine must know its current room after the first tick", engine.currentRoom)
        assertEquals("bastion_entry", engine.currentRoom!!.id)
        assertTrue("The start room counts as visited", engine.visitedRooms.contains("bastion_entry"))
    }

    @Test
    fun crossingBoundariesGrowsTheVisitedSet() {
        val engine = GameEngine()
        // Chart the spawn room first — the run begins in the entry hall.
        engine.update(1f / 60f)
        assertEquals("bastion_entry", engine.currentRoom!!.id)

        // Teleport to the Bastion Ascent (floor 680-1180, top 460).
        engine.player.x = 1000f
        engine.player.y = 416f
        engine.player.vy = 0f
        engine.player.grounded = true
        engine.update(1f / 60f)
        assertEquals("bastion_ascent", engine.currentRoom!!.id)

        // Teleport to the Sanctum Approach (floor 5070-5270, top 430).
        engine.player.x = 5000f
        engine.player.y = 386f
        engine.player.vy = 0f
        engine.player.grounded = true
        engine.update(1f / 60f)
        assertEquals("sanctum_approach", engine.currentRoom!!.id)
        assertEquals("Skipped rooms stay unvisited until walked through", 3, engine.visitedRooms.size)
        assertTrue(
            engine.visitedRooms.containsAll(
                listOf("bastion_entry", "bastion_ascent", "sanctum_approach")
            )
        )
    }

    @Test
    fun roomAtResolvesFactoryLandmarks() {
        val engine = GameEngine()
        assertEquals("bastion_entry", engine.roomGraph.roomAt(80f, 400f)!!.id)
        assertEquals("crucible_labs", engine.roomGraph.roomAt(2500f, 300f)!!.id)
        assertEquals("cultist_tower", engine.roomGraph.roomAt(3000f, 300f)!!.id)
        assertEquals("grand_sanctum", engine.roomGraph.roomAt(5750f, 300f)!!.id)
        // Stage 7b (#51): the Undercroft band charts below the corridor.
        assertEquals("sunken_descent", engine.roomGraph.roomAt(3300f, 900f)!!.id)
        assertEquals("undercroft_halls", engine.roomGraph.roomAt(3700f, 800f)!!.id)
        assertEquals("reliquary_vault", engine.roomGraph.roomAt(4200f, 700f)!!.id)
        assertEquals("Outside the world there is no room", null, engine.roomGraph.roomAt(-10f, 100f))
        assertEquals(null, engine.roomGraph.roomAt(6300f, 100f))
    }

    @Test
    fun checkpointStartsChartTheirRoomImmediately() {
        val engine = GameEngine()
        engine.setStartCheckpoint(2)
        assertEquals("sanctum_approach", engine.currentRoom!!.id)
        assertTrue(engine.visitedRooms.contains("sanctum_approach"))
    }

    @Test
    fun restartResetsTheMapButKeepsTheGraph() {
        val engine = GameEngine()
        // Chart the entry hall at spawn, then push the map forward by teleport.
        engine.update(1f / 60f)
        engine.player.x = 5000f
        engine.player.y = 386f
        engine.player.vy = 0f
        engine.player.grounded = true
        engine.update(1f / 60f)
        assertEquals(2, engine.visitedRooms.size)

        engine.restart()
        assertEquals(
            "Restart re-charts only the checkpoint room",
            setOf("bastion_entry"), engine.visitedRooms.toSet()
        )
        assertEquals("bastion_entry", engine.currentRoom!!.id)
        assertEquals("The room graph itself survives restarts", 12, engine.roomGraph.rooms.size)
    }
}
