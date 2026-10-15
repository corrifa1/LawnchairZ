/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
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

    /** A stack's span range and resize directions (AppWidgetProviderInfo.RESIZE_* flags). */
    data class Rules(val range: SpanRange, val resizeMode: Int)

    /**
     * Applies the launcher's widget resize options to the rules its children allow, the same
     * way AppWidgetResizeFrame does for a single widget: [force] ("Enforce widget resizing")
     * allows both directions, and [unlimited] ("Remove size constraints") allows any span from
     * one cell to the whole grid in every direction the stack can resize.
     */
    fun applyResizeOptions(children: Rules, countX: Int, countY: Int, force: Boolean, unlimited: Boolean): Rules {
        var mode = if (force) RESIZE_BOTH else children.resizeMode
        var range = children.range
        if (unlimited) {
            range = SpanRange(1, 1, countX.coerceAtLeast(1), countY.coerceAtLeast(1))
            if (mode != RESIZE_NONE) mode = RESIZE_BOTH
        }
        return Rules(range, mode)
    }

    /** Whether [rules] let the stack change size in at least one direction, as the frame decides. */
    fun canResize(rules: Rules, countX: Int, countY: Int): Boolean {
        val r = rules.range
        val horizontal = rules.resizeMode and RESIZE_HORIZONTAL != 0 &&
            r.minX < countX && r.maxX > 1 && r.minX < r.maxX
        val vertical = rules.resizeMode and RESIZE_VERTICAL != 0 &&
            r.minY < countY && r.maxY > 1 && r.minY < r.maxY
        return horizontal || vertical
    }

    // AppWidgetProviderInfo's flags, kept here so the rules stay plain JVM code.
    const val RESIZE_NONE = 0
    const val RESIZE_HORIZONTAL = 1
    const val RESIZE_VERTICAL = 2
    const val RESIZE_BOTH = RESIZE_HORIZONTAL or RESIZE_VERTICAL

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
     * Snaps a proposed span to the nearest preset in [range]. On a tie, the axis being resized
     * decides: a preset in the direction of the drag wins (larger when growing, smaller when
     * shrinking), then the one that changes the other axis least. Returns null when no preset
     * fits.
     */
    fun snapToPreset(
        spanX: Int,
        spanY: Int,
        presets: List<Point>,
        range: SpanRange,
        oldSpanX: Int,
        oldSpanY: Int,
    ): Point? {
        val resizingX = spanX != oldSpanX
        val resizingY = spanY != oldSpanY
        // Signed so that "further in the drag direction" sorts first.
        fun alongDrag(p: Point) =
            (if (resizingX) (if (spanX > oldSpanX) -p.x else p.x) else 0) +
                (if (resizingY) (if (spanY > oldSpanY) -p.y else p.y) else 0)
        fun offOtherAxis(p: Point) =
            (if (resizingX) 0 else abs(p.x - oldSpanX)) + (if (resizingY) 0 else abs(p.y - oldSpanY))
        return presets
            .filter { range.contains(it.x, it.y) }
            .minWithOrNull(
                compareBy<Point> { abs(it.x - spanX) + abs(it.y - spanY) }
                    .thenBy(::alongDrag)
                    .thenBy(::offOtherAxis),
            )
    }
}
