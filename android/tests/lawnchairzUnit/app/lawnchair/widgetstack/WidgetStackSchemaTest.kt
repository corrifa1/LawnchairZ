/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.model.DatabaseHelper
import com.android.launcher3.model.DbDowngradeHelper
import com.android.launcher3.model.DbEntry
import com.android.launcher3.widget.WidgetManagerHelper
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Database schema 33: the downgrade script and grid-migration reading of stacks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class WidgetStackSchemaTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        db = SQLiteDatabase.create(null)
        Favorites.addTableToDb(db, 0, false)
    }

    @After
    fun tearDown() = db.close()

    private fun insert(
        id: Int,
        itemType: Int,
        container: Int,
        rank: Int = 0,
        options: Int = 0,
        screen: Int = 0,
        cellX: Int = 0,
        cellY: Int = 0,
        spanX: Int = 1,
        spanY: Int = 1,
    ) {
        db.insert(
            Favorites.TABLE_NAME,
            null,
            ContentValues().apply {
                put(Favorites._ID, id)
                put(Favorites.ITEM_TYPE, itemType)
                put(Favorites.CONTAINER, container)
                put(Favorites.RANK, rank)
                put(Favorites.OPTIONS, options)
                put(Favorites.SCREEN, screen)
                put(Favorites.CELLX, cellX)
                put(Favorites.CELLY, cellY)
                put(Favorites.SPANX, spanX)
                put(Favorites.SPANY, spanY)
                put(Favorites.APPWIDGET_ID, id + 1000)
                put(Favorites.APPWIDGET_PROVIDER, "pkg/pkg.Widget$id")
            },
        )
    }

    private fun insertStack(id: Int, activeRank: Int, screen: Int = 2, cellX: Int = 1, cellY: Int = 3) = insert(
        id, Favorites.ITEM_TYPE_WIDGET_STACK, Favorites.CONTAINER_DESKTOP,
        options = WidgetStackOptions.withActiveRank(WidgetStackOptions.FLAG_SMART_ROTATE, activeRank),
        screen = screen, cellX = cellX, cellY = cellY, spanX = 4, spanY = 2,
    )

    private fun insertChild(id: Int, stack: Int, rank: Int) =
        insert(id, Favorites.ITEM_TYPE_APPWIDGET, stack, rank = rank, spanX = 4, spanY = 2)

    private fun downgradeHelper(): DbDowngradeHelper =
        context.resources.openRawResource(R.raw.downgrade_schema).use {
            DbDowngradeHelper.parse(it.readBytes())
        }

    private data class Row(
        val id: Int,
        val container: Int,
        val screen: Int,
        val cellX: Int,
        val cellY: Int,
        val spanX: Int,
        val spanY: Int,
    )

    private fun rows(): List<Row> = db.query(
        Favorites.TABLE_NAME,
        arrayOf(
            Favorites._ID, Favorites.CONTAINER, Favorites.SCREEN,
            Favorites.CELLX, Favorites.CELLY, Favorites.SPANX, Favorites.SPANY,
        ),
        null, null, null, null, Favorites._ID,
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(Row(c.getInt(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4), c.getInt(5), c.getInt(6)))
            }
        }
    }

    @Test
    fun downgradeSchemaMatchesTheDatabaseVersion() {
        assertThat(downgradeHelper().version).isEqualTo(DatabaseHelper.SCHEMA_VERSION)
        assertThat(DatabaseHelper.SCHEMA_VERSION).isEqualTo(33)
    }

    @Test
    fun downgradeKeepsEachStacksVisibleChildInTheStackCells() {
        insert(1, Favorites.ITEM_TYPE_APPLICATION, Favorites.CONTAINER_DESKTOP)
        insertStack(10, activeRank = 1, screen = 2, cellX = 1, cellY = 3)
        insertChild(11, 10, rank = 0)
        insertChild(12, 10, rank = 1)
        insertChild(13, 10, rank = 2)
        insertStack(20, activeRank = 0, screen = 0, cellX = 0, cellY = 0)
        insertChild(21, 20, rank = 0)
        insertChild(22, 20, rank = 1)

        downgradeHelper().onDowngrade(db, 33, 32)

        assertThat(rows()).containsExactly(
            Row(1, Favorites.CONTAINER_DESKTOP, 0, 0, 0, 1, 1),
            Row(12, Favorites.CONTAINER_DESKTOP, 2, 1, 3, 4, 2),
            Row(21, Favorites.CONTAINER_DESKTOP, 0, 0, 0, 4, 2),
        )
    }

    @Test
    fun downgradeWithStaleActiveRankKeepsTheNearestChild() {
        insertStack(10, activeRank = 9)
        insertChild(11, 10, rank = 0)
        insertChild(12, 10, rank = 1)

        downgradeHelper().onDowngrade(db, 33, 32)

        assertThat(rows().map { it.id }).containsExactly(12)
    }

    @Test
    fun downgradeDropsEmptyStacksAndStacksOutsideTheWorkspace() {
        insertStack(10, activeRank = 0)
        insert(20, Favorites.ITEM_TYPE_WIDGET_STACK, Favorites.CONTAINER_HOTSEAT)
        insertChild(21, 20, rank = 0)

        downgradeHelper().onDowngrade(db, 33, 32)

        assertThat(rows()).isEmpty()
    }

    @Test
    fun downgradeChainsToOlderVersions() {
        insertStack(10, activeRank = 0)
        insertChild(11, 10, rank = 0)
        insertChild(12, 10, rank = 1)

        downgradeHelper().onDowngrade(db, 33, 31)

        assertThat(rows().map { it.id }).containsExactly(11)
    }

    @Test
    fun gridMigrationReadsStackChildrenAndMinSpan() {
        insertStack(10, activeRank = 0)
        insertChild(11, 10, rank = 0)
        insertChild(12, 10, rank = 1)
        val entry = DbEntry().apply {
            id = 10
            itemType = Favorites.ITEM_TYPE_WIDGET_STACK
            spanX = 4
            spanY = 2
        }

        val loaded = WidgetStackMigration.loadStackEntry(
            entry, db, Favorites.TABLE_NAME, WidgetManagerHelper(context),
        )

        assertThat(loaded).isTrue()
        assertThat(entry.mFolderItems.values.flatten()).containsExactly(11, 12)
        // Unknown providers are assumed to shrink to 2x2, clamped to the stack's own span.
        assertThat(entry.minSpanX).isEqualTo(2)
        assertThat(entry.minSpanY).isEqualTo(2)

        val same = DbEntry().apply {
            itemType = Favorites.ITEM_TYPE_WIDGET_STACK
            mFolderItems.putAll(entry.mFolderItems)
        }
        assertThat(same).isEqualTo(entry)
    }

    @Test
    fun gridMigrationRejectsEmptyStacks() {
        insertStack(10, activeRank = 0)
        val entry = DbEntry().apply {
            id = 10
            itemType = Favorites.ITEM_TYPE_WIDGET_STACK
            spanX = 4
            spanY = 2
        }
        assertThat(
            WidgetStackMigration.loadStackEntry(entry, db, Favorites.TABLE_NAME, WidgetManagerHelper(context)),
        ).isFalse()
    }
}
