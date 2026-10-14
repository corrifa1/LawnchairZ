/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.animation

import android.os.UserHandle
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.util.StableViewInfo
import java.util.concurrent.TimeUnit

/**
 * Remembers the last app the launcher started from one of its own icons so that, when the user
 * comes back home, [AppReturnAnimator] can make that icon "receive" the app.
 *
 * This replaces the information a GestureNavContract would normally carry on devices whose
 * recents provider never sends one to third-party launchers (Samsung One UI and other OEM skins).
 *
 * Known limitation: the launcher can't see which app is actually in front. If the user launches
 * app A, switches to app B through recents and then goes home, the "last launched" app is still
 * A, so A's icon is the one that lands. We deliberately don't use PACKAGE_USAGE_STATS or any other
 * permission to find out. The mitigations are the [expiryMs] limit and the subtlety of the effect:
 * the workspace reveal is right for any return, and a wrong icon only gets a small settle.
 *
 * Not thread safe; use from the main thread only.
 */
class AppReturnTracker(
    private val expiryMs: Long = DEFAULT_EXPIRY_MS,
    private val launchToStopWindowMs: Long = DEFAULT_LAUNCH_TO_STOP_WINDOW_MS,
    private val clock: () -> Long,
) {

    /** Where the tracked app was launched from. */
    enum class Source {
        WORKSPACE,
        HOTSEAT,
        FOLDER,
        ALL_APPS,
        OTHER,
    }

    /** A recorded launch that is ready to be animated back into its icon. */
    data class PendingReturn(
        val packageName: String,
        val user: UserHandle,
        val stableViewInfo: StableViewInfo?,
        val source: Source,
        val launchTimeMs: Long,
    )

    private var launch: PendingReturn? = null
    private var stoppedAfterLaunch = false

    /** Records a successful app launch, replacing any older one. */
    fun recordLaunch(
        packageName: String,
        user: UserHandle,
        stableViewInfo: StableViewInfo?,
        source: Source,
        timestampMs: Long = clock(),
    ) {
        launch = PendingReturn(packageName, user, stableViewInfo, source, timestampMs)
        stoppedAfterLaunch = false
    }

    /**
     * Marks that the launcher went to the background, i.e. we really left home.
     *
     * A stop long after the launch wasn't caused by it (the launch failed silently and the user
     * later left home some other way, e.g. a widget or notification), so the record is dropped.
     */
    fun onLauncherStopped(timestampMs: Long = clock()) {
        val current = launch ?: return
        if (stoppedAfterLaunch) return
        val sinceLaunch = timestampMs - current.launchTimeMs
        if (sinceLaunch in 0..launchToStopWindowMs) {
            stoppedAfterLaunch = true
        } else {
            clear()
        }
    }

    /**
     * Returns the recorded launch exactly once, if the launcher was stopped after it and it has
     * not expired. Any call clears the record, whether or not a launch is returned.
     */
    fun consumeReturn(timestampMs: Long = clock()): PendingReturn? {
        val current = launch
        val wasStopped = stoppedAfterLaunch
        clear()
        if (current == null || !wasStopped) return null
        val age = timestampMs - current.launchTimeMs
        if (age < 0 || age > expiryMs) return null
        return current
    }

    /** Forgets the recorded launch, e.g. when something makes it unreliable. */
    fun clear() {
        launch = null
        stoppedAfterLaunch = false
    }

    /** True if a launch is recorded, regardless of whether it can be consumed yet. */
    val hasRecordedLaunch: Boolean get() = launch != null

    companion object {
        val DEFAULT_EXPIRY_MS: Long = TimeUnit.MINUTES.toMillis(10)

        /** Generous enough for a slow cold start; the launcher normally stops within a second. */
        val DEFAULT_LAUNCH_TO_STOP_WINDOW_MS: Long = TimeUnit.SECONDS.toMillis(10)

        /** Maps an [com.android.launcher3.model.data.ItemInfo.container] to a [Source]. */
        @JvmStatic
        fun sourceForContainer(container: Int): Source = when {
            container > 0 -> Source.FOLDER

            container == Favorites.CONTAINER_DESKTOP -> Source.WORKSPACE

            container == Favorites.CONTAINER_HOTSEAT ||
                container == Favorites.CONTAINER_HOTSEAT_PREDICTION -> Source.HOTSEAT

            container == Favorites.CONTAINER_ALL_APPS ||
                container == Favorites.CONTAINER_ALL_APPS_PREDICTION ||
                container == Favorites.CONTAINER_PRIVATESPACE -> Source.ALL_APPS

            else -> Source.OTHER
        }
    }
}
