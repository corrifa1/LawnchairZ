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

    /** `WorkspaceLayoutManager.EXTRA_EMPTY_SCREEN_ID` and `EXTRA_EMPTY_SCREEN_SECOND_ID`. */
    const val EXTRA_SCREEN = -201
    const val EXTRA_SECOND_SCREEN = -200

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
     * The two extra empty screens also form a pair, with [EXTRA_SCREEN] as the dock-side one.
     * Positions that don't hold a pair are left alone. The result doesn't depend on which way
     * round [order] was, so it can undo its own effect.
     */
    fun arrangePairs(order: List<Int>, swap: Boolean): List<Int> {
        val result = order.toMutableList()
        var i = 0
        while (i + 1 < result.size) {
            val a = result[i]
            val b = result[i + 1]
            val dockSide = when {
                a >= 0 && b >= 0 && a xor 1 == b -> if (isDockSideScreen(a)) a else b
                a == EXTRA_SCREEN && b == EXTRA_SECOND_SCREEN -> a
                a == EXTRA_SECOND_SCREEN && b == EXTRA_SCREEN -> b
                else -> null
            }
            if (dockSide != null) {
                val companion = if (dockSide == a) b else a
                result[i] = if (swap) companion else dockSide
                result[i + 1] = if (swap) dockSide else companion
            }
            i += 2
        }
        return result
    }

    /**
     * The id for a new dock-side screen, given the next free id [candidate] from the database: even,
     * and with both screens of its pair free according to [isTaken].
     */
    fun nextDockSideScreenId(candidate: Int, isTaken: (Int) -> Boolean): Int {
        var id = if (candidate and 1 == 1) candidate + 1 else candidate
        while (isTaken(id) || isTaken(id + 1)) id += 2
        return id
    }

    /**
     * Whether an empty cover page can be dropped. Its companion isn't bound on the cover, so the
     * pair goes only if the companion has no items ([companionHasItems]) and isn't a page the user
     * kept ([companionIsSaved]).
     */
    fun coverPairIsRemovable(companionHasItems: Boolean, companionIsSaved: Boolean): Boolean =
        !companionHasItems && !companionIsSaved

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
