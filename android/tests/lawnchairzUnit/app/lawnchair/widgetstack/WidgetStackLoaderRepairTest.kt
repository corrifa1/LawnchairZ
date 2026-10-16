/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import android.app.Application
import android.content.ComponentName
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class WidgetStackLoaderRepairTest {

    private val provider = ComponentName("pkg", "pkg.Widget")

    private fun stack(id: Int, options: Int = 0) = WidgetStackInfo().apply {
        this.id = id
        container = Favorites.CONTAINER_DESKTOP
        screenId = 1
        cellX = 2
        cellY = 3
        spanX = 4
        spanY = 2
        this.options = options
    }

    private fun widget(id: Int, container: Int, rank: Int = 0) =
        LauncherAppWidgetInfo(id + 1000, provider).apply {
            this.id = id
            this.container = container
            this.rank = rank
            spanX = 2
            spanY = 2
        }

    @Test
    fun childrenAreAttachedInRankOrderAndNormalized() {
        val s = stack(10)
        val items = listOf<ItemInfo>(s, widget(1, 10, rank = 4), widget(2, 10, rank = 1))

        val plan = WidgetStackLoaderRepair.plan(items)

        assertThat(s.contents.map { it.id }).containsExactly(2, 1).inOrder()
        assertThat(s.contents.map { it.rank }).containsExactly(0, 1).inOrder()
        assertThat(plan.deleteIds).isEmpty()
        // Ranks and spans were rewritten, so both children are persisted.
        assertThat(plan.dirty.map { it.id }).containsExactly(1, 2)
        s.contents.forEach { assertThat(it.spanX).isEqualTo(4) }
    }

    @Test
    fun cleanStackNeedsNoWrites() {
        val s = stack(10)
        val a = widget(1, 10, rank = 0).apply { spanX = 4; spanY = 2; screenId = 1; cellX = 0; cellY = 0 }
        val b = widget(2, 10, rank = 1).apply { spanX = 4; spanY = 2; screenId = 1; cellX = 0; cellY = 0 }

        val plan = WidgetStackLoaderRepair.plan(listOf(s, a, b))

        assertThat(plan.isEmpty).isTrue()
    }

    @Test
    fun widgetsWhoseParentIsNotAStackAreDeleted() {
        val folder = FolderInfo().apply { id = 20; container = Favorites.CONTAINER_DESKTOP }
        val items = listOf(folder, widget(1, 20), widget(2, 99), widget(3, Favorites.CONTAINER_DESKTOP))

        val plan = WidgetStackLoaderRepair.plan(items)

        assertThat(plan.deleteIds).containsExactly(1, 2)
    }

    @Test
    fun emptyStackIsDeleted() {
        val plan = WidgetStackLoaderRepair.plan(listOf(stack(10)))
        assertThat(plan.deleteIds).containsExactly(10)
    }

    @Test
    fun singleChildStackBecomesAPlainWidgetInTheStackCells() {
        val child = widget(1, 10, rank = 3)

        val plan = WidgetStackLoaderRepair.plan(listOf(stack(10), child))

        assertThat(plan.deleteIds).containsExactly(10)
        assertThat(plan.promoted).containsExactly(child)
        assertThat(child.container).isEqualTo(Favorites.CONTAINER_DESKTOP)
        assertThat(listOf(child.screenId, child.cellX, child.cellY, child.spanX, child.spanY, child.rank))
            .containsExactly(1, 2, 3, 4, 2, 0).inOrder()
    }

    @Test
    fun outOfRangeActiveRankIsClampedAndPersisted() {
        val s = stack(10, options = WidgetStackOptions.withActiveRank(0, 7))

        val plan = WidgetStackLoaderRepair.plan(listOf(s, widget(1, 10, 0), widget(2, 10, 1)))

        assertThat(s.activeRank).isEqualTo(1)
        assertThat(WidgetStackOptions.activeRank(s.options)).isEqualTo(1)
        assertThat(plan.dirty).contains(s)
    }
}
