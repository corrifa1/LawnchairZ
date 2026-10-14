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
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager

/**
 * Invisible, untouchable activity in its own task that sits in front of the home screen for the
 * moment of a fold, so One UI treats the fold like one from a normal app (see
 * [FoldBridgeController]). It closes itself as soon as it has moved to the cover screen, when the
 * hinge is opened again, or after [TIMEOUT_MS] at the latest.
 */
class FoldBridgeActivity :
    Activity(),
    SensorEventListener {

    private val handler = Handler(Looper.getMainLooper())
    private val finishRunnable = Runnable { finishBridge() }
    private var sensorManager: SensorManager? = null
    private var startSmallestWidthDp = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        // Touches go straight through to whatever is behind (the launcher).
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        startSmallestWidthDp = resources.configuration.smallestScreenWidthDp

        sensorManager = getSystemService(SensorManager::class.java)
        sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        handler.postDelayed(finishRunnable, TIMEOUT_MS)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.smallestScreenWidthDp < startSmallestWidthDp) {
            // Now on the cover screen, past One UI's fold decision: hand over to the launcher.
            handler.removeCallbacks(finishRunnable)
            handler.postDelayed(finishRunnable, MOVED_FINISH_DELAY_MS)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.values[0] >= REOPENED_ANGLE) {
            // The fold was abandoned.
            finishBridge()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        handler.removeCallbacks(finishRunnable)
        sensorManager?.unregisterListener(this)
        super.onDestroy()
    }

    private fun finishBridge() {
        if (!isFinishing) finish()
    }

    companion object {
        private const val TAG = "FoldBridge"
        private const val TIMEOUT_MS = 2500L
        private const val MOVED_FINISH_DELAY_MS = 150L
        private const val REOPENED_ANGLE = 160f

        /** Starts the bridge in front of [context]'s task, without any transition. */
        fun start(context: Context) {
            val intent = Intent(context, FoldBridgeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            val options = ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
            try {
                context.startActivity(intent, options)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Couldn't start the fold bridge", e)
            }
        }
    }
}
