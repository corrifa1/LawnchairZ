/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import android.content.ComponentName
import android.database.sqlite.SQLiteDatabase
import android.graphics.Point
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.DbEntry
import com.android.launcher3.widget.WidgetManagerHelper

/** Grid-size migration support for widget stacks. */
object WidgetStackMigration {

    /** Span a widget is assumed to shrink to when its provider is unknown, as for plain widgets. */
    private const val UNKNOWN_MIN_SPAN = 2

    /**
     * Fills [entry] with the stack's children so the migration can identify the stack, move its
     * children with it, and know how small it can get. Returns false when the stack is empty.
     */
    @JvmStatic
    fun loadStackEntry(
        entry: DbEntry,
        db: SQLiteDatabase,
        tableName: String,
        widgets: WidgetManagerHelper,
    ): Boolean {
        val minSpans = ArrayList<Point>()
        db.query(
            tableName,
            arrayOf(Favorites._ID, Favorites.APPWIDGET_PROVIDER, Favorites.APPWIDGET_ID),
            "${Favorites.CONTAINER} = ${entry.id}",
            null, null, null, null,
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getInt(0)
                val provider = c.getString(1)
                val appWidgetId = c.getInt(2)
                entry.mFolderItems["$provider#$appWidgetId"] = setOf(id)
                val info = ComponentName.unflattenFromString(provider.orEmpty())
                    ?.let { widgets.getLauncherAppWidgetInfo(appWidgetId, it) }
                minSpans.add(info?.minSpans ?: Point(UNKNOWN_MIN_SPAN, UNKNOWN_MIN_SPAN))
            }
        }
        if (minSpans.isEmpty()) return false
        val min = WidgetStackSizing.intersectMinSpans(minSpans)
        entry.minSpanX = min.x.coerceIn(1, entry.spanX)
        entry.minSpanY = min.y.coerceIn(1, entry.spanY)
        return true
    }

    /** Migration id: two stacks match when they hold the same widgets. */
    @JvmStatic
    fun migrationId(entry: DbEntry): String =
        "stack:" + entry.mFolderItems.keys.sorted().joinToString(",")
}
