/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for Expressive Launcher.
 * https://github.com/denson9874/ExpressiveLauncher
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import android.app.Application
import android.content.ComponentName
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class WidgetStackInfoTest {

    private val provider = ComponentName("pkg", "pkg.Widget")

    private fun widget(id: Int, rank: Int = 0) = LauncherAppWidgetInfo(id, provider).apply {
        this.id = id
        this.rank = rank
    }

    private fun stackOf(vararg ids: Int) = WidgetStackInfo().apply {
        id = 50
        spanX = 4
        spanY = 2
        setContents(ids.mapIndexed { rank, id -> widget(id, rank) })
    }

    private fun WidgetStackInfo.ids() = contents.map { it.id }

    @Test
    fun optionsPackSmartRotateAndActiveRankIndependently() {
        var options = 0
        options = WidgetStackOptions.withActiveRank(options, 7)
        options = WidgetStackOptions.withSmartRotate(options, true)
        assertThat(WidgetStackOptions.activeRank(options)).isEqualTo(7)
        assertThat(WidgetStackOptions.isSmartRotate(options)).isTrue()

        options = WidgetStackOptions.withActiveRank(options, 2)
        options = WidgetStackOptions.withSmartRotate(options, false)
        assertThat(WidgetStackOptions.activeRank(options)).isEqualTo(2)
        assertThat(WidgetStackOptions.isSmartRotate(options)).isFalse()
    }

    @Test
    fun setContentsSortsByRankThenIdAndRenumbers() {
        val stack = WidgetStackInfo().apply { id = 9 }
        val changed = stack.setContents(listOf(widget(3, 5), widget(1, 5), widget(2, 0)))

        assertThat(stack.ids()).containsExactly(2, 1, 3).inOrder()
        assertThat(stack.contents.map { it.rank }).containsExactly(0, 1, 2).inOrder()
        assertThat(stack.contents.map { it.container }).containsExactly(9, 9, 9)
        assertThat(changed.map { it.id }).containsExactly(1, 3)
    }

    @Test
    fun activeRankIsClampedToContents() {
        val stack = stackOf(1, 2, 3)
        stack.activeRank = 9
        assertThat(stack.activeRank).isEqualTo(2)
        stack.activeRank = -4
        assertThat(stack.activeRank).isEqualTo(0)
    }

    @Test
    fun addKeepsTheVisibleChild() {
        val stack = stackOf(1, 2, 3)
        stack.activeRank = 1 // widget 2

        val newChild = widget(4)
        val changed = stack.add(newChild, 0)

        assertThat(stack.ids()).containsExactly(4, 1, 2, 3).inOrder()
        assertThat(stack.activeChild?.id).isEqualTo(2)
        assertThat(newChild.container).isEqualTo(50)
        assertThat(changed.map { it.id }).containsExactly(4, 1, 2, 3)
    }

    @Test
    fun addToEmptyStackShowsTheChild() {
        val stack = WidgetStackInfo().apply { id = 50 }
        stack.add(widget(1))
        assertThat(stack.activeChild?.id).isEqualTo(1)
    }

    @Test
    fun removingTheVisibleChildShowsTheNextOne() {
        val stack = stackOf(1, 2, 3)
        stack.activeRank = 1

        stack.remove(stack.contents[1])

        assertThat(stack.ids()).containsExactly(1, 3).inOrder()
        assertThat(stack.activeChild?.id).isEqualTo(3)
    }

    @Test
    fun removingTheLastVisibleChildShowsTheNewLast() {
        val stack = stackOf(1, 2, 3)
        stack.activeRank = 2

        stack.remove(stack.contents[2])

        assertThat(stack.activeChild?.id).isEqualTo(2)
    }

    @Test
    fun removingAChildBeforeTheVisibleOneKeepsItVisible() {
        val stack = stackOf(1, 2, 3)
        stack.activeRank = 2

        val changed = stack.remove(stack.contents[0])

        assertThat(stack.activeChild?.id).isEqualTo(3)
        assertThat(changed.map { it.id }).containsExactly(2, 3)
    }

    @Test
    fun moveKeepsTheVisibleChildAndRenumbers() {
        val stack = stackOf(1, 2, 3, 4)
        stack.activeRank = 2 // widget 3

        stack.move(0, 3)

        assertThat(stack.ids()).containsExactly(2, 3, 4, 1).inOrder()
        assertThat(stack.contents.map { it.rank }).containsExactly(0, 1, 2, 3).inOrder()
        assertThat(stack.activeChild?.id).isEqualTo(3)
    }

    @Test
    fun movingTheVisibleChildFollowsIt() {
        val stack = stackOf(1, 2, 3, 4)
        stack.activeRank = 0

        stack.move(0, 2)

        assertThat(stack.activeChild?.id).isEqualTo(1)
        assertThat(stack.activeRank).isEqualTo(2)
    }

    @Test
    fun invalidMoveIsIgnored() {
        val stack = stackOf(1, 2)
        assertThat(stack.move(0, 5)).isEmpty()
        assertThat(stack.move(1, 1)).isEmpty()
        assertThat(stack.ids()).containsExactly(1, 2).inOrder()
    }

    @Test
    fun syncChildGeometryCopiesTheStackSpan() {
        val stack = stackOf(1, 2)
        stack.contents[0].spanX = 2
        stack.screenId = 3

        val changed = stack.syncChildGeometry()

        assertThat(changed).hasSize(2)
        stack.contents.forEach {
            assertThat(it.spanX).isEqualTo(4)
            assertThat(it.spanY).isEqualTo(2)
            assertThat(it.screenId).isEqualTo(3)
        }
        assertThat(stack.syncChildGeometry()).isEmpty()
    }

    @Test
    fun activeRankAfterMoveCoversEveryCase() {
        // Moving another child across the visible one shifts it by one.
        assertThat(WidgetStackOrdering.activeAfterMove(active = 2, from = 0, to = 3)).isEqualTo(1)
        assertThat(WidgetStackOrdering.activeAfterMove(active = 1, from = 3, to = 0)).isEqualTo(2)
        // Moves that stay on one side leave it alone.
        assertThat(WidgetStackOrdering.activeAfterMove(active = 0, from = 2, to = 3)).isEqualTo(0)
        assertThat(WidgetStackOrdering.activeAfterMove(active = 3, from = 0, to = 1)).isEqualTo(3)
    }

    @Test
    fun minSpanIntersectionTakesTheLargestMinimumPerAxis() {
        val min = WidgetStackSizing.intersectMinSpans(
            listOf(android.graphics.Point(2, 1), android.graphics.Point(1, 3), android.graphics.Point(0, 0)),
        )
        assertThat(min.x).isEqualTo(2)
        assertThat(min.y).isEqualTo(3)
        val empty = WidgetStackSizing.intersectMinSpans(emptyList())
        assertThat(empty.x).isEqualTo(1)
        assertThat(empty.y).isEqualTo(1)
    }
}
