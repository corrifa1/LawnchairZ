/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for Expressive Launcher.
 * https://github.com/denson9874/ExpressiveLauncher
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import app.lawnchair.animation.AppReturnTracker.PendingReturn
import app.lawnchair.animation.AppReturnTracker.Source
import com.android.app.animation.Interpolators
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Hotseat
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAnimUtils.HOTSEAT_SCALE_PROPERTY_FACTORY
import com.android.launcher3.LauncherAnimUtils.SCALE_INDEX_REVEAL_ANIM
import com.android.launcher3.LauncherAnimUtils.WORKSPACE_SCALE_PROPERTY_FACTORY
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherState
import com.android.launcher3.Utilities
import com.android.launcher3.Workspace
import com.android.launcher3.folder.Folder
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.statemanager.StateManager
import java.util.function.Predicate

/**
 * Launcher-side "return to home" animation for devices whose recents provider doesn't send a
 * GestureNavContract (Samsung One UI and others). We never get the closing app's window, so
 * instead of morphing it we make the landing intentional:
 *
 * 1. The home content (workspace + hotseat, not the wallpaper) scales and fades in.
 * 2. Shortly after, the launched app's icon drawable (not its label) settles into its slot with
 *    a spring, so it reads as "the app landing back in its icon".
 *
 * Robustness: every exit path (end, touch, state change, pause/stop, new intent, configuration
 * change, watchdog) goes through [finish], which snaps every view we touched back to scale 1,
 * its original alpha and visible. A stuck invisible or scaled icon is the worst outcome, so
 * [finish] is idempotent and safe to call at any time.
 */
class AppReturnAnimator(private val launcher: Launcher) {

    private var session: Session? = null

    /**
     * Applies the initial (pre-reveal) state synchronously, so the very next frame is already
     * part of the animation, and starts animating on the next pre-draw.
     *
     * Must be called before the launcher draws its first frame after returning (we call it from
     * onStart, before the first traversal after restart).
     */
    fun prepareAndStart(pending: PendingReturn) {
        finish()
        try {
            prepare(pending)
        } catch (e: RuntimeException) {
            // This is purely cosmetic: never let it crash or leave the home screen half-hidden.
            Log.e(TAG, "Couldn't start the return animation", e)
            finish()
        }
    }

    private fun prepare(pending: PendingReturn) {
        val workspace = launcher.workspace ?: return
        val hotseat = launcher.hotseat ?: return
        val dragLayer = launcher.dragLayer ?: return

        val icon = if (pending.source in ICON_SOURCES) findIconTarget(pending) else null
        val session = Session(workspace, hotseat, icon)
        this.session = session

        // Widget updates are deliberately not paused (unlike the GNC animation): widgets tend to
        // refresh right as we return home, and resuming deferred updates applies them all
        // synchronously, which made the first tap after landing (it ends the animation) stall.

        workspace.setPivotToScaleWithSelf(hotseat)
        applyReveal(session, 0f)
        icon?.apply(0f)

        session.stateListener = object : StateManager.StateListener<LauncherState> {
            override fun onStateTransitionStart(toState: LauncherState) {
                finish()
            }
        }.also { launcher.stateManager.addStateListener(it) }

        session.preDrawListener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                dragLayer.viewTreeObserver.removeOnPreDrawListener(this)
                if (this@AppReturnAnimator.session === session) {
                    session.preDrawListener = null
                    startAnimations(session)
                }
                return true
            }
        }.also { dragLayer.viewTreeObserver.addOnPreDrawListener(it) }

        // Last line of defence: whatever happens, never leave the home screen scaled or hidden.
        session.watchdog = Runnable { finish() }.also {
            dragLayer.postDelayed(it, (WATCHDOG_MS * durationScale()).toLong())
        }
    }

    /** Ends the animation immediately and restores every view it touched. Idempotent. */
    fun finish() {
        val session = session ?: return
        this.session = null
        val dragLayer = launcher.dragLayer

        session.preDrawListener?.let { dragLayer?.viewTreeObserver?.removeOnPreDrawListener(it) }
        session.preDrawListener = null
        session.watchdog?.let { dragLayer?.removeCallbacks(it) }
        session.watchdog = null
        session.stateListener?.let { launcher.stateManager.removeStateListener(it) }
        session.stateListener = null

        session.animators.forEach {
            it.removeAllListeners()
            it.cancel()
        }
        session.animators.clear()
        session.spring?.let {
            if (it.isRunning) it.cancel()
        }
        session.spring = null

        WORKSPACE_SCALE.set(session.workspace, 1f)
        HOTSEAT_SCALE.set(session.hotseat, 1f)
        session.workspace.alpha = session.workspaceAlpha
        session.hotseat.alpha = session.hotseatAlpha
        session.icon?.restore()
    }

    private fun startAnimations(session: Session) {
        val reveal = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = REVEAL_DURATION_MS
            interpolator = Interpolators.EMPHASIZED_DECELERATE
            addUpdateListener { applyReveal(session, it.animatedValue as Float) }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        session.revealDone = true
                        maybeFinish(session)
                    }
                },
            )
        }
        session.animators.add(reveal)

        val icon = session.icon
        if (icon == null) {
            session.iconDone = true
        } else {
            // A zero-length animator gives us a start delay that follows the system animator
            // duration scale, like the reveal itself.
            val iconDelay = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 0
                startDelay = ICON_START_DELAY_MS
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            startIconSpring(session, icon)
                        }
                    },
                )
            }
            session.animators.add(iconDelay)
            iconDelay.start()
        }
        reveal.start()
    }

    private fun startIconSpring(session: Session, icon: IconTarget) {
        if (this.session !== session) return
        val spring = SpringAnimation(FloatValueHolder(0f)).apply {
            spring = SpringForce(1f)
                .setDampingRatio(ICON_SPRING_DAMPING)
                .setStiffness(ICON_SPRING_STIFFNESS)
            setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE)
            addUpdateListener { _, value, _ -> icon.apply(value) }
            addEndListener { _, canceled, _, _ ->
                if (!canceled) {
                    session.iconDone = true
                    maybeFinish(session)
                }
            }
        }
        session.spring = spring
        spring.start()
    }

    private fun maybeFinish(session: Session) {
        if (this.session === session && session.revealDone && session.iconDone) {
            finish()
        }
    }

    private fun applyReveal(session: Session, progress: Float) {
        val scale = Utilities.mapRange(progress, REVEAL_START_SCALE, 1f)
        val alphaFactor = Utilities.mapRange(progress, REVEAL_START_ALPHA, 1f)
        WORKSPACE_SCALE.set(session.workspace, scale)
        HOTSEAT_SCALE.set(session.hotseat, scale)
        session.workspace.alpha = session.workspaceAlpha * alphaFactor
        session.hotseat.alpha = session.hotseatAlpha * alphaFactor
    }

    private fun findIconTarget(pending: PendingReturn): IconTarget? {
        // getFirstHomeElementForAppClose closes an open folder that doesn't contain the app; the
        // caller only gets here when no folder is open or the open folder contains it.
        val view = launcher.getFirstHomeElementForAppClose(
            pending.stableViewInfo,
            pending.packageName,
            pending.user,
        ) ?: return null
        if (!view.isAttachedToWindow || !view.isShown) return null
        return when (view) {
            is BubbleTextView -> {
                val drawable: FastBitmapDrawable = view.icon ?: return null
                BubbleIconTarget(view, drawable)
            }

            // Folder previews are drawn by the FolderIcon itself, so the whole icon (including
            // its label) settles, pivoting around the preview.
            is FolderIcon -> ViewIconTarget(view)

            else -> null
        }
    }

    private class Session(
        val workspace: Workspace<*>,
        val hotseat: Hotseat,
        val icon: IconTarget?,
    ) {
        val workspaceAlpha = workspace.alpha
        val hotseatAlpha = hotseat.alpha
        val animators = mutableListOf<ValueAnimator>()
        var spring: SpringAnimation? = null
        var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null
        var stateListener: StateManager.StateListener<LauncherState>? = null
        var watchdog: Runnable? = null
        var revealDone = false
        var iconDone = false
    }

    /** The view that "receives" the app. `progress` goes 0 → 1 and may overshoot. */
    internal interface IconTarget {
        fun apply(progress: Float)
        fun restore()
    }

    /** Animates only the icon drawable of a [BubbleTextView], leaving the label alone. */
    internal class BubbleIconTarget(
        private val view: BubbleTextView,
        private val drawable: FastBitmapDrawable,
    ) : IconTarget {
        private val originalAlpha = drawable.alpha

        init {
            view.setForceHideDot(true)
        }

        override fun apply(progress: Float) {
            FastBitmapDrawable.SCALE.set(drawable, iconScale(progress))
            drawable.alpha = (originalAlpha * iconAlpha(progress)).toInt()
        }

        override fun restore() {
            drawable.resetScale()
            drawable.alpha = originalAlpha
            view.setForceHideDot(false)
            view.invalidate()
        }
    }

    /** Animates a whole view (folder icons) around its icon area. */
    internal class ViewIconTarget(private val view: View) : IconTarget {
        private val originalAlpha = view.alpha
        private val originalScaleX = view.scaleX
        private val originalScaleY = view.scaleY
        private val originalPivotX = view.pivotX
        private val originalPivotY = view.pivotY

        init {
            if (view is FolderIcon) {
                val bounds = Rect()
                view.getPreviewBounds(bounds)
                if (!bounds.isEmpty) {
                    view.pivotX = bounds.exactCenterX()
                    view.pivotY = bounds.exactCenterY()
                }
            }
        }

        override fun apply(progress: Float) {
            val scale = iconScale(progress)
            view.scaleX = originalScaleX * scale
            view.scaleY = originalScaleY * scale
            view.alpha = originalAlpha * iconAlpha(progress)
        }

        override fun restore() {
            view.scaleX = originalScaleX
            view.scaleY = originalScaleY
            view.alpha = originalAlpha
            view.pivotX = originalPivotX
            view.pivotY = originalPivotY
        }
    }

    companion object {
        private const val TAG = "AppReturnAnimator"

        const val REVEAL_DURATION_MS = 300L
        const val REVEAL_START_SCALE = 0.94f
        const val REVEAL_START_ALPHA = 0.6f
        const val ICON_START_DELAY_MS = 70L
        const val ICON_START_SCALE = 1.25f
        const val ICON_SPRING_DAMPING = 0.75f
        const val ICON_SPRING_STIFFNESS = 500f

        /** Fraction of the spring progress over which the icon fades fully in. */
        private const val ICON_FADE_FRACTION = 0.5f
        private const val WATCHDOG_MS = 2000L

        private val ICON_SOURCES = setOf(Source.WORKSPACE, Source.HOTSEAT, Source.FOLDER)

        private val WORKSPACE_SCALE = WORKSPACE_SCALE_PROPERTY_FACTORY.get(SCALE_INDEX_REVEAL_ANIM)
        private val HOTSEAT_SCALE = HOTSEAT_SCALE_PROPERTY_FACTORY.get(SCALE_INDEX_REVEAL_ANIM)

        internal fun iconScale(progress: Float): Float = Utilities.mapRange(progress, ICON_START_SCALE, 1f)

        internal fun iconAlpha(progress: Float): Float = (progress / ICON_FADE_FRACTION).coerceIn(0f, 1f)

        private fun durationScale(): Float = if (Utilities.ATLEAST_T) {
            ValueAnimator.getDurationScale().coerceAtLeast(1f)
        } else {
            1f
        }

        /**
         * Returns true if [folder] holds the item that [pending] returns to, using the same
         * matching order as [Launcher.getFirstHomeElementForAppClose].
         */
        fun folderContains(folder: Folder, pending: PendingReturn): Boolean {
            val preferred = Predicate<ItemInfo> { pending.stableViewInfo?.matches(it) == true }
            val sameApp = Predicate<ItemInfo> { info ->
                info != null &&
                    info.itemType == ITEM_TYPE_APPLICATION &&
                    info.user == pending.user &&
                    info.targetPackage == pending.packageName
            }
            return folder.getFirstMatch(preferred, sameApp) != null
        }
    }
}
