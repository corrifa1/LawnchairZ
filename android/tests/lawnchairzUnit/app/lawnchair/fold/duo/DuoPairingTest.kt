/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 */

package app.lawnchair.fold.duo

import app.lawnchair.fold.duo.DuoPairing.Pane
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The Duo continuity pairing rule: which screen is next to the dock, and how pairs are ordered. */
class DuoPairingTest {

    private val extra = -201
    private val extraSecond = -200

    // Which pane holds the cover page

    @Test
    fun coverPane_rightAndBottomDocksUseTheRightPane() {
        assertThat(DuoPairing.coverPane(DockSide.RIGHT)).isEqualTo(Pane.RIGHT)
        assertThat(DuoPairing.coverPane(DockSide.BOTTOM)).isEqualTo(Pane.RIGHT)
    }

    @Test
    fun coverPane_leftDockUsesTheLeftPane() {
        assertThat(DuoPairing.coverPane(DockSide.LEFT)).isEqualTo(Pane.LEFT)
    }

    // Whether the pair swaps in the view order

    @Test
    fun swapsViewOrder_rightPaneSwapsInLtrOnly() {
        assertThat(DuoPairing.swapsViewOrder(Pane.RIGHT, isRtl = false)).isTrue()
        assertThat(DuoPairing.swapsViewOrder(Pane.RIGHT, isRtl = true)).isFalse()
    }

    @Test
    fun swapsViewOrder_leftPaneSwapsInRtlOnly() {
        assertThat(DuoPairing.swapsViewOrder(Pane.LEFT, isRtl = false)).isFalse()
        assertThat(DuoPairing.swapsViewOrder(Pane.LEFT, isRtl = true)).isTrue()
    }

    // Screens

    @Test
    fun pairs_matchTheWorkspacePairing() {
        // Workspace.getScreenPair pairs (2k, 2k + 1).
        for (id in 0..9) {
            assertThat(DuoPairing.pairedScreenOf(DuoPairing.pairedScreenOf(id))).isEqualTo(id)
            assertThat(DuoPairing.pairIndex(id)).isEqualTo(DuoPairing.pairIndex(DuoPairing.pairedScreenOf(id)))
        }
        assertThat(DuoPairing.pairedScreenOf(2)).isEqualTo(3)
        assertThat(DuoPairing.pairedScreenOf(5)).isEqualTo(4)
    }

    @Test
    fun dockSideScreen_isTheEvenScreenOfThePair() {
        assertThat(DuoPairing.dockSideScreen(0)).isEqualTo(0)
        assertThat(DuoPairing.dockSideScreen(3)).isEqualTo(6)
        assertThat(DuoPairing.companionScreen(3)).isEqualTo(7)
        assertThat(DuoPairing.dockSideScreenOf(7)).isEqualTo(6)
        assertThat(DuoPairing.dockSideScreenOf(6)).isEqualTo(6)
    }

    @Test
    fun extraScreens_areNeverPairedOrHidden() {
        assertThat(DuoPairing.dockSideScreenOf(extra)).isEqualTo(extra)
        assertThat(DuoPairing.pairedScreenOf(extraSecond)).isEqualTo(extraSecond)
        assertThat(DuoPairing.isVisibleOnCover(extra)).isTrue()
        assertThat(DuoPairing.isCompanionScreen(extra)).isFalse()
    }

    @Test
    fun isVisibleOnCover_onlyDockSideScreens() {
        assertThat((0..5).filter { DuoPairing.isVisibleOnCover(it) }).containsExactly(0, 2, 4).inOrder()
    }

    // View order

    @Test
    fun arrangePairs_withoutSwap_putsTheDockSideScreenFirst() {
        assertThat(DuoPairing.arrangePairs(listOf(0, 1, 2, 3), swap = false))
            .containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun arrangePairs_withSwap_putsTheCompanionFirst() {
        assertThat(DuoPairing.arrangePairs(listOf(0, 1, 2, 3), swap = true))
            .containsExactly(1, 0, 3, 2).inOrder()
    }

    @Test
    fun arrangePairs_doesNotDependOnWhichWayRoundItStarted() {
        val swapped = listOf(1, 0, 3, 2)
        assertThat(DuoPairing.arrangePairs(swapped, swap = true)).containsExactly(1, 0, 3, 2).inOrder()
        assertThat(DuoPairing.arrangePairs(swapped, swap = false)).containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun arrangePairs_keepsPagesAfterAPageGroupMove() {
        // After a page move the pairs sit in a different order but stay together.
        assertThat(DuoPairing.arrangePairs(listOf(2, 3, 0, 1), swap = true))
            .containsExactly(3, 2, 1, 0).inOrder()
    }

    @Test
    fun arrangePairs_leavesUnpairedScreensAlone() {
        assertThat(DuoPairing.arrangePairs(listOf(0, 1, 2, extra), swap = true))
            .containsExactly(1, 0, 2, extra).inOrder()
        assertThat(DuoPairing.arrangePairs(listOf(0, 2, 1, 3), swap = true))
            .containsExactly(0, 2, 1, 3).inOrder()
        assertThat(DuoPairing.arrangePairs(emptyList(), swap = true)).isEmpty()
    }

    @Test
    fun arrangePairs_theExtraScreensArePairedWithTheFirstOneDockSide() {
        assertThat(DuoPairing.arrangePairs(listOf(0, 1, extra, extraSecond), swap = true))
            .containsExactly(1, 0, extraSecond, extra).inOrder()
        assertThat(DuoPairing.arrangePairs(listOf(0, 1, extraSecond, extra), swap = false))
            .containsExactly(0, 1, extra, extraSecond).inOrder()
    }

    @Test
    fun extraScreenConstants_matchTheWorkspace() {
        assertThat(DuoPairing.EXTRA_SCREEN)
            .isEqualTo(com.android.launcher3.WorkspaceLayoutManager.EXTRA_EMPTY_SCREEN_ID)
        assertThat(DuoPairing.EXTRA_SECOND_SCREEN)
            .isEqualTo(com.android.launcher3.WorkspaceLayoutManager.EXTRA_EMPTY_SCREEN_SECOND_ID)
    }

    // New pages

    @Test
    fun nextDockSideScreenId_roundsUpToAnEvenScreen() {
        assertThat(DuoPairing.nextDockSideScreenId(3) { false }).isEqualTo(4)
        assertThat(DuoPairing.nextDockSideScreenId(4) { false }).isEqualTo(4)
        assertThat(DuoPairing.nextDockSideScreenId(0) { false }).isEqualTo(0)
    }

    @Test
    fun nextDockSideScreenId_skipsPairsWithATakenScreen() {
        // 4 is free but its companion 5 is a hidden companion that exists.
        assertThat(DuoPairing.nextDockSideScreenId(3) { it == 5 }).isEqualTo(6)
        assertThat(DuoPairing.nextDockSideScreenId(4) { it == 4 || it == 6 }).isEqualTo(8)
    }

    // Removing empty pages on the cover

    @Test
    fun coverPairIsRemovable_onlyWhenTheCompanionHasNothing() {
        assertThat(DuoPairing.coverPairIsRemovable(companionHasItems = false, companionIsSaved = false)).isTrue()
        assertThat(DuoPairing.coverPairIsRemovable(companionHasItems = true, companionIsSaved = false)).isFalse()
        assertThat(DuoPairing.coverPairIsRemovable(companionHasItems = false, companionIsSaved = true)).isFalse()
    }

    // Cover screen

    @Test
    fun coverScreens_mapsEveryScreenToItsDockSideScreenOnce() {
        assertThat(DuoPairing.coverScreens(listOf(0, 1, 2, 3, 4))).containsExactly(0, 2, 4).inOrder()
    }

    @Test
    fun coverScreens_aCompanionWithoutADockSideScreenBringsAnEmptyOne() {
        assertThat(DuoPairing.coverScreens(listOf(0, 3))).containsExactly(0, 2).inOrder()
    }

    @Test
    fun hiddenCompanions_areTheOddScreens() {
        assertThat(DuoPairing.hiddenCompanions(listOf(0, 1, 2, 3, 3, 4))).containsExactly(1, 3).inOrder()
        assertThat(DuoPairing.hiddenCompanions(listOf(0, 2, 4))).isEmpty()
    }

    // Saving

    @Test
    fun persistableOrder_keepsHiddenCompanionsNextToTheirDockSideScreen() {
        assertThat(DuoPairing.persistableOrder(listOf(0, 2, 4), listOf(1, 3, 5)))
            .containsExactly(0, 1, 2, 3, 4, 5).inOrder()
    }

    @Test
    fun persistableOrder_undoesTheSwap() {
        assertThat(DuoPairing.persistableOrder(listOf(1, 0, 3, 2), emptyList()))
            .containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun persistableOrder_doesNotDuplicateACompanionThatIsShown() {
        assertThat(DuoPairing.persistableOrder(listOf(0, 1, 2), listOf(1, 3)))
            .containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun persistableOrder_neverDropsAHiddenCompanion() {
        // The companion's dock-side screen (4) is no longer in the order.
        assertThat(DuoPairing.persistableOrder(listOf(0, 2), listOf(1, 5)))
            .containsExactly(0, 1, 2, 5).inOrder()
    }

    @Test
    fun persistableOrder_respectsAMovedPage() {
        assertThat(DuoPairing.persistableOrder(listOf(2, 0), listOf(3, 1)))
            .containsExactly(2, 3, 0, 1).inOrder()
    }

    @Test
    fun coverThenPersist_roundTripsTheSavedOrder() {
        val saved = listOf(0, 1, 2, 3, 4, 5)
        val cover = DuoPairing.coverScreens(saved)
        val hidden = DuoPairing.hiddenCompanions(saved)
        assertThat(DuoPairing.persistableOrder(cover, hidden)).containsExactlyElementsIn(saved).inOrder()
    }

    @Test
    fun coverThenPersist_roundTripsAMovedPageOrder() {
        val saved = listOf(2, 3, 0, 1, 4, 5)
        val cover = DuoPairing.coverScreens(saved)
        val hidden = DuoPairing.hiddenCompanions(saved)
        assertThat(DuoPairing.persistableOrder(cover, hidden)).containsExactlyElementsIn(saved).inOrder()
    }

    @Test
    fun unfoldedRoundTrip_arrangeThenPersistGivesTheSavedOrder() {
        val saved = listOf(0, 1, 2, 3)
        val views = DuoPairing.arrangePairs(saved, swap = true)
        assertThat(views).containsExactly(1, 0, 3, 2).inOrder()
        assertThat(DuoPairing.persistableOrder(views, emptyList())).containsExactlyElementsIn(saved).inOrder()
    }
}
