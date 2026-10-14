/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import android.view.MotionEvent
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher

/** Touch arbitration between widget stacks and the launcher's own vertical gestures. */
object WidgetStackTouch {

    /**
     * Whether [ev], in drag layer coordinates, starts on a stack that pages on vertical swipes.
     * The swipe-up (app drawer) and swipe-down (notifications, custom gestures) controllers check
     * this on ACTION_DOWN and leave such touches to the stack.
     */
    @JvmStatic
    fun isTouchOnPageableStack(launcher: Launcher, ev: MotionEvent): Boolean {
        val workspace = launcher.workspace ?: return false
        val dragLayer = launcher.dragLayer ?: return false
        val coord = FloatArray(2)
        for (i in 0 until workspace.childCount) {
            val page = workspace.getChildAt(i) as? CellLayout ?: continue
            val items = page.shortcutsAndWidgets
            for (j in 0 until items.childCount) {
                val stack = items.getChildAt(j) as? WidgetStackView ?: continue
                if (!stack.isShown || !stack.canPage()) continue
                coord[0] = ev.x
                coord[1] = ev.y
                dragLayer.mapCoordInSelfToDescendant(stack, coord)
                if (coord[0] >= 0 && coord[1] >= 0 && coord[0] < stack.width && coord[1] < stack.height) {
                    return true
                }
            }
        }
        return false
    }
}
