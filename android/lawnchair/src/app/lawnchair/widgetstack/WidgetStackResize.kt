/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for Expressive Launcher.
 * https://github.com/denson9874/ExpressiveLauncher
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.graphics.Point
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo

/** Resize rules for a stack, used by AppWidgetResizeFrame. */
class WidgetStackResize private constructor(
    /** The span range every child supports. */
    val range: WidgetStackSizing.SpanRange,
    /** Resize directions every child allows. */
    val resizeMode: Int,
    /** Sizes to snap to, or null for free resizing. */
    private val presets: List<Point>?,
) {
    val minSpanX get() = range.minX
    val minSpanY get() = range.minY
    val maxSpanX get() = range.maxX
    val maxSpanY get() = range.maxY

    /**
     * In preset mode, replaces the frame's proposed cells `[cellX, cellY, spanX, spanY]` with the
     * nearest preset, anchored on the edge opposite the one being dragged. [oldSpan] is the span
     * before this step. No-op in free mode or when no preset fits.
     */
    fun snap(cells: IntArray, oldSpanX: Int, oldSpanY: Int, leftActive: Boolean, topActive: Boolean, countX: Int, countY: Int) {
        val presets = presets ?: return
        val (cellX, cellY, spanX, spanY) = cells.toList()
        val snapped = WidgetStackSizing.snapToPreset(
            spanX, spanY, presets, range, growingX = spanX > oldSpanX, growingY = spanY > oldSpanY,
        ) ?: return
        val right = cellX + spanX
        val bottom = cellY + spanY
        cells[0] = (if (leftActive) right - snapped.x else cellX).coerceIn(0, countX - snapped.x)
        cells[1] = (if (topActive) bottom - snapped.y else cellY).coerceIn(0, countY - snapped.y)
        cells[2] = snapped.x
        cells[3] = snapped.y
    }

    companion object {
        /** Builds the rules for [stack] on a [countX] x [countY] grid. */
        @JvmStatic
        fun forStack(stack: WidgetStackView, countX: Int, countY: Int): WidgetStackResize {
            val infos = stack.pageViews.mapNotNull {
                (it as? AppWidgetHostView)?.appWidgetInfo as? LauncherAppWidgetProviderInfo
            }
            val info = stack.stackInfo
            val current = Point(info?.spanX ?: 1, info?.spanY ?: 1)
            val range = WidgetStackSizing.intersectRanges(
                infos.map { WidgetStackSizing.SpanRange(it.minSpanX, it.minSpanY, it.maxSpanX, it.maxSpanY) },
                countX, countY, current,
            )
            val resizeMode = infos.fold(AppWidgetProviderInfo.RESIZE_BOTH) { mode, it -> mode and it.resizeMode }
            val preset = PreferenceManager2.getInstance(stack.context).widgetStackPresetSizes.firstCached()
            return WidgetStackResize(
                range,
                resizeMode,
                if (preset) WidgetStackSizing.presetSizes(countX, countY) else null,
            )
        }
    }
}
