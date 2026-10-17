/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import android.app.AlertDialog
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.AppWidgetResizeFrame
import com.android.launcher3.CellLayout
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.views.OptionsPopupView
import com.android.launcher3.views.OptionsPopupView.OptionItem

/**
 * The long-press menu of a stack: Edit stack and Delete stack. Like an app icon's menu, it opens
 * with the drag and closes once the stack is actually moved. Plain widgets keep having no menu.
 */
object WidgetStackPopup {

    /** Shows the menu and returns the condition that defers the drag until the finger moves. */
    @JvmStatic
    fun show(launcher: Launcher, stackView: WidgetStackView): DragOptions.PreDragCondition? {
        val stack = stackView.stackInfo ?: return null
        val bounds = Rect()
        launcher.dragLayer.getDescendantRectRelativeToSelf(stackView, bounds)
        val items = buildList {
            add(
                OptionItem(launcher, R.string.widget_stack_edit, R.drawable.ic_edit, LauncherEvent.IGNORE) {
                    WidgetStackEditor.show(launcher, stack)
                    true
                },
            )
            // The menu holds the drag back until the finger moves, so lifting it never reaches
            // the drop that opens a widget's resize frame. Offer the frame here instead.
            resizeFrameOpener(launcher, stackView)?.let { openFrame ->
                add(
                    OptionItem(launcher, R.string.widget_stack_resize, R.drawable.ic_aspect_ratio, LauncherEvent.IGNORE) {
                        // After this menu has closed, since the frame closes other floating views.
                        stackView.post(openFrame)
                        true
                    },
                )
            }
            add(
                OptionItem(launcher, R.string.widget_stack_delete, R.drawable.ic_remove_no_shadow, LauncherEvent.IGNORE) {
                    confirmDelete(launcher, stack)
                    true
                },
            )
        }
        val popup = OptionsPopupView.show<Launcher>(launcher, RectF(bounds), items, true) ?: return null

        // Close the menu as soon as the stack is really being dragged.
        val dragController = launcher.dragController
        dragController.addDragListener(object : DragController.DragListener {
            override fun onDragStart(dragObject: DragObject, options: DragOptions) {
                popup.close(true)
                dragController.removeDragListener(this)
            }

            override fun onDragEnd() {
                dragController.removeDragListener(this)
            }
        })

        val threshold = launcher.resources.getDimensionPixelSize(R.dimen.deep_shortcuts_start_drag_threshold)
        return object : DragOptions.PreDragCondition {
            override fun shouldStartDrag(distanceDragged: Double) = distanceDragged > threshold

            override fun onPreDragStart(dragObject: DragObject) {
                // Keep the stack in place under its menu until the drag really starts.
                stackView.visibility = View.VISIBLE
            }

            override fun onPreDragEnd(dragObject: DragObject, dragStarted: Boolean) {
                stackView.visibility = if (dragStarted) View.INVISIBLE else View.VISIBLE
            }
        }
    }

    /**
     * Returns an action that opens the resize frame on [stackView], or null when the stack can't
     * change size (its widgets and the resize options allow no handle) or the home screen is locked.
     */
    private fun resizeFrameOpener(launcher: Launcher, stackView: WidgetStackView): Runnable? {
        val cellLayout = stackView.parent?.parent as? CellLayout ?: return null
        // The workspace only offers the frame on the home screen, never in the dock.
        if (stackView.stackInfo?.container != Favorites.CONTAINER_DESKTOP) return null
        if (PreferenceManager2.getInstance(launcher).lockHomeScreen.firstCached()) return null
        val countX = cellLayout.countX
        val countY = cellLayout.countY
        if (!WidgetStackResize.forStack(stackView, countX, countY).canResize(countX, countY)) return null
        return Runnable {
            if (stackView.isAttachedToWindow && stackView.parent?.parent === cellLayout) {
                AppWidgetResizeFrame.showForWidget(stackView, cellLayout)
            }
        }
    }

    /** Asks before deleting a stack and every widget in it. */
    @JvmStatic
    fun confirmDelete(launcher: Launcher, stack: WidgetStackInfo) {
        AlertDialog.Builder(launcher)
            .setTitle(R.string.widget_stack_delete_title)
            .setMessage(launcher.resources.getQuantityString(R.plurals.widget_stack_delete_message, stack.size, stack.size))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.widget_stack_delete) { _, _ ->
                WidgetStackController.removeStack(launcher, null, stack, deleteFromDb = true)
                launcher.workspace.stripEmptyScreens()
            }
            .show()
    }
}
