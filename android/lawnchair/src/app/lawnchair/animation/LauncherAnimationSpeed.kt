/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.animation

import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.annotation.MainThread
import java.lang.reflect.Method

/**
 * Keeps the launcher's own animations at normal speed when the system animator duration scale
 * is turned down.
 *
 * On One UI the system holds input for the whole of its home transition, and that transition
 * follows `animator_duration_scale`. Lowering the global scale shortens the hold, but would also
 * speed up every launcher animation. Each process has its own copy of the scale, so we set ours
 * back to 1x without affecting other apps.
 *
 * Android has no public setter: this uses `ValueAnimator.setDurationScale` through reflection,
 * which is marked as tolerated for apps. If it's blocked we log once and keep the system scale.
 * The system resets the process scale whenever the global setting changes, so we re-apply from
 * the public duration-scale listener and on every launcher resume.
 *
 * We only override a scale between 0 and 1: 0 means "Remove animations" or animations off, and a
 * scale above 1 is usually an accessibility choice; both are left alone.
 */
object LauncherAnimationSpeed {
    private const val TAG = "LauncherAnimationSpeed"
    private const val NORMAL_SCALE = 1f

    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var enabled = false
    private var listenerRegistered = false
    private var setterUnavailable = false

    private val setDurationScale: Method? by lazy {
        try {
            ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType)
        } catch (e: ReflectiveOperationException) {
            Log.w(TAG, "Can't override the animator duration scale on this device", e)
            setterUnavailable = true
            null
        }
    }

    // Held strongly: ValueAnimator only keeps weak references to listeners.
    private val scaleListener = ValueAnimator.DurationScaleChangeListener { scale ->
        // Called on whichever thread changed the scale (a binder thread for system changes).
        if (enabled && scale != NORMAL_SCALE) mainHandler.post { reapply() }
    }

    /** Turns the override on or off and applies it immediately. */
    @MainThread
    fun setEnabled(context: Context, enabled: Boolean) {
        appContext = context.applicationContext
        this.enabled = enabled
        if (enabled && !listenerRegistered) {
            listenerRegistered = ValueAnimator.registerDurationScaleChangeListener(scaleListener)
        }
        reapply()
    }

    /** Re-applies the right scale for the current system setting. Cheap; safe to call often. */
    @MainThread
    fun reapply() {
        val context = appContext ?: return
        val target = targetScale(systemScale(context), enabled)
        if (ValueAnimator.getDurationScale() != target) {
            setScale(target)
        }
    }

    /** The process animator scale to use for a given system scale. */
    internal fun targetScale(systemScale: Float, enabled: Boolean): Float = if (enabled && systemScale > 0f && systemScale < NORMAL_SCALE) {
        NORMAL_SCALE
    } else {
        systemScale
    }

    private fun systemScale(context: Context): Float = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        NORMAL_SCALE,
    )

    private fun setScale(scale: Float) {
        if (setterUnavailable) return
        val setter = setDurationScale ?: return
        try {
            setter.invoke(null, scale)
        } catch (e: ReflectiveOperationException) {
            Log.w(TAG, "Couldn't set the animator duration scale", e)
            setterUnavailable = true
        } catch (e: SecurityException) {
            Log.w(TAG, "Couldn't set the animator duration scale", e)
            setterUnavailable = true
        }
    }
}
