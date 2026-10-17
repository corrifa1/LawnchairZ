/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 */

package app.lawnchair.fold.duo

/**
 * The pairing rule for Duo continuity, kept free of Android types so it can be unit tested.
 *
 * Screens pair as (2k, 2k + 1), the same ids that `Workspace.getScreenPair` pairs. The even screen
 * is the *dock-side* screen: it is the cover-screen page, and the pane next to the dock when
 * unfolded. The odd screen is its *companion*, which shows only when unfolded.
 *
 * Extra empty screens have negative ids and are never part of a pair.
 */
object DuoPairing {

    enum class Pane { LEFT, RIGHT }

    /** The pane that holds the cover-screen page when unfolded, for a dock on [dockSide]. */
    fun coverPane(dockSide: DockSide): Pane = when (dockSide) {
        DockSide.LEFT -> Pane.LEFT
        DockSide.RIGHT, DockSide.BOTTOM -> Pane.RIGHT
    }

    /** The pair a regular screen belongs to. */
    fun pairIndex(screenId: Int): Int = screenId shr 1

    /** The dock-side (cover) screen of pair [pair]. */
    fun dockSideScreen(pair: Int): Int = pair * 2

    /** The companion screen of pair [pair]. */
    fun companionScreen(pair: Int): Int = pair * 2 + 1

    /** The dock-side screen of the pair [screenId] belongs to. Extra screens map to themselves. */
    fun dockSideScreenOf(screenId: Int): Int = if (screenId < 0) screenId else screenId and 1.inv()

    /** The other screen of the pair. Extra screens map to themselves. */
    fun pairedScreenOf(screenId: Int): Int = if (screenId < 0) screenId else screenId xor 1

    fun isDockSideScreen(screenId: Int): Boolean = screenId >= 0 && screenId and 1 == 0

    fun isCompanionScreen(screenId: Int): Boolean = screenId >= 0 && screenId and 1 == 1

    /** Only dock-side screens are pages of the cover screen. Extra screens are never hidden. */
    fun isVisibleOnCover(screenId: Int): Boolean = screenId < 0 || isDockSideScreen(screenId)

    /**
     * Whether the two screens of a pair swap places in the view order, so that the dock-side screen
     * lands in [coverPane]. `PagedView` puts the child at an even index in the left pane in LTR and
     * in the right pane in RTL.
     */
    fun swapsViewOrder(coverPane: Pane, isRtl: Boolean): Boolean = (coverPane == Pane.RIGHT) != isRtl

    /**
     * Returns [order] with every aligned pair of positions (0 and 1, 2 and 3, ...) that holds the two
     * screens of one pair put in the dock-side screen first (when [swap] is false) or second.
     * Positions that don't hold a pair, such as extra empty screens, are left alone. The result
     * doesn't depend on which way round [order] was, so it can undo its own effect.
     */
    fun arrangePairs(order: List<Int>, swap: Boolean): List<Int> {
        val result = order.toMutableList()
        var i = 0
        while (i + 1 < result.size) {
            val a = result[i]
            val b = result[i + 1]
            if (a >= 0 && b >= 0 && a xor 1 == b) {
                val dockSide = if (isDockSideScreen(a)) a else b
                val companion = pairedScreenOf(dockSide)
                result[i] = if (swap) companion else dockSide
                result[i + 1] = if (swap) dockSide else companion
            }
            i += 2
        }
        return result
    }

    /**
     * The screens the cover shows for [order]: each regular screen replaced by its dock-side
     * screen, once, in order. A companion with no dock-side screen in [order] brings an empty one.
     */
    fun coverScreens(order: List<Int>): List<Int> =
        order.map { dockSideScreenOf(it) }.distinct()

    /** The companions in [order], which the cover doesn't show. */
    fun hiddenCompanions(order: List<Int>): List<Int> =
        order.filter { isCompanionScreen(it) }.distinct()

    /**
     * The order to save for a workspace that shows [shown] while [hidden] companions are not bound:
     * dock-side screen first, each hidden companion straight after its dock-side screen, and any
     * companion whose dock-side screen is gone at the end. Nothing in [hidden] is dropped.
     */
    fun persistableOrder(shown: List<Int>, hidden: Collection<Int>): List<Int> {
        val unswapped = arrangePairs(shown, swap = false)
        if (hidden.isEmpty()) return unswapped
        val pending = hidden.filter { it !in unswapped }.toMutableList()
        val result = ArrayList<Int>(unswapped.size + pending.size)
        for (id in unswapped) {
            result.add(id)
            val companion = pairedScreenOf(id)
            if (isDockSideScreen(id) && pending.remove(companion)) {
                result.add(companion)
            }
        }
        result.addAll(pending)
        return result
    }
}

/** Where the dock sits, as far as pairing cares. */
enum class DockSide { LEFT, RIGHT, BOTTOM }
