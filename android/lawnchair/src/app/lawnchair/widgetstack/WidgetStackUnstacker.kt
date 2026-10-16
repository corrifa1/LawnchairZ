/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import android.content.ContentValues
import android.content.Context
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.Utilities
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.GridOccupancy
import com.android.launcher3.util.IntArray
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * "Unstack all": turns every stack back into separate widgets. Each stack's visible widget keeps
 * the stack's cells; the others go to the first free cells, on the stack's page first, then on
 * later pages, then on new pages. Nothing is deleted.
 */
object WidgetStackUnstacker {

    /** A row on the workspace grid. */
    data class Cells(val id: Int, val screen: Int, val x: Int, val y: Int, val spanX: Int, val spanY: Int)

    /** A stack row with its children's ids in rank order. */
    data class Stack(val cells: Cells, val activeRank: Int, val childIds: List<Int>)

    /**
     * Plans where every child goes. [others] are the non-stack items on the workspace. Returns the
     * new cells of every child.
     */
    fun plan(stacks: List<Stack>, others: List<Cells>, countX: Int, countY: Int): List<Cells> {
        val grids = HashMap<Int, GridOccupancy>()
        fun grid(screen: Int) = grids.getOrPut(screen) { GridOccupancy(countX, countY) }
        others.forEach { grid(it.screen).markCells(it.x, it.y, it.spanX, it.spanY, true) }
        stacks.forEach { grid(it.cells.screen).markCells(it.cells.x, it.cells.y, it.cells.spanX, it.cells.spanY, true) }

        val result = ArrayList<Cells>()
        val vacant = kotlin.IntArray(2)
        var nextNewScreen = (grids.keys.maxOrNull() ?: -1) + 1
        stacks.forEach { stack ->
            val s = stack.cells
            val visible = stack.activeRank.coerceIn(0, (stack.childIds.size - 1).coerceAtLeast(0))
            stack.childIds.forEachIndexed { rank, id ->
                if (rank == visible) {
                    result.add(s.copy(id = id))
                    return@forEachIndexed
                }
                val screens = listOf(s.screen) + grids.keys.sorted().filter { it > s.screen } +
                    grids.keys.sorted().filter { it < s.screen }
                val screen = screens.firstOrNull { grid(it).findVacantCell(vacant, s.spanX, s.spanY) }
                    ?: nextNewScreen++.also { grid(it).findVacantCell(vacant, s.spanX, s.spanY) }
                grid(screen).markCells(vacant[0], vacant[1], s.spanX, s.spanY, true)
                result.add(Cells(id, screen, vacant[0], vacant[1], s.spanX, s.spanY))
            }
        }
        return result
    }

    /** Unstacks every stack and reloads the launcher. Returns how many stacks were separated. */
    suspend fun unstackAll(context: Context): Int = withContext(MODEL_EXECUTOR.asCoroutineDispatcher()) {
        val app = LauncherAppState.getInstance(context)
        val db = app.model.modelDbController
        val idp = LauncherAppState.getIDP(context)
        val (stacks, others) = read(db)
        if (stacks.isEmpty()) return@withContext 0

        val placements = plan(stacks, others, idp.numColumns, idp.numRows)
        db.newTransaction().use { t ->
            placements.forEach { c ->
                val values = ContentValues().apply {
                    put(Favorites.CONTAINER, Favorites.CONTAINER_DESKTOP)
                    put(Favorites.SCREEN, c.screen)
                    put(Favorites.CELLX, c.x)
                    put(Favorites.CELLY, c.y)
                    put(Favorites.SPANX, c.spanX)
                    put(Favorites.SPANY, c.spanY)
                    put(Favorites.RANK, 0)
                }
                db.update(values, "${Favorites._ID}=${c.id}", null)
            }
            db.delete(
                Utilities.createDbSelectionQuery(Favorites._ID, IntArray.wrap(*stacks.map { it.cells.id }.toIntArray())),
                null,
            )
            t.commit()
        }
        app.model.forceReload()
        stacks.size
    }

    private fun read(db: ModelDbController): Pair<List<Stack>, List<Cells>> {
        val columns = arrayOf(
            Favorites._ID, Favorites.ITEM_TYPE, Favorites.CONTAINER, Favorites.SCREEN,
            Favorites.CELLX, Favorites.CELLY, Favorites.SPANX, Favorites.SPANY,
            Favorites.RANK, Favorites.OPTIONS,
        )
        val stackRows = ArrayList<Pair<Cells, Int>>()
        val others = ArrayList<Cells>()
        val children = HashMap<Int, MutableList<Pair<Int, Int>>>()
        db.query(columns, null, null, null).use { c ->
            while (c.moveToNext()) {
                val cells = Cells(c.getInt(0), c.getInt(3), c.getInt(4), c.getInt(5), c.getInt(6), c.getInt(7))
                val itemType = c.getInt(1)
                val container = c.getInt(2)
                when {
                    itemType == Favorites.ITEM_TYPE_WIDGET_STACK && container == Favorites.CONTAINER_DESKTOP ->
                        stackRows.add(cells to WidgetStackOptions.activeRank(c.getInt(9)))
                    container == Favorites.CONTAINER_DESKTOP -> others.add(cells)
                    container >= 0 -> children.getOrPut(container) { ArrayList() }.add(c.getInt(0) to c.getInt(8))
                }
            }
        }
        val stacks = stackRows.map { (cells, active) ->
            Stack(cells, active, children[cells.id].orEmpty().sortedWith(compareBy({ it.second }, { it.first })).map { it.first })
        }
        return stacks to others
    }
}
