/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for Expressive Launcher.
 * https://github.com/denson9874/ExpressiveLauncher
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.fold

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * Keeps One UI from locking the phone when it's folded from the home screen.
 *
 * One UI decides what happens on fold from the kind of task in front: for a home (or recents)
 * task it puts the device to sleep, whatever the "continue apps on cover screen" settings say,
 * while a normal app task gets app continuity. (Seen in system logs as
 * `buildFoldingPolicyInfo: targetRootTask=HomeOrRecents` vs `appContinuityMode=APPLIED`.)
 *
 * While the launcher is resumed, this watches the public hinge-angle sensor and, as soon as a
 * fold starts, starts [FoldBridgeActivity]: an invisible activity in its own normal task. When
 * the fold completes that task is in front, so One UI applies continuity instead of sleeping,
 * and the bridge then closes itself, leaving the launcher on the cover screen. It doesn't touch
 * the keyguard: the phone simply stays awake, as it does when folding from any other app.
 *
 * This works around a One UI policy rather than using a supported API, so it's opt-in.
 */
class FoldBridgeController(private val activity: Activity) : SensorEventListener {

    private val sensorManager = activity.getSystemService(SensorManager::class.java)
    private val hingeSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
    private val detector = HingeFoldDetector()
    private var listening = false

    /** Starts or stops watching the hinge. Call with false whenever the launcher pauses. */
    fun setActive(active: Boolean) {
        val sensor = hingeSensor
        if (active && !listening && sensor != null) {
            detector.reset()
            listening = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            if (!listening) Log.w(TAG, "Couldn't listen to the hinge angle sensor")
        } else if (!active && listening) {
            sensorManager.unregisterListener(this)
            listening = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (detector.onAngle(event.values[0])) {
            FoldBridgeActivity.start(activity)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val TAG = "FoldBridge"

        /** True if this device reports a hinge angle, i.e. the feature can work at all. */
        fun isSupported(context: Context): Boolean = context.getSystemService(SensorManager::class.java)
            ?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null
    }
}
