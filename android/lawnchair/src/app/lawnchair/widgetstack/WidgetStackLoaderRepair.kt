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
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.Utilities
import com.android.launcher3.logging.FileLog
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.IntSparseArrayMap

/**
 * Attaches loaded widget rows to their stacks and repairs whatever the database left behind.
 *
 * Runs once per load, after invalid rows were already removed:
 * - Widgets whose container is not a loaded stack are deleted. Widgets are never folder children.
 * - Empty stacks are deleted.
 * - A stack with one child becomes a plain widget at the stack's position.
 * - Ranks are renumbered 0..n-1, the visible rank is clamped, and children take the stack's span.
 *
 * Widget ids freed here are reclaimed by DatabaseHelper.removeGhostWidgets later in the load.
 */
object WidgetStackLoaderRepair {

    private const val TAG = "WidgetStackLoaderRepair"

    class Plan {
        /** Row ids to delete from the database and drop from the loaded items. */
        val deleteIds = LinkedHashSet<Int>()

        /** Children that replace their single-child stack on the workspace. */
        val promoted = ArrayList<LauncherAppWidgetInfo>()

        /** Rows whose position, span, rank or options changed. */
        val dirty = LinkedHashSet<ItemInfo>()

        val isEmpty get() = deleteIds.isEmpty() && promoted.isEmpty() && dirty.isEmpty()
    }

    /** Computes the repairs for [items] and applies them to the in-memory items only. */
    fun plan(items: Iterable<ItemInfo>): Plan {
        val plan = Plan()
        val stacks = items.filterIsInstance<WidgetStackInfo>().associateBy { it.id }
        val childrenByStack = HashMap<Int, MutableList<LauncherAppWidgetInfo>>()

        items.forEach { item ->
            if (item !is LauncherAppWidgetInfo || item.container < 0) return@forEach
            if (stacks.containsKey(item.container)) {
                childrenByStack.getOrPut(item.container) { ArrayList() }.add(item)
            } else {
                plan.deleteIds.add(item.id)
            }
        }

        stacks.values.forEach { stack ->
            val children = childrenByStack[stack.id].orEmpty()
            when {
                children.isEmpty() -> plan.deleteIds.add(stack.id)
                children.size < WidgetStackInfo.MIN_CHILDREN -> {
                    val child = children.single()
                    child.container = Favorites.CONTAINER_DESKTOP
                    child.screenId = stack.screenId
                    child.cellX = stack.cellX
                    child.cellY = stack.cellY
                    child.spanX = stack.spanX
                    child.spanY = stack.spanY
                    child.rank = 0
                    plan.promoted.add(child)
                    plan.dirty.add(child)
                    plan.deleteIds.add(stack.id)
                }
                else -> {
                    val oldOptions = stack.options
                    plan.dirty.addAll(stack.setContents(children))
                    plan.dirty.addAll(stack.syncChildGeometry())
                    if (stack.options != oldOptions) plan.dirty.add(stack)
                    if (children.size > WidgetStackInfo.MAX_CHILDREN) {
                        // Keep them rather than lose widgets; the cap is enforced when adding.
                        FileLog.w(TAG, "Stack ${stack.id} has ${children.size} children")
                    }
                }
            }
        }
        return plan
    }

    /** Plans and persists the repairs, and removes deleted rows from [loadedItems]. */
    @JvmStatic
    fun repair(loadedItems: IntSparseArrayMap<ItemInfo>, db: ModelDbController) {
        val plan = plan(loadedItems)
        if (plan.isEmpty) return

        if (plan.deleteIds.isNotEmpty()) {
            FileLog.d(TAG, "Deleting stack rows ${plan.deleteIds}")
            val ids = IntArray.wrap(*plan.deleteIds.toIntArray())
            db.delete(Utilities.createDbSelectionQuery(Favorites._ID, ids), null)
            plan.deleteIds.forEach(loadedItems::remove)
        }
        plan.dirty.forEach { item ->
            val values = ContentValues().apply {
                put(Favorites.CONTAINER, item.container)
                put(Favorites.SCREEN, item.screenId)
                put(Favorites.CELLX, item.cellX)
                put(Favorites.CELLY, item.cellY)
                put(Favorites.SPANX, item.spanX)
                put(Favorites.SPANY, item.spanY)
                put(Favorites.RANK, item.rank)
                if (item is WidgetStackInfo) put(Favorites.OPTIONS, item.options)
            }
            db.update(values, "${Favorites._ID}=${item.id}", null)
        }
    }
}
