/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import app.lawnchair.widgetstack.WidgetStackUnstacker.Cells
import app.lawnchair.widgetstack.WidgetStackUnstacker.Stack
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WidgetStackUnstackerTest {

    @Test
    fun visibleChildKeepsTheStackCells() {
        val stack = Stack(Cells(10, screen = 0, x = 0, y = 0, spanX = 2, spanY = 2), activeRank = 1, childIds = listOf(11, 12))

        val placed = WidgetStackUnstacker.plan(listOf(stack), emptyList(), countX = 4, countY = 4)

        assertThat(placed).contains(Cells(12, 0, 0, 0, 2, 2))
    }

    @Test
    fun otherChildrenGoToFreeCellsOnTheSamePageFirst() {
        val stack = Stack(Cells(10, 0, 0, 0, 2, 2), activeRank = 0, childIds = listOf(11, 12, 13))
        val blocker = Cells(1, 0, 2, 0, 2, 2)

        val placed = WidgetStackUnstacker.plan(listOf(stack), listOf(blocker), countX = 4, countY = 4)
            .associateBy { it.id }

        assertThat(placed.getValue(11)).isEqualTo(Cells(11, 0, 0, 0, 2, 2))
        // The top row is taken, so the next widgets fill the bottom row.
        assertThat(placed.getValue(12)).isEqualTo(Cells(12, 0, 0, 2, 2, 2))
        assertThat(placed.getValue(13)).isEqualTo(Cells(13, 0, 2, 2, 2, 2))
    }

    @Test
    fun fullPagesSpillToLaterPagesThenNewOnes() {
        val stack = Stack(Cells(10, 1, 0, 0, 4, 4), activeRank = 0, childIds = listOf(11, 12, 13))
        val fullPage2 = Cells(2, 2, 0, 0, 4, 4)

        val placed = WidgetStackUnstacker.plan(listOf(stack), listOf(fullPage2), countX = 4, countY = 4)
            .associateBy { it.id }

        assertThat(placed.getValue(11).screen).isEqualTo(1)
        // Pages 1 and 2 are full: new pages after the last one are created in order.
        assertThat(placed.getValue(12).screen).isEqualTo(3)
        assertThat(placed.getValue(13).screen).isEqualTo(4)
    }

    @Test
    fun nothingOverlaps() {
        val stacks = listOf(
            Stack(Cells(10, 0, 0, 0, 2, 2), 0, listOf(11, 12)),
            Stack(Cells(20, 0, 2, 0, 2, 2), 1, listOf(21, 22, 23)),
        )
        val placed = WidgetStackUnstacker.plan(stacks, emptyList(), 4, 4)

        val occupied = HashSet<Triple<Int, Int, Int>>()
        placed.forEach { c ->
            for (x in c.x until c.x + c.spanX) for (y in c.y until c.y + c.spanY) {
                assertThat(occupied.add(Triple(c.screen, x, y))).isTrue()
            }
        }
        assertThat(placed.map { it.id }).containsExactly(11, 12, 21, 22, 23)
    }
}
