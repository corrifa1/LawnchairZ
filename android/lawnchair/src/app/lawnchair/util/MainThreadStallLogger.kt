/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for Expressive Launcher.
 * https://github.com/denson9874/ExpressiveLauncher
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.util

import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Debug-only diagnostics for input lag: logs every main-thread message that takes longer than
 * [THRESHOLD_MS], and how late touch-downs reach the launcher. Filter logcat with
 * `MainThreadStall`.
 */
object MainThreadStallLogger {
    private const val TAG = "MainThreadStall"
    private const val THRESHOLD_MS = 100L
    private const val TOUCH_DELAY_THRESHOLD_MS = 50L

    private var installed = false
    private var dispatchStart = 0L
    private var dispatchLine: String? = null

    /** Time of the last launcher onStart, to relate stalls to returning home. */
    @Volatile
    var lastStartUptime = 0L

    fun install() {
        if (installed) return
        installed = true
        Looper.getMainLooper().setMessageLogging { line ->
            if (line.startsWith(">>>>>")) {
                dispatchStart = SystemClock.uptimeMillis()
                dispatchLine = line
            } else if (line.startsWith("<<<<<")) {
                val duration = SystemClock.uptimeMillis() - dispatchStart
                if (duration >= THRESHOLD_MS) {
                    Log.w(TAG, "Main thread busy ${duration}ms (${sinceStart()}): $dispatchLine")
                }
                dispatchLine = null
            }
        }
    }

    /** Logs how long the system kept the launcher started but not resumed. */
    fun onResumed() {
        Log.i(TAG, "Launcher resumed (${sinceStart()})")
    }

    /** Logs when the system hands window focus back to (or takes it from) the launcher. */
    fun onWindowFocusChanged(hasFocus: Boolean) {
        Log.i(TAG, "Launcher window focus=$hasFocus (${sinceStart()})")
    }

    /** Logs how long a touch-down waited before the launcher could handle it. */
    fun onTouchDown(eventTime: Long) {
        val delay = SystemClock.uptimeMillis() - eventTime
        if (delay >= TOUCH_DELAY_THRESHOLD_MS) {
            Log.w(TAG, "Touch-down delivered ${delay}ms late (${sinceStart()})")
        }
    }

    private fun sinceStart(): String = if (lastStartUptime == 0L) {
        "no start yet"
    } else {
        "${SystemClock.uptimeMillis() - lastStartUptime}ms after launcher start"
    }
}
