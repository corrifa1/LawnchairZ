/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WidgetStackTouchTest {

    private val slop = 10

    @Test
    fun smallMovesStayInTheTapZone() {
        assertThat(WidgetStackTouch.movedPastSlop(0f, 0f, slop)).isFalse()
        assertThat(WidgetStackTouch.movedPastSlop(9f, -9f, slop)).isFalse()
    }

    @Test
    fun movingSlopInAnyDirectionLeavesTheTapZone() {
        assertThat(WidgetStackTouch.movedPastSlop(0f, 10f, slop)).isTrue()
        assertThat(WidgetStackTouch.movedPastSlop(0f, -25f, slop)).isTrue()
        assertThat(WidgetStackTouch.movedPastSlop(-10f, 0f, slop)).isTrue()
    }

    @Test
    fun aStillLongPressPicksUpTheStack() {
        assertThat(
            WidgetStackTouch.shouldForwardChildLongPress(movedPastSlop = false, isPaging = false, isSettling = false),
        ).isTrue()
    }

    @Test
    fun scrollingPagingOrSettlingNeverOpensTheStackMenu() {
        assertThat(
            WidgetStackTouch.shouldForwardChildLongPress(movedPastSlop = true, isPaging = false, isSettling = false),
        ).isFalse()
        assertThat(
            WidgetStackTouch.shouldForwardChildLongPress(movedPastSlop = false, isPaging = true, isSettling = false),
        ).isFalse()
        assertThat(
            WidgetStackTouch.shouldForwardChildLongPress(movedPastSlop = false, isPaging = false, isSettling = true),
        ).isFalse()
    }
}
