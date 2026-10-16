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
import android.graphics.Point
import app.lawnchair.widgetstack.WidgetStackSizing.SpanRange
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class WidgetStackSizingTest {

    @Test
    fun rangeIsTheIntersectionClampedToTheGrid() {
        val range = WidgetStackSizing.intersectRanges(
            listOf(SpanRange(2, 1, 5, 4), SpanRange(1, 2, 4, 9)),
            countX = 4, countY = 6, current = Point(2, 2),
        )
        assertThat(range).isEqualTo(SpanRange(2, 2, 4, 4))
    }

    @Test
    fun disjointAxisIsLockedToTheCurrentSpan() {
        val range = WidgetStackSizing.intersectRanges(
            listOf(SpanRange(1, 1, 2, 4), SpanRange(3, 1, 4, 4)),
            countX = 5, countY = 5, current = Point(2, 3),
        )
        assertThat(range.minX).isEqualTo(2)
        assertThat(range.maxX).isEqualTo(2)
        assertThat(range.minY).isEqualTo(1)
        assertThat(range.maxY).isEqualTo(4)
    }

    @Test
    fun noChildrenAllowsTheWholeGrid() {
        assertThat(WidgetStackSizing.intersectRanges(emptyList(), 4, 5, Point(2, 2)))
            .isEqualTo(SpanRange(1, 1, 4, 5))
    }

    @Test
    fun presetsAreSmallMediumLargeAndAdjustToSmallGrids() {
        assertThat(WidgetStackSizing.presetSizes(5, 6))
            .containsExactly(Point(2, 2), Point(4, 2), Point(4, 4)).inOrder()
        assertThat(WidgetStackSizing.presetSizes(3, 3))
            .containsExactly(Point(2, 2), Point(3, 2), Point(3, 3)).inOrder()
        // A tiny grid collapses duplicate presets.
        assertThat(WidgetStackSizing.presetSizes(2, 2)).containsExactly(Point(2, 2))
    }

    private val anySize = SpanRange(1, 1, 4, 6)
    private val presets = WidgetStackSizing.presetSizes(4, 6)

    @Test
    fun snapPicksTheNearestAllowedPreset() {
        assertThat(WidgetStackSizing.snapToPreset(4, 3, presets, anySize, oldSpanX = 4, oldSpanY = 2))
            .isEqualTo(Point(4, 4))
        assertThat(WidgetStackSizing.snapToPreset(1, 1, presets, anySize, oldSpanX = 2, oldSpanY = 2))
            .isEqualTo(Point(2, 2))
        // Presets the children cannot render are skipped.
        val wideOnly = SpanRange(3, 1, 4, 6)
        assertThat(WidgetStackSizing.snapToPreset(2, 2, presets, wideOnly, oldSpanX = 2, oldSpanY = 2))
            .isEqualTo(Point(4, 2))
        assertThat(WidgetStackSizing.snapToPreset(2, 2, presets, SpanRange(1, 1, 1, 1), 2, 2)).isNull()
    }

    @Test
    fun snapTieGoesInTheResizeDirection() {
        // Widening 2x2 by one column: halfway to 4x2, the drag direction wins.
        assertThat(WidgetStackSizing.snapToPreset(3, 2, presets, anySize, oldSpanX = 2, oldSpanY = 2))
            .isEqualTo(Point(4, 2))
        // Narrowing 4x2 by one column goes back to 2x2.
        assertThat(WidgetStackSizing.snapToPreset(3, 2, presets, anySize, oldSpanX = 4, oldSpanY = 2))
            .isEqualTo(Point(2, 2))
    }

    @Test
    fun verticalGrowthNeverSnapsSmaller() {
        // Growing a 2x3 stack downward ties between 2x2 and 4x4; only 4x4 is taller.
        assertThat(WidgetStackSizing.snapToPreset(2, 4, presets, anySize, oldSpanX = 2, oldSpanY = 3))
            .isEqualTo(Point(4, 4))
    }

    private fun preset() = WidgetStackResize(anySize, 0, presets)

    private fun drag(cells: IntArray, startX: Int, startY: Int, dx: Float, dy: Float, right: Boolean = false, bottom: Boolean = false, left: Boolean = false) =
        cells.also { preset().snapFromDrag(it, startX, startY, dx, dy, left, right, false, bottom, 4, 6) }

    @Test
    fun dragSnapsFromTheWholeDragAndStaysPut() {
        // Dragging the right edge of a 2x2 stack one column snaps to 4x2.
        val afterOne = drag(intArrayOf(0, 1, 2, 2), 2, 2, dx = 1f, dy = 0f, right = true)
        assertThat(afterOne.toList()).containsExactly(0, 1, 4, 2).inOrder()
        // The finger still near one column away must not bounce back to 2x2.
        val hovering = drag(afterOne.copyOf(), 2, 2, dx = 0.8f, dy = 0f, right = true)
        assertThat(hovering.toList()).containsExactly(0, 1, 4, 2).inOrder()
        // Dragging back to where it started returns to 2x2.
        val back = drag(afterOne.copyOf(), 2, 2, dx = 0f, dy = 0f, right = true)
        assertThat(back.toList()).containsExactly(0, 1, 2, 2).inOrder()
    }

    @Test
    fun leftEdgeDragKeepsTheRightEdge() {
        // A 2x2 stack at x=2 widened from the left grows toward x=0.
        val cells = drag(intArrayOf(2, 0, 2, 2), 2, 2, dx = -1f, dy = 0f, left = true)
        assertThat(cells.toList()).containsExactly(0, 0, 4, 2).inOrder()
    }

    @Test
    fun freeModeLeavesCellsAlone() {
        val cells = intArrayOf(0, 0, 3, 3)
        WidgetStackResize(anySize, 0, null).snapFromDrag(cells, 2, 2, 1f, 1f, false, true, false, true, 4, 6)
        assertThat(cells.toList()).containsExactly(0, 0, 3, 3).inOrder()
    }
}
