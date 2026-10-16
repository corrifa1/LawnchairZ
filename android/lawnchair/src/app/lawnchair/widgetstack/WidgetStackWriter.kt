/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.celllayout.CellPosMapper
import com.android.launcher3.model.ModelWriter
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.widget.LauncherWidgetHolder

/**
 * Model writes for widget stacks, built on [ModelWriter] so they share its threading, undo and
 * callback behaviour. Call from the UI thread, like ModelWriter.
 *
 * Positions passed in are presenter coordinates, as for ModelWriter. [cellPosMapper] converts a
 * stack's model position back when a child takes its place.
 */
class WidgetStackWriter(
    private val writer: ModelWriter,
    private val cellPosMapper: CellPosMapper,
) {

    /**
     * Creates a stack in the given cells holding [children] in order, showing the first. Children
     * may be new (no id yet) or existing widgets, which are moved into the stack.
     */
    fun createStack(
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
        children: List<LauncherAppWidgetInfo>,
    ): WidgetStackInfo {
        require(container == Favorites.CONTAINER_DESKTOP) { "Stacks are workspace-only" }
        require(children.size <= WidgetStackInfo.MAX_CHILDREN) { "Too many children" }
        val stack = WidgetStackInfo()
        stack.spanX = spanX
        stack.spanY = spanY
        writer.addItemToDatabase(stack, container, screenId, cellX, cellY)
        children.forEach { stack.add(it) }
        stack.activeRank = 0
        children.forEach { persistChild(stack, it) }
        writer.updateItemInDatabase(stack)
        return stack
    }

    /** Adds [child] at [rank]. Returns false when the stack is full. */
    @JvmOverloads
    fun addChild(stack: WidgetStackInfo, child: LauncherAppWidgetInfo, rank: Int = stack.size): Boolean {
        if (stack.size >= WidgetStackInfo.MAX_CHILDREN) return false
        stack.add(child, rank).forEach { persistChild(stack, it) }
        writer.updateItemInDatabase(stack)
        return true
    }

    /**
     * Takes [child] out of [stack] without deleting it, for example when it is dragged onto the
     * workspace. The caller places the child. Resolve a stack left with fewer than two children
     * with [collapseIfNeeded].
     */
    fun detachChild(stack: WidgetStackInfo, child: LauncherAppWidgetInfo) {
        stack.remove(child).forEach { persistChild(stack, it) }
        writer.updateItemInDatabase(stack)
    }

    /** Removes [child] and deletes its widget id. */
    fun deleteChild(stack: WidgetStackInfo, child: LauncherAppWidgetInfo, holder: LauncherWidgetHolder?) {
        stack.remove(child).forEach { persistChild(stack, it) }
        writer.deleteWidgetInfo(child, holder, "removed from widget stack")
        writer.updateItemInDatabase(stack)
    }

    /** Moves the child at [from] to [to]. */
    fun reorder(stack: WidgetStackInfo, from: Int, to: Int) {
        val changed = stack.move(from, to)
        if (changed.isEmpty()) return
        changed.forEach { persistChild(stack, it) }
        writer.updateItemInDatabase(stack)
    }

    /** Moves [child] from one stack to another. Returns false when [to] is full. */
    @JvmOverloads
    fun moveChild(
        from: WidgetStackInfo,
        to: WidgetStackInfo,
        child: LauncherAppWidgetInfo,
        rank: Int = to.size,
    ): Boolean {
        if (to.size >= WidgetStackInfo.MAX_CHILDREN) return false
        detachChild(from, child)
        return addChild(to, child, rank)
    }

    fun setActiveRank(stack: WidgetStackInfo, rank: Int) {
        val old = stack.options
        stack.activeRank = rank
        if (stack.options != old) writer.updateItemInDatabase(stack)
    }

    fun setSmartRotate(stack: WidgetStackInfo, enabled: Boolean) {
        val old = stack.options
        stack.smartRotate = enabled
        if (stack.options != old) writer.updateItemInDatabase(stack)
    }

    /** Persists a move or resize of the stack (presenter coordinates) and syncs its children. */
    fun moveOrResize(stack: WidgetStackInfo, screenId: Int, cellX: Int, cellY: Int, spanX: Int, spanY: Int) {
        writer.modifyItemInDatabase(stack, stack.container, screenId, cellX, cellY, spanX, spanY)
        stack.contents.forEach { persistChild(stack, it) }
    }

    /**
     * Converts a stack with one child back into that widget, in the stack's cells, and deletes an
     * empty stack. Returns the widget that took the stack's place, if any.
     */
    fun collapseIfNeeded(stack: WidgetStackInfo): LauncherAppWidgetInfo? {
        if (stack.size >= WidgetStackInfo.MIN_CHILDREN) return null
        val child = stack.contents.singleOrNull()
        if (child != null) {
            val pos = cellPosMapper.mapModelToPresenter(stack)
            stack.remove(child)
            child.rank = 0
            writer.modifyItemInDatabase(
                child, stack.container, pos.screenId, pos.cellX, pos.cellY, stack.spanX, stack.spanY,
            )
        }
        writer.deleteItemFromDatabase(stack, "widget stack collapsed")
        return child
    }

    /** Deletes the stack, every child and their widget ids. */
    fun deleteStack(stack: WidgetStackInfo, holder: LauncherWidgetHolder?) {
        stack.contents.toList().forEach { writer.deleteWidgetInfo(it, holder, "widget stack deleted") }
        writer.deleteItemFromDatabase(stack, "widget stack deleted")
    }

    /** Writes [child]'s container, rank and the stack's geometry, inserting it if it is new. */
    private fun persistChild(stack: WidgetStackInfo, child: LauncherAppWidgetInfo) {
        if (child.id == ItemInfo.NO_ID) {
            child.spanX = stack.spanX
            child.spanY = stack.spanY
            writer.addItemToDatabase(child, stack.id, stack.screenId, 0, 0)
        } else {
            writer.modifyItemInDatabase(child, stack.id, stack.screenId, 0, 0, stack.spanX, stack.spanY)
        }
    }
}
