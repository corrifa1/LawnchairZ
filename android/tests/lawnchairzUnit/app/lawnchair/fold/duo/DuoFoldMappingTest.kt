/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 */

package app.lawnchair.fold.duo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Which page shows after a fold or unfold in Duo continuity mode. */
class DuoFoldMappingTest {

    // Cover pages are the even screens, so the cover has pages 0, 2, 4. The unfolded view order has
    // the companion first (Right dock, LTR): [1, 0, 3, 2, 5, 4].
    private val cover = listOf(0, 2, 4)
    private val unfolded = DuoPairing.arrangePairs(listOf(0, 1, 2, 3, 4, 5), swap = true)

    private fun paneOf(viewOrder: List<Int>, screen: Int) = viewOrder.indexOf(screen) % 2

    // Unfolding

    @Test
    fun unfold_everyCoverPageLandsInThePaneNextToTheDock() {
        for (screen in cover) {
            val page = DuoFoldMapping.unfoldedPageIndex(unfolded, screen)
            // Right dock: the cover page is the second page of the pair, which is the right pane.
            assertThat(unfolded[page + 1]).isEqualTo(screen)
            assertThat(paneOf(unfolded, screen)).isEqualTo(1)
        }
    }

    @Test
    fun unfold_fromCoverPage0_showsPair0() {
        assertThat(DuoFoldMapping.unfoldedPageIndex(unfolded, 0)).isEqualTo(0)
    }

    @Test
    fun unfold_fromCoverPage2_showsTheSecondPair() {
        assertThat(DuoFoldMapping.unfoldedPageIndex(unfolded, 2)).isEqualTo(2)
        assertThat(unfolded.subList(2, 4)).containsExactly(3, 2).inOrder()
    }

    @Test
    fun unfold_fromCoverPage4_showsTheThirdPair() {
        assertThat(DuoFoldMapping.unfoldedPageIndex(unfolded, 4)).isEqualTo(4)
    }

    @Test
    fun unfold_withALeftDock_putsTheCoverPageInTheLeftPane() {
        val leftDock = DuoPairing.arrangePairs(listOf(0, 1, 2, 3), swap = false)
        assertThat(paneOf(leftDock, 2)).isEqualTo(0)
        assertThat(DuoFoldMapping.unfoldedPageIndex(leftDock, 2)).isEqualTo(2)
    }

    @Test
    fun unfold_ofAnUnboundScreen_isNotFound() {
        assertThat(DuoFoldMapping.unfoldedPageIndex(unfolded, 8)).isEqualTo(-1)
    }

    // Folding

    @Test
    fun fold_fromEitherPaneOfAPair_returnsTheDockSidePage() {
        assertThat(DuoFoldMapping.coverScreenAfterFold(2)).isEqualTo(2)
        assertThat(DuoFoldMapping.coverScreenAfterFold(3)).isEqualTo(2)
    }

    @Test
    fun foldThenUnfold_returnsToTheSamePair() {
        for (start in cover) {
            val page = DuoFoldMapping.unfoldedPageIndex(unfolded, start)
            val shown = unfolded[page]
            val back = DuoFoldMapping.coverScreenAfterFold(shown)
            assertThat(back).isEqualTo(start)
        }
    }

    @Test
    fun unfoldThenFold_returnsToTheSameCoverPage() {
        for (start in cover) {
            val page = DuoFoldMapping.unfoldedPageIndex(unfolded, start)
            val visible = listOf(unfolded[page], unfolded[page + 1])
            // Whichever pane the "current screen" is read from, it folds to the same page.
            for (screen in visible) {
                assertThat(DuoFoldMapping.coverScreenAfterFold(screen)).isEqualTo(start)
            }
        }
    }

    // Default home page

    @Test
    fun defaultPage_savedValueIsThePairsFirstScreen() {
        // Cover page 1 is screen 2; the unfolded pair (3, 2) starts at page index 2.
        assertThat(DuoFoldMapping.defaultPageToStored(1, isTwoPanels = false)).isEqualTo(2)
        assertThat(DuoFoldMapping.defaultPageToStored(2, isTwoPanels = true)).isEqualTo(2)
        assertThat(DuoFoldMapping.defaultPageToStored(3, isTwoPanels = true)).isEqualTo(2)
    }

    @Test
    fun defaultPage_sameSavedValueGivesTheSamePairOnBothScreens() {
        val stored = 4
        assertThat(DuoFoldMapping.storedToDefaultPage(stored, isTwoPanels = false)).isEqualTo(2)
        assertThat(DuoFoldMapping.storedToDefaultPage(stored, isTwoPanels = true)).isEqualTo(4)
        // Cover page 2 is screen 4, which is in the pair that starts at unfolded page 4.
        assertThat(cover[2]).isEqualTo(4)
        assertThat(unfolded.subList(4, 6)).contains(4)
    }

    @Test
    fun defaultPage_roundTripsOnEachScreen() {
        for (page in 0..5) {
            val coverStored = DuoFoldMapping.defaultPageToStored(page, isTwoPanels = false)
            assertThat(DuoFoldMapping.storedToDefaultPage(coverStored, isTwoPanels = false)).isEqualTo(page)
        }
        for (page in listOf(0, 2, 4)) {
            val stored = DuoFoldMapping.defaultPageToStored(page, isTwoPanels = true)
            assertThat(DuoFoldMapping.storedToDefaultPage(stored, isTwoPanels = true)).isEqualTo(page)
        }
    }

    @Test
    fun defaultPage_matchesPagePairsModeWhenTheModeIsTurnedOff() {
        // In "Page pairs" mode the saved value is the page index of the pair's first page.
        assertThat(DuoFoldMapping.defaultPageToStored(1, isTwoPanels = false)).isEqualTo(2)
        assertThat(DuoFoldMapping.defaultPageToStored(0, isTwoPanels = false)).isEqualTo(0)
    }

    // The in-place step before the rebind

    @Test
    fun pageAfterInPlaceFold_movesFromTheCompanionToTheDockSidePage() {
        // The unfolded current page is the first of the pair, which is the companion.
        assertThat(DuoFoldMapping.pageAfterInPlaceFold(unfolded, 2)).isEqualTo(3)
        assertThat(unfolded[3]).isEqualTo(2)
    }

    @Test
    fun pageAfterInPlaceFold_staysWhenTheDockSidePageIsTheFirstOfThePair() {
        val leftDock = listOf(0, 1, 2, 3)
        assertThat(DuoFoldMapping.pageAfterInPlaceFold(leftDock, 2)).isEqualTo(2)
    }

    @Test
    fun pageAfterInPlaceFold_ignoresPagesOutOfRangeAndExtras() {
        assertThat(DuoFoldMapping.pageAfterInPlaceFold(unfolded, 9)).isEqualTo(9)
        assertThat(DuoFoldMapping.pageAfterInPlaceFold(listOf(1, 0, -201, -200), 2)).isEqualTo(2)
    }
}
