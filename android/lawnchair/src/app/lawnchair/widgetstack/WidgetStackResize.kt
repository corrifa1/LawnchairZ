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
import android.content.res.ColorStateList
import android.graphics.Point
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.Themes
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
        /**
         * Adds the frame's stack button, opposite the reconfigure button: "Edit stack" on a
         * stack, or "Create stack" on a plain workspace widget while stacks are enabled.
         */
        @JvmStatic
        fun attachFrameButton(frame: AbstractFloatingView, widgetView: View, launcher: Launcher) {
            val reconfigure = frame.findViewById<View>(R.id.widget_reconfigure_button) ?: return
            val container = reconfigure.parent as? FrameLayout ?: return
            val isStack = widgetView is WidgetStackView
            val widget = widgetView.tag as? LauncherAppWidgetInfo
            val canCreate = !isStack && widget != null && !widget.isCustomWidget &&
                widget.container == Favorites.CONTAINER_DESKTOP &&
                PreferenceManager2.getInstance(launcher).widgetStacks.firstCached()
            if (!isStack && !canCreate) return

            val margin = launcher.resources.getDimensionPixelSize(R.dimen.widget_reconfigure_button_margin)
            val padding = launcher.resources.getDimensionPixelSize(R.dimen.widget_reconfigure_button_padding)
            val button = ImageButton(launcher).apply {
                setImageResource(if (isStack) R.drawable.ic_edit else R.drawable.ic_widget_stack)
                imageTintList = ColorStateList.valueOf(Themes.getAttrColor(launcher, R.attr.workspaceAccentColor))
                setPadding(padding, padding, padding, padding)
                val ripple = TypedValue()
                launcher.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
                setBackgroundResource(ripple.resourceId)
                contentDescription = launcher.getString(
                    if (isStack) R.string.widget_stack_edit else R.string.widget_stack_create,
                )
                setOnClickListener {
                    frame.close(false)
                    val stack = if (widgetView is WidgetStackView) {
                        widgetView.stackInfo
                    } else {
                        WidgetStackController.createStackFrom(launcher, widgetView)
                    }
                    stack?.let { WidgetStackEditor.show(launcher, it) }
                }
            }
            container.addView(
                button,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.START,
                ).apply {
                    bottomMargin = margin
                    marginStart = margin
                },
            )
        }

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
