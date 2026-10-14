/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for Expressive Launcher.
 * https://github.com/denson9874/ExpressiveLauncher
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants
import android.view.View
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.Alarm
import com.android.launcher3.CellLayout
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.NavigableAppWidgetHostView
import com.android.launcher3.widget.PendingAppWidgetHostView
import com.android.launcher3.widget.WidgetManagerHelper

/**
 * Creates stacks by dropping a widget onto another widget of the same span, and adds widgets to
 * an existing stack by dropping them onto it. Works like folder creation: the drag has to hover
 * over the middle of the target for a moment, then the target shrinks slightly to show the drop
 * will stack. Hovering outside the middle keeps the normal reorder behaviour.
 */
class WidgetStackDropHelper {

    private var candidate: NavigableAppWidgetHostView? = null
    private var armed: NavigableAppWidgetHostView? = null
    private var cueAnimator: ValueAnimator? = null
    private val alarm = Alarm().apply { setOnAlarmListener { arm() } }

    /**
     * Called for each drag-over event with the drag's visual center in [layout] coordinates.
     * Returns true while the drag hovers a stack target, in which case the caller skips reorder.
     */
    fun onDragOver(launcher: Launcher, layout: CellLayout?, d: DragObject, center: FloatArray): Boolean {
        val target = layout?.let { findTarget(launcher, it, d, center) }
        if (target !== candidate) {
            disarm()
            candidate = target
            if (target != null) alarm.setAlarm(HOVER_DELAY_MS) else alarm.cancelAlarm()
        }
        return target != null
    }

    /**
     * Completes a drop onto an armed target. [cell] is the dragged widget's view. Returns false
     * when the drop is not a stack drop and should be handled normally.
     */
    fun onDrop(launcher: Launcher, cell: View?, d: DragObject): Boolean {
        val target = armed ?: run {
            reset()
            return false
        }
        val dragged = d.dragInfo as? LauncherAppWidgetInfo ?: run {
            reset()
            return false
        }
        val layout = target.parent?.parent as? CellLayout ?: run {
            reset()
            return false
        }
        reset()

        // Take the dragged widget's view off the workspace; the stack inflates its own. A live
        // widget drag carries the view inside the drag view instead of a cell layout.
        val sourceLayout = cell?.parent?.parent as? CellLayout
        if (sourceLayout != null) {
            sourceLayout.removeView(cell)
        } else if (cell is LauncherAppWidgetHostView) {
            d.dragView?.detachContentView(false)
        }

        val writer = WidgetStackController.writer(launcher)
        val stack: WidgetStackInfo
        if (target is WidgetStackView) {
            stack = target.stackInfo ?: return true
            val rank = stack.activeRank + 1
            if (!writer.addChild(stack, dragged, rank)) return true
            writer.setActiveRank(stack, rank)
        } else {
            val targetInfo = target.tag as LauncherAppWidgetInfo
            val lp = target.layoutParams as CellLayoutLayoutParams
            val screenId = launcher.workspace.getCellLayoutId(layout)
            layout.removeView(target)
            stack = writer.createStack(
                Favorites.CONTAINER_DESKTOP, screenId, lp.cellX, lp.cellY,
                targetInfo.spanX, targetInfo.spanY, listOf(targetInfo, dragged),
            )
            // Show the widget that was just dropped, as iOS does.
            writer.setActiveRank(stack, 1)
        }
        WidgetStackController.rebindStack(launcher, stack)
        WidgetStackController.findStackView(launcher, stack.id)?.playCreateAnimation()
        return true
    }

    /**
     * The drag left the workspace. DragController calls this right before a drop, so an armed
     * target is kept for [onDrop]; [reset] clears it when the drag ends or re-enters.
     */
    fun onDragExit() {
        alarm.cancelAlarm()
        candidate = null
    }

    /** Clears all hover state and the visual cue. */
    fun reset() {
        alarm.cancelAlarm()
        disarm()
        candidate = null
    }

    private fun arm() {
        val target = candidate ?: return
        armed = target
        target.performHapticFeedback(
            HapticFeedbackConstants.SEGMENT_TICK,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
        animateCue(target, ARMED_SCALE)
    }

    private fun disarm() {
        armed?.let { animateCue(it, 1f) }
        armed = null
    }

    private fun animateCue(target: NavigableAppWidgetHostView, to: Float) {
        cueAnimator?.cancel()
        cueAnimator = ValueAnimator.ofFloat(target.reorderBounceScale, to).apply {
            duration = CUE_DURATION_MS
            addUpdateListener { target.reorderBounceScale = it.animatedValue as Float }
            start()
        }
    }

    private fun findTarget(launcher: Launcher, layout: CellLayout, d: DragObject, center: FloatArray): NavigableAppWidgetHostView? {
        // Workspace pages only: stacks never go in the hotseat.
        if (layout.parent !== launcher.workspace) return null
        if (!PreferenceManager2.getInstance(launcher).widgetStacks.firstCached()) return null
        val dragged = d.dragInfo as? LauncherAppWidgetInfo ?: return null
        if (dragged.isCustomWidget || WidgetStackController.isStackChild(dragged)) return null

        val cell = IntArray(2)
        layout.pointToCellExact(center[0].toInt(), center[1].toInt(), cell)
        val view = layout.getChildAt(cell[0], cell[1]) as? NavigableAppWidgetHostView ?: return null
        if (view.tag === dragged || !isInStackZone(layout, view, center)) return null

        return when {
            view is WidgetStackView -> {
                val stack = view.stackInfo ?: return null
                if (stack.size >= WidgetStackInfo.MAX_CHILDREN) return null
                if (!canRenderAt(launcher, dragged, stack.spanX, stack.spanY)) return null
                view
            }
            view is LauncherAppWidgetHostView && view !is PendingAppWidgetHostView -> {
                val info = view.tag as? LauncherAppWidgetInfo ?: return null
                if (info.isCustomWidget || info.container != Favorites.CONTAINER_DESKTOP) return null
                if (info.spanX != dragged.spanX || info.spanY != dragged.spanY) return null
                view
            }
            else -> null
        }
    }

    /** The middle half of the target, so edges still reorder as usual. */
    private fun isInStackZone(layout: CellLayout, view: View, center: FloatArray): Boolean {
        val container = layout.shortcutsAndWidgets
        val left = container.left + view.left
        val top = container.top + view.top
        val insetX = view.width / 4f
        val insetY = view.height / 4f
        return center[0] in (left + insetX)..(left + view.width - insetX) &&
            center[1] in (top + insetY)..(top + view.height - insetY)
    }

    private fun canRenderAt(launcher: Launcher, widget: LauncherAppWidgetInfo, spanX: Int, spanY: Int): Boolean {
        if (PreferenceManager2.getInstance(launcher).widgetUnlimitedSize.firstCached()) return true
        val info = WidgetManagerHelper(launcher).getLauncherAppWidgetInfo(widget.appWidgetId, widget.providerName)
            ?: return false
        return spanX in info.minSpanX..info.maxSpanX && spanY in info.minSpanY..info.maxSpanY
    }

    companion object {
        /** Same idea as folder creation's hover delay. */
        private const val HOVER_DELAY_MS = 350L
        private const val CUE_DURATION_MS = 150L
        private const val ARMED_SCALE = 0.92f
    }
}
