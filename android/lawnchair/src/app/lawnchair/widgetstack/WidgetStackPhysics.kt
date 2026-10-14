/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for Expressive Launcher.
 * https://github.com/denson9874/ExpressiveLauncher
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Pure paging math for [WidgetStackView]. Positions are in pages: 0 shows the first child, 1.5 is
 * halfway between the second and third.
 */
object WidgetStackPhysics {

    /** Furthest the stack can be pulled past its first or last child, in pages. */
    const val MAX_OVERSCROLL = 0.18f

    /** How quickly the rubber band stiffens. */
    private const val RUBBER_BAND_COEFFICIENT = 0.55f

    /**
     * Maps a raw position to what is shown, resisting increasingly past either end. Never wraps.
     */
    fun rubberBand(raw: Float, count: Int): Float {
        val last = (count - 1).coerceAtLeast(0).toFloat()
        val over = when {
            raw < 0f -> raw
            raw > last -> raw - last
            else -> return raw
        }
        val damped = (1f - 1f / (abs(over) * RUBBER_BAND_COEFFICIENT / MAX_OVERSCROLL + 1f)) *
            MAX_OVERSCROLL
        return if (over < 0f) -damped else last + damped
    }

    /**
     * The page to settle on when the finger lifts at [position] with [velocity] pages per second.
     * A fling moves one page in its direction; otherwise the nearest page wins, so a drag past the
     * halfway point turns the page. The result is at most one page from [startPage].
     */
    fun settleTarget(
        position: Float,
        velocity: Float,
        startPage: Int,
        count: Int,
        minFlingVelocity: Float,
    ): Int {
        if (count <= 0) return 0
        val target = when {
            abs(velocity) >= minFlingVelocity && velocity.sign > 0 -> floor(position).toInt() + 1
            abs(velocity) >= minFlingVelocity -> ceil(position).toInt() - 1
            else -> position.roundToInt()
        }
        return target.coerceIn(startPage - 1, startPage + 1).coerceIn(0, count - 1)
    }

    /** Scale for a child [distance] pages away from the one shown. */
    fun childScale(distance: Float) = 1f - 0.06f * abs(distance).coerceAtMost(1f)

    /** Alpha for a child [distance] pages away from the one shown. */
    fun childAlpha(distance: Float) = 1f - 0.5f * abs(distance).coerceAtMost(1f)
}
