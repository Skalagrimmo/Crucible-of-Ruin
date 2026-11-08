package com.skalagrimmo.crucibleofruin.game

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Stage 6b (#38): the level's collision world on a uniform tile grid.
 *
 * Every platform and hazard rectangle is rasterized into 10 px cells, each
 * carrying a collision attribute (empty / solid / one-way), an optional hazard
 * attribute, and the index of the platform that painted it. Collision queries
 * that used to scan the platform list — wall pushes, landings, ledge probes,
 * projectile hits — become O(1) cell lookups.
 *
 * Rasterization snaps rectangles outward to whole cells. The factory layout is
 * fully 10 px aligned (the two arena girders were rounded to 20 px thick for
 * exactly this reason), so cells and source rectangles coincide in production;
 * the outward snap only widens the unaligned rectangles that unit tests install,
 * and it never moves a surface top — the only thing the resolvers read. OPEN
 * platforms (the boss gates before the fight) rasterize as empty cells; sealing
 * the gates re-rasterizes just those cells.
 */
internal class LevelGrid(val widthPx: Float, val heightPx: Float) {

    /** World cell size in pixels. All factory geometry is a multiple of this. */
    val tile = 10f
    val cols = floor(widthPx / tile).toInt()
    val rows = floor(heightPx / tile).toInt()

    // Per-cell attributes: collision 0 empty / 1 solid / 2 one-way, hazard
    // 0 none / 1 acid / 2 spikes, plus the source-platform index (-1 = none).
    private val cellCollision = ByteArray(cols * rows)
    private val cellHazard = ByteArray(cols * rows)
    private val cellPlatform = IntArray(cols * rows) { -1 }

    /** Cells each platform index painted, so a re-sync clears only those cells. */
    private val paintedCells = HashMap<Int, ArrayList<Int>>()

    /** The platform list the grid was built from; landing hooks receive these exact instances. */
    private val sourcePlatforms = ArrayList<Platform>()

    /** Rasterizes [platforms] and [hazards], replacing all previous cell state. */
    fun buildFrom(platforms: List<Platform>, hazards: List<Hazard>) {
        cellCollision.fill(0)
        cellHazard.fill(0)
        cellPlatform.fill(-1)
        paintedCells.clear()
        sourcePlatforms.clear()
        sourcePlatforms.addAll(platforms)
        for (index in platforms.indices) {
            rasterizePlatform(platforms[index], index)
        }
        for (hazard in hazards) {
            rasterizeHazard(hazard)
        }
    }

    /**
     * Re-rasterizes a single replaced entry (boss-gate sealing). Platform is
     * immutable, so callers swap the list entry and hand the new instance here
     * together with its index.
     */
    fun syncPlatform(platform: Platform, index: Int) {
        // Stage 6c (#38): clearing walks only the cells this index painted, so a
        // moving platform re-rasterizes in O(its own cells) instead of a
        // full-world scan. Cells another platform overwrote keep their owner —
        // the cellPlatform ownership check skips them.
        val stale = paintedCells.remove(index)
        if (stale != null) {
            for (i in stale) {
                if (cellPlatform[i] == index) {
                    cellPlatform[i] = -1
                    cellCollision[i] = 0
                }
            }
        }
        while (sourcePlatforms.size <= index) sourcePlatforms.add(platform)
        sourcePlatforms[index] = platform
        rasterizePlatform(platform, index)
    }

    private fun rasterizePlatform(platform: Platform, index: Int) {
        val collision: Byte = when (platform.collision) {
            PlatformCollision.SOLID -> 1
            PlatformCollision.ONE_WAY -> 2
            // OPEN is a purely visual state — it occupies no collision cells.
            PlatformCollision.OPEN -> return
        }
        val painted = ArrayList<Int>()
        rasterizeRect(platform.x, platform.y, platform.w, platform.h) { col, row ->
            val i = row * cols + col
            cellCollision[i] = collision
            cellPlatform[i] = index
            painted.add(i)
        }
        paintedCells[index] = painted
    }

    private fun rasterizeHazard(hazard: Hazard) {
        val attribute: Byte = when (hazard.type) {
            HazardType.ACID -> 1
            HazardType.SPIKES -> 2
        }
        rasterizeRect(hazard.x, hazard.y, hazard.w, hazard.h) { col, row ->
            cellHazard[row * cols + col] = attribute
        }
    }

    /** Covers every cell the rectangle touches, snapping outward to whole cells. */
    private inline fun rasterizeRect(
        x: Float, y: Float, w: Float, h: Float,
        visit: (col: Int, row: Int) -> Unit
    ) {
        val colFrom = floor(x / tile).toInt()
        val colTo = ceil((x + w) / tile).toInt() - 1
        val rowFrom = floor(y / tile).toInt()
        val rowTo = ceil((y + h) / tile).toInt() - 1
        var row = rowFrom
        while (row <= rowTo) {
            if (row in 0 until rows) {
                var col = colFrom
                while (col <= colTo) {
                    if (col in 0 until cols) visit(col, row)
                    col++
                }
            }
            row++
        }
    }

    // --- Cell queries -----------------------------------------------------

    /** Collision attribute of a cell: 0 empty, 1 solid, 2 one-way. Out of bounds reads empty. */
    fun collisionCell(col: Int, row: Int): Int {
        if (col < 0 || col >= cols || row < 0 || row >= rows) return 0
        return cellCollision[row * cols + col].toInt()
    }

    fun solidIn(col: Int, row: Int): Boolean = collisionCell(col, row) == 1

    /** The exact platform instance that painted a cell, or null when there is none. */
    fun platformIn(col: Int, row: Int): Platform? {
        if (col < 0 || col >= cols || row < 0 || row >= rows) return null
        return sourcePlatforms.getOrNull(cellPlatform[row * cols + col])
    }

    /** Collision attribute of the cell containing the world point: 0 empty, 1 solid, 2 one-way. */
    fun collisionAt(px: Float, py: Float): Int =
        collisionCell(floor(px / tile).toInt(), floor(py / tile).toInt())

    /** Hazard attribute of the cell containing the world point: 0 none, 1 acid, 2 spikes. */
    fun hazardAt(px: Float, py: Float): Int {
        val col = floor(px / tile).toInt()
        val row = floor(py / tile).toInt()
        if (col < 0 || col >= cols || row < 0 || row >= rows) return 0
        return cellHazard[row * cols + col].toInt()
    }

    /**
     * True when any non-empty cell overlaps the rectangle. Mirrors the legacy
     * strict-aabb scan over the platform list (OPEN platforms were skipped).
     */
    fun rectTouchesCollision(x: Float, y: Float, w: Float, h: Float): Boolean {
        val colFrom = floor(x / tile).toInt()
        val colTo = ceil((x + w) / tile).toInt() - 1
        val rowFrom = floor(y / tile).toInt()
        val rowTo = ceil((y + h) / tile).toInt() - 1
        var row = rowFrom
        while (row <= rowTo) {
            var col = colFrom
            while (col <= colTo) {
                if (collisionCell(col, row) != 0) return true
                col++
            }
            row++
        }
        return false
    }

    /**
     * Stage 4 (#36) ledge probe against the grid: is there a standable surface
     * top within 2 px of the feet at probe x? Mirrors the legacy list scan
     * "collision != OPEN && probeX in [x, x + w] && |top - feetY| <= 2":
     * a cell counts only as a surface top (occupied with empty above), and the
     * probe keeps the legacy inclusive right edge — a probe exactly on a cell
     * boundary also examines the cell to its left.
     */
    fun hasStandableSurfaceNear(px: Float, feetY: Float): Boolean {
        var colFrom = floor(px / tile).toInt()
        val colTo = colFrom
        if (px % tile == 0f && colFrom > 0) colFrom -= 1
        var row = floor((feetY - 2f) / tile).toInt()
        if (row < 0) row = 0
        while (row * tile < feetY - 2f) row++
        val lastRow = floor((feetY + 2f) / tile).toInt()
        while (row <= lastRow) {
            var col = colFrom
            while (col <= colTo) {
                if (collisionCell(col, row) != 0 && collisionCell(col, row - 1) == 0) return true
                col++
            }
            row++
        }
        return false
    }
}
