/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import com.android.launcher3.model.data.ItemInfo

/** Pure ordering rules for the children of a widget stack. */
object WidgetStackOrdering {

    private val RANK_ORDER = compareBy<ItemInfo>({ it.rank }, { it.id })

    fun <T : ItemInfo> sortByRank(items: MutableList<T>) = items.sortWith(RANK_ORDER)

    /** Assigns ranks 0..n-1 in list order. Returns the items whose rank changed. */
    fun <T : ItemInfo> renumber(items: List<T>): List<T> = items.filterIndexed { index, item ->
        val changed = item.rank != index
        item.rank = index
        changed
    }

    fun <T> move(items: MutableList<T>, from: Int, to: Int) {
        items.add(to, items.removeAt(from))
    }

    /** Where the visible child ends up after the child at [from] moves to [to]. */
    fun activeAfterMove(active: Int, from: Int, to: Int): Int = when {
        active == from -> to
        from < active && to >= active -> active - 1
        from > active && to <= active -> active + 1
        else -> active
    }

    /** Where the visible child ends up after a child is inserted at [index]. */
    fun activeAfterInsert(active: Int, index: Int): Int = if (index <= active) active + 1 else active

    /**
     * Where the visible child ends up after the child at [index] is removed, leaving [newSize]
     * children. Removing the visible child shows the one that slid into its place, or the new last
     * child when the old last one was removed.
     */
    fun activeAfterRemove(active: Int, index: Int, newSize: Int): Int {
        if (newSize <= 0) return 0
        val next = if (index < active) active - 1 else active
        return next.coerceIn(0, newSize - 1)
    }
}
