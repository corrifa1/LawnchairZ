/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import app.lawnchair.widgetstack.WidgetStackSizing.RESIZE_BOTH
import app.lawnchair.widgetstack.WidgetStackSizing.RESIZE_HORIZONTAL
import app.lawnchair.widgetstack.WidgetStackSizing.RESIZE_NONE
import app.lawnchair.widgetstack.WidgetStackSizing.Rules
import app.lawnchair.widgetstack.WidgetStackSizing.SpanRange
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WidgetStackResizeOptionsTest {

    private val countX = 4
    private val countY = 6

    // A stack holding a fixed 2x2 widget: its children allow no size change.
    private val fixed = Rules(SpanRange(2, 2, 2, 2), RESIZE_NONE)

    private fun apply(children: Rules, force: Boolean, unlimited: Boolean) =
        WidgetStackSizing.applyResizeOptions(children, countX, countY, force, unlimited)

    @Test
    fun withoutOptionsTheChildrenRulesStand() {
        val children = Rules(SpanRange(2, 1, 4, 3), RESIZE_HORIZONTAL)
        assertThat(apply(children, force = false, unlimited = false)).isEqualTo(children)
        assertThat(WidgetStackSizing.canResize(fixed, countX, countY)).isFalse()
    }

    @Test
    fun bothOptionsMakeAnyStackFreelyResizable() {
        val rules = apply(fixed, force = true, unlimited = true)
        assertThat(rules).isEqualTo(Rules(SpanRange(1, 1, countX, countY), RESIZE_BOTH))
        assertThat(WidgetStackSizing.canResize(rules, countX, countY)).isTrue()
    }

    @Test
    fun unlimitedOpensTheWholeGridInTheAllowedDirections() {
        val children = Rules(SpanRange(2, 2, 3, 2), RESIZE_HORIZONTAL)
        val rules = apply(children, force = false, unlimited = true)
        assertThat(rules.range).isEqualTo(SpanRange(1, 1, countX, countY))
        assertThat(rules.resizeMode).isEqualTo(RESIZE_BOTH)
        // Like a single widget, "Remove size constraints" alone doesn't unlock a non-resizable one.
        assertThat(apply(fixed, force = false, unlimited = true).resizeMode).isEqualTo(RESIZE_NONE)
    }

    @Test
    fun forceAloneAllowsBothDirectionsWithinTheChildrenRange() {
        val children = Rules(SpanRange(2, 1, 4, 3), RESIZE_HORIZONTAL)
        val rules = apply(children, force = true, unlimited = false)
        assertThat(rules).isEqualTo(Rules(children.range, RESIZE_BOTH))
        // A range with no room to grow or shrink still can't resize.
        assertThat(WidgetStackSizing.canResize(apply(fixed, force = true, unlimited = false), countX, countY)).isFalse()
    }
}
