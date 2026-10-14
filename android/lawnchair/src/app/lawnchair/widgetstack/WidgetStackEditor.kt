/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.ui.preferences.components.reorderable.ReorderHapticFeedbackType
import app.lawnchair.ui.preferences.components.reorderable.rememberReorderHapticFeedback
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.WidgetManagerHelper
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * The "Edit stack" sheet: reorder widgets with a long-press drag, remove them (with undo), move
 * them to the home screen, add widgets that fit the stack's size, open a widget's settings, and
 * toggle Smart Rotate.
 */
object WidgetStackEditor {

    /** Stack whose editor closed to add a widget, and reopens when that finishes. */
    private var reopenFor = ItemInfo.NO_ID

    @JvmStatic
    fun show(launcher: Launcher, stack: WidgetStackInfo) {
        AbstractFloatingView.closeAllOpenViews(launcher)
        val sheet = ComposeBottomSheet<Launcher>(launcher)
        val state = EditorState(launcher, stack) { sheet.close(true) }
        sheet.setContent { EditorContent(state) }
        sheet.addOnCloseListener { state.onClosed() }
        WidgetStackController.setChangeListener { if (it === stack) state.refresh() }
        sheet.show()
    }

    /** Re-opens the editor once a widget added from it was placed or its setup was canceled. */
    @JvmStatic
    fun onAddFinished(launcher: Launcher, stack: WidgetStackInfo) {
        if (reopenFor != stack.id) return
        reopenFor = ItemInfo.NO_ID
        launcher.workspace.post { show(launcher, stack) }
    }

    private class EditorState(
        val launcher: Launcher,
        val stack: WidgetStackInfo,
        val close: () -> Unit,
    ) {
        private val writer = WidgetStackController.writer(launcher)
        private val widgets = WidgetManagerHelper(launcher)
        val items = mutableStateListOf<LauncherAppWidgetInfo>().apply { addAll(stack.contents) }
        var smartRotate by mutableStateOf(stack.smartRotate)
        var picking by mutableStateOf(false)
        var pendingRemoval by mutableStateOf<Pair<LauncherAppWidgetInfo, Int>?>(null)
        private var closed = false

        fun refresh() {
            items.clear()
            items.addAll(stack.contents)
        }

        private fun stackView() = WidgetStackController.findStackView(launcher, stack.id)

        fun providerOf(child: LauncherAppWidgetInfo): LauncherAppWidgetProviderInfo? =
            widgets.getLauncherAppWidgetInfo(child.appWidgetId, child.providerName)

        fun moveInList(from: Int, to: Int) {
            if (from in items.indices && to in items.indices) items.add(to, items.removeAt(from))
        }

        /** Persists the order shown in the list and re-orders the stack's pages in place. */
        fun commitOrder() {
            items.forEachIndexed { target, child ->
                val from = stack.contents.indexOf(child)
                if (from >= 0 && from != target) writer.reorder(stack, from, target)
            }
            stackView()?.syncPagesToInfo()
        }

        fun remove(child: LauncherAppWidgetInfo) {
            commitRemoval()
            val rank = stack.contents.indexOf(child)
            stackView()?.let { view -> view.pageViews.firstOrNull { it.tag === child }?.let(view::removePage) }
            writer.detachChild(stack, child)
            refresh()
            pendingRemoval = child to rank
        }

        fun undoRemoval() {
            val (child, rank) = pendingRemoval ?: return
            pendingRemoval = null
            writer.addChild(stack, child, rank)
            WidgetStackController.rebindStack(launcher, stack)
            refresh()
        }

        /** Deletes a removed widget for good, which frees its widget id. */
        fun commitRemoval() {
            val (child, _) = pendingRemoval ?: return
            pendingRemoval = null
            launcher.modelWriter.deleteWidgetInfo(child, launcher.appWidgetHolder, "removed from widget stack")
        }

        fun moveOut(child: LauncherAppWidgetInfo) {
            commitRemoval()
            if (!WidgetStackController.moveChildToWorkspace(launcher, child)) {
                Toast.makeText(launcher, R.string.out_of_space, Toast.LENGTH_SHORT).show()
            }
            refresh()
            if (stack.size < WidgetStackInfo.MIN_CHILDREN) close()
        }

        fun toggleSmartRotate(enabled: Boolean) {
            writer.setSmartRotate(stack, enabled)
            smartRotate = enabled
        }

        fun reconfigure(child: LauncherAppWidgetInfo) {
            WidgetStackController.reconfigure(launcher, child)
        }

        fun add(provider: LauncherAppWidgetProviderInfo) {
            commitRemoval()
            reopenFor = stack.id
            close()
            WidgetStackController.startAddWidget(launcher, stack, provider)
        }

        /** Widgets that can render at the stack's size, grouped by app label. */
        suspend fun loadProviders(): List<ProviderEntry> = withContext(Dispatchers.IO) {
            val unlimited = PreferenceManager2.getInstance(launcher).widgetUnlimitedSize.firstCached()
            val pm = launcher.packageManager
            val density = launcher.resources.displayMetrics.densityDpi
            widgets.getAllProviders(null)
                .map { LauncherAppWidgetProviderInfo.fromProviderInfo(launcher, it) }
                .filter { !it.isCustomWidget }
                .filter {
                    unlimited || (
                        stack.spanX in it.minSpanX..it.maxSpanX && stack.spanY in it.minSpanY..it.maxSpanY
                        )
                }
                .map { info ->
                    val appLabel = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
                    }.getOrDefault(info.provider.packageName)
                    ProviderEntry(info, info.loadLabel(pm) ?: appLabel, appLabel, info.loadIcon(launcher, density))
                }
                .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
        }

        fun onClosed() {
            if (closed) return
            closed = true
            commitRemoval()
            WidgetStackController.setChangeListener(null)
            // Leaving to add a widget keeps the stack, even an empty one, for the widget to land in.
            if (reopenFor != stack.id) {
                WidgetStackController.collapseIfNeeded(launcher, stack)
                launcher.workspace.stripEmptyScreens()
            }
        }
    }

    private class ProviderEntry(
        val info: LauncherAppWidgetProviderInfo,
        val label: String,
        val appLabel: String,
        val icon: Drawable?,
    )

    @Composable
    private fun EditorContent(state: EditorState) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (state.picking) ProviderPicker(state) else StackList(state)
        }
    }

    @Composable
    private fun StackList(state: EditorState) {
        val haptic = rememberReorderHapticFeedback()
        val listState = rememberLazyListState()
        val reorderState = rememberReorderableLazyListState(listState) { from, to ->
            state.moveInList(from.index, to.index)
            haptic.performHapticFeedback(ReorderHapticFeedbackType.MOVE)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.widget_stack_edit), style = MaterialTheme.typography.titleLarge)
                Text(
                    pluralStringResource(
                        R.plurals.widget_stack_summary,
                        state.items.size,
                        state.items.size,
                        state.stack.spanX,
                        state.stack.spanY,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = state.close) { Text(stringResource(R.string.widget_stack_done)) }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { state.toggleSmartRotate(!state.smartRotate) }
                .padding(vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.widget_stack_smart_rotate), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.widget_stack_smart_rotate_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.smartRotate, onCheckedChange = state::toggleSmartRotate)
        }

        if (state.items.size > 1) {
            Text(
                stringResource(R.string.widget_stack_reorder_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        LazyColumn(state = listState, modifier = Modifier.heightIn(max = 360.dp)) {
            itemsIndexed(state.items, key = { _, child -> child.id }) { index, child ->
                ReorderableItem(reorderState, key = child.id) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                    val moveUp = stringResource(R.string.widget_stack_move_up)
                    val moveDown = stringResource(R.string.widget_stack_move_down)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = elevation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .longPressDraggableHandle(
                                onDragStarted = { haptic.performHapticFeedback(ReorderHapticFeedbackType.START) },
                                onDragStopped = {
                                    haptic.performHapticFeedback(ReorderHapticFeedbackType.END)
                                    state.commitOrder()
                                },
                            )
                            .semantics {
                                customActions = buildList {
                                    if (index > 0) {
                                        add(
                                            CustomAccessibilityAction(moveUp) {
                                                state.moveInList(index, index - 1)
                                                state.commitOrder()
                                                true
                                            },
                                        )
                                    }
                                    if (index < state.items.size - 1) {
                                        add(
                                            CustomAccessibilityAction(moveDown) {
                                                state.moveInList(index, index + 1)
                                                state.commitOrder()
                                                true
                                            },
                                        )
                                    }
                                }
                            },
                    ) {
                        ChildRow(state, child, isShowing = child === state.stack.activeChild)
                    }
                }
            }
        }

        state.pendingRemoval?.let { removal ->
            LaunchedEffect(removal) {
                delay(UNDO_WINDOW_MS)
                state.commitRemoval()
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(
                    stringResource(R.string.widget_stack_widget_removed),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = state::undoRemoval) { Text(stringResource(R.string.undo)) }
            }
        }

        val full = state.items.size >= WidgetStackInfo.MAX_CHILDREN
        FilledTonalButton(
            onClick = { state.picking = true },
            enabled = !full,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (full) R.string.widget_stack_full else R.string.widget_stack_add))
        }
    }

    @Composable
    private fun ChildRow(state: EditorState, child: LauncherAppWidgetInfo, isShowing: Boolean) {
        val provider = remember(child) { state.providerOf(child) }
        val context = state.launcher
        val label = remember(child) {
            provider?.loadLabel(context.packageManager) ?: child.providerName.packageName
        }
        val icon = remember(child) { provider?.loadIcon(context, context.resources.displayMetrics.densityDpi) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Icon(Icons.Rounded.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            WidgetIcon(icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                val status = when {
                    child.restoreStatus != LauncherAppWidgetInfo.RESTORE_COMPLETED ->
                        stringResource(R.string.widget_stack_needs_setup)
                    isShowing -> stringResource(R.string.widget_stack_showing)
                    else -> null
                }
                if (status != null) {
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (provider?.isReconfigurable == true) {
                IconButton(onClick = { state.reconfigure(child) }) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.gadget_setup_text))
                }
            }
            IconButton(onClick = { state.moveOut(child) }) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(R.string.action_move_to_workspace))
            }
            IconButton(onClick = { state.remove(child) }) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.widget_stack_remove_widget))
            }
        }
    }

    @Composable
    private fun WidgetIcon(icon: Drawable?) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(painter = rememberDrawablePainter(icon), contentDescription = null, modifier = Modifier.size(32.dp))
            }
        }
    }

    @Composable
    private fun ProviderPicker(state: EditorState) {
        var entries by remember { mutableStateOf<List<ProviderEntry>?>(null) }
        LaunchedEffect(Unit) { entries = state.loadProviders() }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.picking = false }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.widget_stack_back))
            }
            Column {
                Text(stringResource(R.string.widget_stack_choose_widget), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.widget_stack_fits_size, state.stack.spanX, state.stack.spanY),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val loaded = entries
        when {
            loaded == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            loaded.isEmpty() -> Text(
                stringResource(R.string.widget_stack_no_fitting_widgets),
                modifier = Modifier.padding(16.dp),
            )
            else -> LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                loaded.groupBy { it.appLabel }.forEach { (app, group) ->
                    item(key = "header:$app") {
                        Text(
                            app,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(group, key = { "${it.info.provider.flattenToString()}/${it.info.profile.hashCode()}" }) { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { state.add(entry.info) }
                                .padding(vertical = 10.dp),
                        ) {
                            WidgetIcon(entry.icon)
                            Text(entry.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }

    private const val UNDO_WINDOW_MS = 4000L
}
