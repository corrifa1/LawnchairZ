/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for Expressive Launcher.
 * https://github.com/denson9874/ExpressiveLauncher
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.fold

/**
 * Detects that the device is being folded, from hinge-angle readings in degrees.
 *
 * It fires once the hinge has closed by [minDrop] from the most open angle seen since the last
 * [reset] (or the last firing), as long as the angle is at most [maxTriggerAngle]. Measuring the
 * drop from wherever the hinge was means a fold is caught both from flat and after the device was
 * held half-folded, while small wobbles in Flex mode don't count. Readings at or below
 * [closedAngle] never fire: the fold is already done.
 */
class HingeFoldDetector(
    private val minDrop: Float = DEFAULT_MIN_DROP,
    private val maxTriggerAngle: Float = DEFAULT_MAX_TRIGGER_ANGLE,
    private val closedAngle: Float = DEFAULT_CLOSED_ANGLE,
) {
    private var mostOpen = Float.NaN

    /** Returns true when a fold has just been detected. */
    fun onAngle(angle: Float): Boolean {
        if (mostOpen.isNaN() || angle > mostOpen) {
            mostOpen = angle
            return false
        }
        if (angle <= maxTriggerAngle && angle > closedAngle && mostOpen - angle >= minDrop) {
            mostOpen = angle
            return true
        }
        return false
    }

    /** Forgets the readings so far; the next one becomes the starting point. */
    fun reset() {
        mostOpen = Float.NaN
    }

    companion object {
        const val DEFAULT_MIN_DROP = 20f
        const val DEFAULT_MAX_TRIGGER_ANGLE = 140f
        const val DEFAULT_CLOSED_ANGLE = 5f
    }
}
