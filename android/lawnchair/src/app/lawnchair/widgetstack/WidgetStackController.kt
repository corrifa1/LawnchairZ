/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for Expressive Launcher.
 * https://github.com/denson9874/ExpressiveLauncher
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import android.view.View
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import java.util.function.Predicate

/**
 * Keeps stack views and the model in step when launcher code acts on a widget that happens to
 * live in a stack. Launcher3 assumes widgets sit directly in a cell layout; these entry points are
 * called from small hooks in Launcher and Workspace.
 */
object WidgetStackController {

    @JvmStatic
    fun isStackChild(info: ItemInfo?) = info is LauncherAppWidgetInfo && info.container >= 0

    @JvmStatic
    fun writer(launcher: Launcher) = WidgetStackWriter(launcher.modelWriter, launcher.cellPosMapper)

    @JvmStatic
    fun findStackView(launcher: Launcher, stackId: Int): WidgetStackView? =
        launcher.workspace?.getViewByItemId(stackId) as? WidgetStackView

    /** Replaces the stack's view with a freshly inflated one. */
    @JvmStatic
    fun rebindStack(launcher: Launcher, stack: WidgetStackInfo) {
        val workspace = launcher.workspace ?: return
        findStackView(launcher, stack.id)?.let(workspace::removeWorkspaceItem)
        val view = launcher.itemInflater.inflateItem(stack) ?: return
        workspace.addInScreen(view, stack)
        workspace.requestLayout()
    }

    /**
     * Launcher.removeItem for a widget inside a stack. Without [deleteFromDb] this only detaches
     * the view, as when a pending widget re-inflates; otherwise the child is deleted and a stack
     * left with one widget turns back into that widget.
     */
    @JvmStatic
    fun removeChild(launcher: Launcher, view: View?, child: LauncherAppWidgetInfo, deleteFromDb: Boolean) {
        val stackView = findStackView(launcher, child.container)
        if (view != null) stackView?.removePage(view)
        if (!deleteFromDb) return
        val stack = stackView?.stackInfo ?: return
        writer(launcher).deleteChild(stack, child, launcher.appWidgetHolder)
        collapseIfNeeded(launcher, stack)
    }

    /** Launcher.bindAppWidget for a widget inside a stack: rebuilds the stack around it. */
    @JvmStatic
    fun bindChild(launcher: Launcher, child: LauncherAppWidgetInfo) {
        val stack = findStackView(launcher, child.container)?.stackInfo ?: return
        rebindStack(launcher, stack)
    }

    /**
     * Workspace.removeItemsByMatcher for a stack: drops matching children, for example when their
     * app is uninstalled. The model has already deleted their rows when [persistChanges] is set.
     */
    @JvmStatic
    fun removeMatchingChildren(
        launcher: Launcher,
        stackView: WidgetStackView,
        matcher: Predicate<ItemInfo>,
        persistChanges: Boolean,
    ) {
        val stack = stackView.stackInfo ?: return
        val matches = stack.contents.filter { matcher.test(it) }
        if (matches.isEmpty()) return
        matches.forEach { child ->
            stackView.pageViews.firstOrNull { it.tag === child }?.let(stackView::removePage)
            if (persistChanges) writer(launcher).detachChild(stack, child) else stack.remove(child)
        }
        if (persistChanges) collapseIfNeeded(launcher, stack)
    }

    /**
     * Turns a stack left with one widget back into that widget, or removes an empty stack. Empty
     * stacks are kept while [keepEmpty] is set, for the editor of a stack just placed from the
     * widget picker.
     */
    @JvmStatic
    @JvmOverloads
    fun collapseIfNeeded(launcher: Launcher, stack: WidgetStackInfo, keepEmpty: Boolean = false) {
        if (stack.size >= WidgetStackInfo.MIN_CHILDREN || (keepEmpty && stack.size == 0)) return
        val workspace = launcher.workspace ?: return
        val stackView = findStackView(launcher, stack.id)
        val survivor = writer(launcher).collapseIfNeeded(stack)
        stackView?.let {
            // Release the survivor's host view so it can be re-created on the workspace.
            it.pageViews.toList().forEach(it::removePage)
            workspace.removeWorkspaceItem(it)
        }
        if (survivor != null && survivor.container == Favorites.CONTAINER_DESKTOP) {
            launcher.bindAppWidget(survivor)
        }
    }
}
