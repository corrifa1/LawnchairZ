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

    @Test
    fun snapPicksTheNearestAllowedPreset() {
        val presets = WidgetStackSizing.presetSizes(4, 6)
        val any = SpanRange(1, 1, 4, 6)
        assertThat(WidgetStackSizing.snapToPreset(4, 3, presets, any, growingX = false, growingY = true))
            .isEqualTo(Point(4, 4))
        assertThat(WidgetStackSizing.snapToPreset(1, 1, presets, any, growingX = false, growingY = false))
            .isEqualTo(Point(2, 2))
        // Presets the children cannot render are skipped.
        val wideOnly = SpanRange(3, 1, 4, 6)
        assertThat(WidgetStackSizing.snapToPreset(2, 2, presets, wideOnly, growingX = false, growingY = false))
            .isEqualTo(Point(4, 2))
        assertThat(WidgetStackSizing.snapToPreset(2, 2, presets, SpanRange(1, 1, 1, 1), false, false)).isNull()
    }

    @Test
    fun snapTieGoesInTheResizeDirection() {
        val presets = listOf(Point(2, 2), Point(4, 2))
        val any = SpanRange(1, 1, 4, 4)
        assertThat(WidgetStackSizing.snapToPreset(3, 2, presets, any, growingX = true, growingY = false))
            .isEqualTo(Point(4, 2))
        assertThat(WidgetStackSizing.snapToPreset(3, 2, presets, any, growingX = false, growingY = false))
            .isEqualTo(Point(2, 2))
    }
}
