/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for Expressive Launcher.
 * https://github.com/denson9874/ExpressiveLauncher
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger.RestoreError
import com.android.launcher3.model.LoaderCursor
import com.android.launcher3.model.LoaderMemoryLogger
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.IntSparseArrayMap

/** Loader hooks for widget stacks, called from WorkspaceItemProcessor. */
object WidgetStackLoader {

    /** Reads the stack row under [c]. Its children are attached later by [WidgetStackLoaderRepair]. */
    @JvmStatic
    fun processStackRow(
        c: LoaderCursor,
        loadedItems: IntSparseArrayMap<ItemInfo>,
        memoryLogger: LoaderMemoryLogger?,
    ) {
        if (c.container != Favorites.CONTAINER_DESKTOP) {
            // Stacks are workspace-only. Its children become orphans and are deleted on repair.
            c.markDeleted(
                "Widget stack outside the workspace: id=${c.id}, container=${c.container}",
                RestoreError.INVALID_WIDGET_CONTAINER,
            )
            return
        }
        val stack = WidgetStackInfo()
        c.applyCommonProperties(stack)
        stack.spanX = c.spanX
        stack.spanY = c.spanY
        stack.options = c.options
        if (stack.spanX <= 0 || stack.spanY <= 0) {
            c.markDeleted(
                "Widget stack has invalid size: ${stack.spanX}x${stack.spanY}, id=${c.id}",
                RestoreError.INVALID_WIDGET_SIZE,
            )
            return
        }
        c.markRestored()
        c.checkAndAddItem(stack, loadedItems, memoryLogger)
    }

    /**
     * Whether a widget row at [container] may load. Positive containers are candidate stacks;
     * [WidgetStackLoaderRepair] deletes the ones that turn out not to be stacks.
     */
    @JvmStatic
    fun isValidWidgetContainer(container: Int) = container >= 0
}
