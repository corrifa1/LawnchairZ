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
import com.android.launcher3.LauncherConstants
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.PendingRequestArgs
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.PendingAddWidgetInfo
import com.android.launcher3.widget.util.WidgetSizes
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

    /** Launcher.removeItem for a whole stack: removes the view, and with [deleteFromDb] every widget. */
    @JvmStatic
    fun removeStack(launcher: Launcher, view: View?, stack: WidgetStackInfo, deleteFromDb: Boolean) {
        val stackView = view ?: findStackView(launcher, stack.id)
        stackView?.let { launcher.workspace?.removeWorkspaceItem(it) }
        if (deleteFromDb) writer(launcher).deleteStack(stack, launcher.appWidgetHolder)
        notifyChanged(stack)
    }

    /**
     * Launcher.completeAddAppWidget for a widget requested for a stack (its container is the
     * stack id). Called once when the widget is bound and, for widgets with a configure activity,
     * again when configuration finishes. Returns false when [request] is not for a stack.
     */
    @JvmStatic
    fun completeAddToStack(
        launcher: Launcher,
        widget: LauncherAppWidgetInfo,
        request: ItemInfo,
        configurePending: Boolean,
    ): Boolean {
        if (request.container < 0) return false
        val stack = findStackView(launcher, request.container)?.stackInfo
        if (stack == null) {
            launcher.appWidgetHolder.deleteAppWidgetId(widget.appWidgetId)
            return true
        }
        val existing = stack.contents.firstOrNull { it.appWidgetId == widget.appWidgetId }
        if (existing != null) {
            // Configuration finished.
            existing.restoreStatus = LauncherAppWidgetInfo.RESTORE_COMPLETED
            launcher.modelWriter.updateItemInDatabase(existing)
        } else {
            widget.restoreStatus =
                if (configurePending) LauncherAppWidgetInfo.FLAG_UI_NOT_READY else LauncherAppWidgetInfo.RESTORE_COMPLETED
            val writer = writer(launcher)
            val rank = request.rank.coerceIn(0, stack.size)
            if (!writer.addChild(stack, widget, rank)) {
                launcher.appWidgetHolder.deleteAppWidgetId(widget.appWidgetId)
                return true
            }
            writer.setActiveRank(stack, rank)
        }
        rebindStack(launcher, stack)
        notifyChanged(stack)
        // Back to the editor once the widget is ready (not while its setup screen is open).
        if (!configurePending) WidgetStackEditor.onAddFinished(launcher, stack)
        return true
    }

    /** A widget requested for a stack was not bound or configured; drop its placeholder. */
    @JvmStatic
    fun onAddCanceled(launcher: Launcher, appWidgetId: Int, request: ItemInfo) {
        if (request.container < 0) return
        val stackView = findStackView(launcher, request.container) ?: return
        val stack = stackView.stackInfo ?: return
        val child = stack.contents.firstOrNull { it.appWidgetId == appWidgetId }
        if (child != null) {
            stackView.pageViews.firstOrNull { it.tag === child }?.let(stackView::removePage)
            writer(launcher).deleteChild(stack, child, launcher.appWidgetHolder)
            notifyChanged(stack)
        }
        WidgetStackEditor.onAddFinished(launcher, stack)
    }

    /**
     * Starts adding [provider] to [stack], through the usual bind-permission and configure
     * flow. The widget lands right after the visible one.
     */
    @JvmStatic
    fun startAddWidget(launcher: Launcher, stack: WidgetStackInfo, provider: LauncherAppWidgetProviderInfo) {
        if (stack.size >= WidgetStackInfo.MAX_CHILDREN) return
        val pending = PendingAddWidgetInfo(provider, stack.id)
        pending.rank = if (stack.size == 0) 0 else stack.activeRank + 1
        pending.bindOptions = WidgetSizes.getWidgetSizeOptions(launcher, provider.provider, stack.spanX, stack.spanY)
        launcher.addPendingItem(pending, stack.id, stack.screenId, intArrayOf(0, 0), stack.spanX, stack.spanY)
    }

    /** Opens the configure activity of a reconfigurable child. */
    @JvmStatic
    fun reconfigure(launcher: Launcher, child: LauncherAppWidgetInfo) {
        launcher.setWaitingForResult(PendingRequestArgs.forWidgetInfo(child.appWidgetId, null, child))
        launcher.appWidgetHolder.startConfigActivity(
            launcher,
            child.appWidgetId,
            LauncherConstants.ActivityCodes.REQUEST_RECONFIGURE_APPWIDGET,
        )
    }

    /**
     * Moves [child] out of its stack to the first free cells, preferring the current page and
     * adding a page when the workspace is full. Returns false when no space could be made.
     */
    @JvmStatic
    fun moveChildToWorkspace(launcher: Launcher, child: LauncherAppWidgetInfo): Boolean {
        val stackView = findStackView(launcher, child.container) ?: return false
        val stack = stackView.stackInfo ?: return false
        val workspace = launcher.workspace ?: return false
        val cell = IntArray(2)
        val screenId = findSpace(launcher, child.spanX, child.spanY, cell) ?: return false

        stackView.pageViews.firstOrNull { it.tag === child }?.let(stackView::removePage)
        val writer = writer(launcher)
        writer.detachChild(stack, child)
        launcher.modelWriter.moveItemInDatabase(child, Favorites.CONTAINER_DESKTOP, screenId, cell[0], cell[1])
        workspace.post { launcher.bindAppWidget(child) }
        collapseIfNeeded(launcher, stack, keepEmpty = true)
        notifyChanged(stack)
        return true
    }

    /**
     * Turns a plain widget into a one-widget stack in the same cells, ready for the editor to add
     * more. Such a stack collapses back if the editor closes with it still holding one widget.
     */
    @JvmStatic
    fun createStackFrom(launcher: Launcher, widgetView: View): WidgetStackInfo? {
        val widget = widgetView.tag as? LauncherAppWidgetInfo ?: return null
        if (widget.container != Favorites.CONTAINER_DESKTOP || widget.isCustomWidget) return null
        val layout = widgetView.parent?.parent as? com.android.launcher3.CellLayout ?: return null
        val pos = launcher.cellPosMapper.mapModelToPresenter(widget)
        layout.removeView(widgetView)
        val stack = writer(launcher).createStack(
            Favorites.CONTAINER_DESKTOP, pos.screenId, pos.cellX, pos.cellY,
            widget.spanX, widget.spanY, listOf(widget),
        )
        rebindStack(launcher, stack)
        findStackView(launcher, stack.id)?.playCreateAnimation()
        return stack
    }

    /**
     * Places an empty stack of [spanX] x [spanY] in the first free cells, shows its page and
     * returns it, for the widget picker's "Widget stack" entry. Returns null when there is no room.
     */
    @JvmStatic
    fun placeEmptyStack(launcher: Launcher, spanX: Int = 2, spanY: Int = 2): WidgetStackInfo? {
        val cell = IntArray(2)
        val screenId = findSpace(launcher, spanX, spanY, cell) ?: return null
        val stack = writer(launcher).createStack(
            Favorites.CONTAINER_DESKTOP, screenId, cell[0], cell[1], spanX, spanY, emptyList(),
        )
        rebindStack(launcher, stack)
        val workspace = launcher.workspace
        workspace.snapToPage(workspace.getPageIndexForScreenId(screenId))
        findStackView(launcher, stack.id)?.playCreateAnimation()
        return stack
    }

    private fun findSpace(launcher: Launcher, spanX: Int, spanY: Int, outCell: IntArray): Int? {
        val workspace = launcher.workspace
        val order = workspace.screenOrder
        val current = order.get(workspace.currentPage.coerceIn(0, order.size() - 1))
        val candidates = listOf(current) + (0 until order.size()).map { order.get(it) }
        for (screenId in candidates) {
            if (workspace.getScreenWithId(screenId)?.findCellForSpan(outCell, spanX, spanY) == true) {
                return screenId
            }
        }
        workspace.addExtraEmptyScreens()
        val added = workspace.commitExtraEmptyScreens()
        if (added.isEmpty) return null
        val screenId = added.array.get(0)
        return screenId.takeIf { workspace.getScreenWithId(it)?.findCellForSpan(outCell, spanX, spanY) == true }
    }

    // The open editor, if any, listens for changes made outside it (async adds, removals).
    private var changeListener: ((WidgetStackInfo) -> Unit)? = null

    @JvmStatic
    fun setChangeListener(listener: ((WidgetStackInfo) -> Unit)?) {
        changeListener = listener
    }

    private fun notifyChanged(stack: WidgetStackInfo) {
        changeListener?.invoke(stack)
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
