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
    fun roomsPartitionTheWholeWorldIntoContiguousSlices() {
        val engine = GameEngine()
        val rooms = engine.roomGraph.rooms.sortedBy { it.x }
        assertTrue("The world must start with a room at x = 0", rooms.first().x == 0f)
        assertTrue(
            "Every room spans the full world height",
            rooms.all { it.y == 0f && it.y + it.h == engine.levelHeight }
        )
        for (i in 0 until rooms.size - 1) {
            assertEquals(
                "Rooms must tile the world without gaps or overlaps: " + rooms[i].id,
                rooms[i].x + rooms[i].w, rooms[i + 1].x, 0f
            )
        }
        assertEquals(
            "The last room must close the world at levelWidth",
            engine.levelWidth, rooms.last().x + rooms.last().w, 0f
        )
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
                assertTrue(
                    "The door back from " + door.toRoomId + " must exist at the same spot",
                    target!!.doors.any { it.toRoomId == room.id && it.x == door.x && it.y == door.y }
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
        engine.player.x = 5000f
        engine.player.y = 386f
        engine.update(1f / 60f)
        assertEquals(3, engine.visitedRooms.size)

        engine.restart()
        assertEquals(
            "Restart re-charts only the checkpoint room",
            setOf("bastion_entry"), engine.visitedRooms.toSet()
        )
        assertEquals("bastion_entry", engine.currentRoom!!.id)
        assertEquals("The room graph itself survives restarts", 9, engine.roomGraph.rooms.size)
    }
}
