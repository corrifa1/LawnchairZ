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
 * Detects the start of a fold from (nearly) flat, from hinge-angle readings in degrees.
 *
 * It fires once when the angle drops from [readyAngle] or more to [triggerAngle] or less, and
 * only re-arms after the hinge is opened back to [readyAngle]. Holding the device half-folded
 * (e.g. in Flex mode) therefore can't fire it repeatedly.
 */
class HingeFoldDetector(
    private val readyAngle: Float = DEFAULT_READY_ANGLE,
    private val triggerAngle: Float = DEFAULT_TRIGGER_ANGLE,
) {
    private var armed = false

    /** Returns true when a fold has just started. */
    fun onAngle(angle: Float): Boolean {
        if (angle >= readyAngle) {
            armed = true
            return false
        }
        if (armed && angle <= triggerAngle) {
            armed = false
            return true
        }
        return false
    }

    /** Forgets the last reading; the next fold only counts after the hinge is seen open. */
    fun reset() {
        armed = false
    }

    companion object {
        const val DEFAULT_READY_ANGLE = 150f
        const val DEFAULT_TRIGGER_ANGLE = 130f
    }
}
