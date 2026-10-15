/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.fold

/**
 * Detects that the device is being folded, from hinge-angle readings in degrees.
 *
 * It fires once the hinge has closed by [minDrop] from the most open angle seen since the last
 * [reset] (or the last firing), as long as the angle is at most [maxTriggerAngle]. Measuring the
 * drop from wherever the hinge was means a fold is caught both from flat and after the device was
 * held half-folded, while small wobbles in Flex mode don't count.
 *
 * A drop all the way to closed still fires. Some hinge sensors only report a few coarse positions
 * (the Galaxy Z Fold reports 0, 90 or 180 degrees, about every 200 ms), so a normal fold can go
 * from fully open straight to 0 with nothing in between, and that reading can still arrive before
 * the system decides what the fold does.
 */
class HingeFoldDetector(
    private val minDrop: Float = DEFAULT_MIN_DROP,
    private val maxTriggerAngle: Float = DEFAULT_MAX_TRIGGER_ANGLE,
) {
    private var mostOpen = Float.NaN

    /** Returns true when a fold has just been detected. */
    fun onAngle(angle: Float): Boolean {
        if (mostOpen.isNaN() || angle > mostOpen) {
            mostOpen = angle
            return false
        }
        if (angle <= maxTriggerAngle && mostOpen - angle >= minDrop) {
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
    }
}
