/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 */

package app.lawnchair.fold.duo

/**
 * Which page to show across a fold or unfold in Duo continuity mode. Pure, so it can be unit tested.
 *
 * Unfolding from cover page N shows the pair whose dock-side screen is N, with N in the pane next to
 * the dock. Folding from any pane of a pair shows that pair's dock-side screen, never its companion.
 */
object DuoFoldMapping {

    /** The cover screen to show after folding from a pair that contains [screenInView]. */
    fun coverScreenAfterFold(screenInView: Int): Int = DuoPairing.dockSideScreenOf(screenInView)

    /**
     * The page index to show after unfolding from [coverScreen], in the unfolded [viewOrder]: the
     * first page of the pair that holds it, or -1 when the screen isn't bound.
     */
    fun unfoldedPageIndex(viewOrder: List<Int>, coverScreen: Int, panelCount: Int = 2): Int {
        val index = viewOrder.indexOf(coverScreen)
        return if (index < 0) -1 else index - index % panelCount
    }

    /**
     * The page to show on the cover screen straight after a fold, before the model rebinds, while the
     * views still follow the unfolded [viewOrder]: the dock-side screen of the pair at [currentPage].
     * Returns [currentPage] when the pair has no dock-side screen bound.
     */
    fun pageAfterInPlaceFold(viewOrder: List<Int>, currentPage: Int): Int {
        val screen = viewOrder.getOrNull(currentPage) ?: return currentPage
        val index = viewOrder.indexOf(coverScreenAfterFold(screen))
        return if (index < 0) currentPage else index
    }
}
