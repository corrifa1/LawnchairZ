/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for Expressive Launcher.
 * https://github.com/denson9874/ExpressiveLauncher
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import android.graphics.Point
import kotlin.math.abs

/** Pure sizing rules for widget stacks. */
object WidgetStackSizing {

    /** An inclusive span range a widget supports, in cells. */
    data class SpanRange(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {
        fun contains(spanX: Int, spanY: Int) = spanX in minX..maxX && spanY in minY..maxY
    }

    /**
     * The smallest span every child supports: the largest of the children's minimum spans, per
     * axis. Non-positive spans count as 1.
     */
    fun intersectMinSpans(minSpans: Collection<Point>): Point = Point(
        minSpans.maxOfOrNull { it.x.coerceAtLeast(1) } ?: 1,
        minSpans.maxOfOrNull { it.y.coerceAtLeast(1) } ?: 1,
    )

    /**
     * The span range every child supports, clamped to the grid. When the children have no size in
     * common on an axis, that axis is locked to [current] so the stack never shrinks below a
     * child's minimum or grows past another's maximum.
     */
    fun intersectRanges(ranges: Collection<SpanRange>, countX: Int, countY: Int, current: Point): SpanRange {
        if (ranges.isEmpty()) return SpanRange(1, 1, countX, countY)
        var minX = ranges.maxOf { it.minX.coerceAtLeast(1) }
        var minY = ranges.maxOf { it.minY.coerceAtLeast(1) }
        var maxX = ranges.minOf { it.maxX.coerceAtLeast(1) }.coerceAtMost(countX)
        var maxY = ranges.minOf { it.maxY.coerceAtLeast(1) }.coerceAtMost(countY)
        if (minX > maxX) {
            minX = current.x
            maxX = current.x
        }
        if (minY > maxY) {
            minY = current.y
            maxY = current.y
        }
        return SpanRange(minX, minY, maxX, maxY)
    }

    /**
     * Preset sizes, iOS-style small, medium and large (2x2, 4x2, 4x4), narrowed to fit grids with
     * fewer than four columns or rows. Wider grids keep the 4-cell presets.
     */
    fun presetSizes(countX: Int, countY: Int): List<Point> {
        val wide = countX.coerceIn(1, 4)
        val tall = countY.coerceIn(1, 4)
        val narrow = countX.coerceIn(1, 2)
        val short = countY.coerceIn(1, 2)
        return listOf(Point(narrow, short), Point(wide, short), Point(wide, tall)).distinct()
    }

    /**
     * Snaps a proposed span to the nearest preset in [range]. On a tie the preset in the
     * direction the user is resizing wins. Returns null when no preset fits.
     */
    fun snapToPreset(
        spanX: Int,
        spanY: Int,
        presets: List<Point>,
        range: SpanRange,
        growingX: Boolean,
        growingY: Boolean,
    ): Point? = presets
        .filter { range.contains(it.x, it.y) }
        .minWithOrNull(
            compareBy<Point> { abs(it.x - spanX) + abs(it.y - spanY) }
                .thenBy { if (growingX) -it.x else it.x }
                .thenBy { if (growingY) -it.y else it.y },
        )
}
