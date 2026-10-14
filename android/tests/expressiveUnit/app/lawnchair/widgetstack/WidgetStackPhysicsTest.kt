/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WidgetStackPhysicsTest {

    private val minFling = 1f

    private fun settle(position: Float, velocity: Float, start: Int, count: Int = 5) =
        WidgetStackPhysics.settleTarget(position, velocity, start, count, minFling)

    @Test
    fun slowReleaseSettlesOnTheNearestPage() {
        assertThat(settle(1.4f, 0.2f, start = 1)).isEqualTo(1)
        assertThat(settle(1.6f, 0.2f, start = 1)).isEqualTo(2)
        assertThat(settle(0.6f, -0.2f, start = 1)).isEqualTo(1)
        assertThat(settle(0.4f, -0.2f, start = 1)).isEqualTo(0)
    }

    @Test
    fun flingTurnsOnePageInItsDirection() {
        assertThat(settle(1.1f, 3f, start = 1)).isEqualTo(2)
        assertThat(settle(0.9f, -3f, start = 1)).isEqualTo(0)
        // A fling back toward the start page returns to it.
        assertThat(settle(1.3f, -3f, start = 1)).isEqualTo(1)
    }

    @Test
    fun settleNeverMovesMoreThanOnePageOrPastTheEnds() {
        assertThat(settle(3.9f, 0f, start = 1)).isEqualTo(2)
        assertThat(settle(-0.2f, -5f, start = 0)).isEqualTo(0)
        assertThat(settle(4.2f, 5f, start = 4)).isEqualTo(4)
        assertThat(settle(0f, 5f, start = 0, count = 0)).isEqualTo(0)
    }

    @Test
    fun rubberBandIsIdentityInsideTheRange() {
        assertThat(WidgetStackPhysics.rubberBand(0f, 3)).isEqualTo(0f)
        assertThat(WidgetStackPhysics.rubberBand(1.3f, 3)).isEqualTo(1.3f)
        assertThat(WidgetStackPhysics.rubberBand(2f, 3)).isEqualTo(2f)
    }

    @Test
    fun rubberBandResistsAndIsBoundedAtBothEnds() {
        val small = WidgetStackPhysics.rubberBand(-0.1f, 3)
        val large = WidgetStackPhysics.rubberBand(-10f, 3)
        assertThat(small).isLessThan(0f)
        assertThat(small).isGreaterThan(-0.1f)
        assertThat(large).isLessThan(small)
        assertThat(large).isGreaterThan(-WidgetStackPhysics.MAX_OVERSCROLL)

        val pastEnd = WidgetStackPhysics.rubberBand(2.5f, 3)
        assertThat(pastEnd).isGreaterThan(2f)
        assertThat(pastEnd).isLessThan(2f + WidgetStackPhysics.MAX_OVERSCROLL)
    }

    @Test
    fun childEffectsAreSubtleAndCapped() {
        assertThat(WidgetStackPhysics.childScale(0f)).isEqualTo(1f)
        assertThat(WidgetStackPhysics.childScale(3f)).isEqualTo(WidgetStackPhysics.childScale(1f))
        assertThat(WidgetStackPhysics.childScale(1f)).isAtLeast(0.9f)
        assertThat(WidgetStackPhysics.childAlpha(-1f)).isEqualTo(0.5f)
    }
}
