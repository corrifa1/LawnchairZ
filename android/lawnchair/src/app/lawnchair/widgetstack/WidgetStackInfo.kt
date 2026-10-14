/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.ContentWriter

/**
 * Model for a widget stack: one workspace item that holds several widgets in the same cells.
 *
 * Children are ordinary [LauncherAppWidgetInfo] rows whose `container` is this stack's id and
 * whose `rank` gives their order, mirroring how folders hold their items. Per-stack settings live
 * in the existing `options` column (see [WidgetStackOptions]), so the stack needs no new columns.
 *
 * This deliberately extends [ItemInfo] rather than CollectionInfo: CollectionInfo's contract is a
 * collection of icons, and many folder and app-pair code paths type-check for it.
 */
class WidgetStackInfo : ItemInfo() {

    /** Packed per-stack settings. See [WidgetStackOptions]. */
    @JvmField var options: Int = 0

    private val children = ArrayList<LauncherAppWidgetInfo>()

    init {
        itemType = Favorites.ITEM_TYPE_WIDGET_STACK
    }

    /** The children in rank order. */
    val contents: List<LauncherAppWidgetInfo> get() = children

    val size: Int get() = children.size

    var smartRotate: Boolean
        get() = WidgetStackOptions.isSmartRotate(options)
        set(value) {
            options = WidgetStackOptions.withSmartRotate(options, value)
        }

    /** Rank of the child the stack shows. Always within the current contents when non-empty. */
    var activeRank: Int
        get() = WidgetStackOptions.activeRank(options).coerceIn(0, (size - 1).coerceAtLeast(0))
        set(value) {
            options = WidgetStackOptions.withActiveRank(
                options,
                value.coerceIn(0, (size - 1).coerceAtLeast(0)),
            )
        }

    val activeChild: LauncherAppWidgetInfo? get() = children.getOrNull(activeRank)

    /**
     * Replaces the contents with [items], ordered by rank (ties broken by id), and renumbers ranks
     * from 0. Returns the children whose rank changed. Used by the loader.
     */
    fun setContents(items: Collection<LauncherAppWidgetInfo>): List<LauncherAppWidgetInfo> {
        children.clear()
        children.addAll(items)
        WidgetStackOrdering.sortByRank(children)
        children.forEach { it.container = id }
        val changed = WidgetStackOrdering.renumber(children)
        activeRank = activeRank
        return changed
    }

    /**
     * Inserts [child] at [rank] (appends by default) and keeps the visible child unchanged.
     * Returns every child whose rank or container changed, including [child].
     */
    @JvmOverloads
    fun add(child: LauncherAppWidgetInfo, rank: Int = size): List<LauncherAppWidgetInfo> {
        val active = if (children.isEmpty()) -1 else activeRank
        val index = rank.coerceIn(0, size)
        children.add(index, child)
        child.container = id
        val changed = WidgetStackOrdering.renumber(children).toMutableSet()
        changed.add(child)
        activeRank = if (active < 0) 0 else WidgetStackOrdering.activeAfterInsert(active, index)
        return children.filter { it in changed }
    }

    /** Removes [child]. Returns the remaining children whose rank changed. */
    fun remove(child: LauncherAppWidgetInfo): List<LauncherAppWidgetInfo> {
        val index = children.indexOf(child)
        if (index < 0) return emptyList()
        val active = activeRank
        children.removeAt(index)
        val changed = WidgetStackOrdering.renumber(children)
        activeRank = WidgetStackOrdering.activeAfterRemove(active, index, size)
        return changed
    }

    /**
     * Moves the child at [from] to [to]. The visible child stays the same widget. Returns the
     * children whose rank changed.
     */
    fun move(from: Int, to: Int): List<LauncherAppWidgetInfo> {
        if (from !in children.indices || to !in children.indices || from == to) return emptyList()
        val active = activeRank
        WidgetStackOrdering.move(children, from, to)
        activeRank = WidgetStackOrdering.activeAfterMove(active, from, to)
        return WidgetStackOrdering.renumber(children)
    }

    /**
     * Copies the stack's position and span onto every child. Children are never laid out on their
     * own, but keeping them in sync keeps size options and a downgrade promotion consistent.
     * Returns the children that changed.
     */
    fun syncChildGeometry(): List<LauncherAppWidgetInfo> = children.filter { child ->
        val changed = child.spanX != spanX || child.spanY != spanY ||
            child.screenId != screenId || child.cellX != 0 || child.cellY != 0
        child.spanX = spanX
        child.spanY = spanY
        child.screenId = screenId
        child.cellX = 0
        child.cellY = 0
        changed
    }

    override fun onAddToDatabase(writer: ContentWriter) {
        super.onAddToDatabase(writer)
        writer.put(Favorites.OPTIONS, options)
    }

    override fun dumpProperties(): String =
        super.dumpProperties() + " children=${children.map { it.id }} options=$options"

    companion object {
        /** A stack shows at most this many widgets. */
        const val MAX_CHILDREN = 10

        /** A stack with fewer children than this is converted back into a plain widget. */
        const val MIN_CHILDREN = 2
    }
}
