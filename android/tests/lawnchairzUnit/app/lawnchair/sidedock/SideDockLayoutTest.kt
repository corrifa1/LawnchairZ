/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.sidedock

import android.app.Application
import app.lawnchair.DeviceProfileOverrides
import com.android.launcher3.DeviceProfile
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.celllayout.CellPosMapper
import com.android.launcher3.celllayout.CellPosMapper.CellPos
import com.android.launcher3.model.data.ItemInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The side dock's (iPhone Duo-style) dock position mapping, slot size and dock count rules. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class SideDockLayoutTest {

    private fun hotseatItem(rank: Int) = ItemInfo().apply {
        container = CONTAINER_HOTSEAT
        screenId = rank
    }

    private fun sideDockMapper(icons: Int) = CellPosMapper(true, icons, 1, 1, true)

    // Rank order

    @Test
    fun sideDockListsRanksFromTheTop() {
        val mapper = sideDockMapper(icons = 5)
        for (rank in 0 until 5) {
            assertThat(mapper.mapModelToPresenter(hotseatItem(rank)))
                .isEqualTo(CellPos(0, rank, 0))
        }
    }

    @Test
    fun sideDockMapsCellsBackToTheSameRank() {
        val mapper = sideDockMapper(icons = 5)
        for (rank in 0 until 5) {
            val cell = mapper.mapModelToPresenter(hotseatItem(rank))
            val model = mapper.mapPresenterToModel(cell.cellX, cell.cellY, cell.screenId, CONTAINER_HOTSEAT)
            assertThat(model.screenId).isEqualTo(rank)
        }
    }

    @Test
    fun stockLandscapeDockStillListsRanksFromTheBottom() {
        val mapper = CellPosMapper(true, 5, 1, 1, false)
        assertThat(mapper.mapModelToPresenter(hotseatItem(0))).isEqualTo(CellPos(0, 4, 0))
        assertThat(mapper.mapPresenterToModel(0, 4, 0, CONTAINER_HOTSEAT).screenId).isEqualTo(0)
    }

    @Test
    fun topDownFlagIsIgnoredForABottomDock() {
        val mapper = CellPosMapper(false, 5, 1, 1, true)
        assertThat(mapper.mapModelToPresenter(hotseatItem(3))).isEqualTo(CellPos(3, 0, 0))
        assertThat(mapper.mapPresenterToModel(3, 0, 0, CONTAINER_HOTSEAT).screenId).isEqualTo(3)
    }

    @Test
    fun sideDockLeavesWorkspaceItemsAlone() {
        val item = ItemInfo().apply {
            container = CONTAINER_DESKTOP
            cellX = 2
            cellY = 3
            screenId = 1
        }
        assertThat(sideDockMapper(icons = 5).mapModelToPresenter(item)).isEqualTo(CellPos(2, 3, 1))
    }

    // Icon spacing

    @Test
    fun spacingZeroUsesTheIconSlot() {
        assertThat(DeviceProfile.calculateSideDockSlot(300, 180, 0f)).isEqualTo(180)
    }

    @Test
    fun spacingOneUsesAWholeRow() {
        assertThat(DeviceProfile.calculateSideDockSlot(300, 180, 1f)).isEqualTo(300)
    }

    @Test
    fun spacingInBetweenInterpolates() {
        assertThat(DeviceProfile.calculateSideDockSlot(300, 180, 0.4f)).isEqualTo(228)
    }

    @Test
    fun slotNeverExceedsARow() {
        // Rows shorter than the icon slot: every spacing gives one row.
        assertThat(DeviceProfile.calculateSideDockSlot(150, 180, 0f)).isEqualTo(150)
        assertThat(DeviceProfile.calculateSideDockSlot(150, 180, 1f)).isEqualTo(150)
    }

    @Test
    fun spacingOutsideTheSliderRangeIsClamped() {
        assertThat(DeviceProfile.calculateSideDockSlot(300, 180, -1f)).isEqualTo(180)
        assertThat(DeviceProfile.calculateSideDockSlot(300, 180, 3f)).isEqualTo(300)
    }

    // Dock count on a foldable

    @Test
    fun bottomDockShowsAtMostTheUnfoldedCount() {
        assertThat(DeviceProfileOverrides.shownHotseatIconsOnFoldable(7, 5, sideDock = false))
            .isEqualTo(5)
        assertThat(DeviceProfileOverrides.shownHotseatIconsOnFoldable(5, 8, sideDock = false))
            .isEqualTo(5)
    }

    @Test
    fun sideDockShowsTheFoldedCountOnBothScreens() {
        assertThat(DeviceProfileOverrides.shownHotseatIconsOnFoldable(7, 5, sideDock = true))
            .isEqualTo(7)
        assertThat(DeviceProfileOverrides.shownHotseatIconsOnFoldable(5, 8, sideDock = true))
            .isEqualTo(5)
    }
}
