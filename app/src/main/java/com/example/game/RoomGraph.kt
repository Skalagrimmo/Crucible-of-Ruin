package com.example.game

/**
 * Stage 6d (#38): the level charted as a room/door graph.
 *
 * The strategic decision behind #38 replaced a bare linear corridor with a
 * room graph: rooms are the map's nodes, doors are its edges, and the world
 * map renders exactly this data with a fog of war over unvisited rooms. The
 * first level is still a side-scroller, so its rooms are full-height slices
 * of one corridor — but the model is general: rooms are arbitrary
 * world-space rectangles and doors are arbitrary transition points, so
 * future content can branch, stack, or loop without touching the map screen.
 */

/** A walkable transition between two rooms, anchored to a world position. */
data class RoomDoor(
    val x: Float,
    val y: Float,
    val toRoomId: String
)

/** One charted room: a world-space rectangle plus the doors leading out of it. */
data class LevelRoom(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val doors: List<RoomDoor> = emptyList()
) {
    fun contains(px: Float, py: Float): Boolean =
        px >= x && px < x + w && py >= y && py < y + h
}

/**
 * The static room/door graph of a level. Room lookup is a linear scan over
 * the handful of rooms — deterministic and allocation-free.
 */
class RoomGraph(val rooms: List<LevelRoom>) {

    /** The room containing a world point, or null outside the charted world. */
    fun roomAt(px: Float, py: Float): LevelRoom? =
        rooms.firstOrNull { it.contains(px, py) }

    fun roomById(id: String): LevelRoom? = rooms.firstOrNull { it.id == id }

    /** Rooms directly reachable from [room] through its doors. */
    fun neighborsOf(room: LevelRoom): List<LevelRoom> =
        room.doors.mapNotNull { roomById(it.toRoomId) }

    /** Every room reachable from [start] by walking doors, [start] included. */
    fun reachableFrom(start: LevelRoom): Set<LevelRoom> {
        val visited = LinkedHashSet<LevelRoom>()
        val queue = ArrayDeque<LevelRoom>()
        visited.add(start)
        queue.add(start)
        while (queue.isNotEmpty()) {
            for (neighbor in neighborsOf(queue.removeFirst())) {
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }
        return visited
    }
}
