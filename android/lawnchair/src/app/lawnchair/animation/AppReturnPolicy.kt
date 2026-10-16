/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.animation

/** Everything [shouldAnimateReturn] needs to decide whether to play the return-home animation. */
data class AppReturnConditions(
    /** `pref_appReturnAnimation` is on. */
    val featureEnabled: Boolean,
    /** LawnchairZ is the system recents provider, so the real recents animation handles it. */
    val isRecentsProvider: Boolean,
    /** The intent that brought us home carried a GestureNavContract and GNC is enabled. */
    val gestureNavContractHandled: Boolean,
    /** A GNC icon surface ([com.android.launcher3.AbstractFloatingView.TYPE_ICON_SURFACE]) is open. */
    val iconSurfaceOpen: Boolean,
    /** The launcher is in the NORMAL state after intent handling. */
    val isInNormalState: Boolean,
    /** The -1 feed/overlay is visible. */
    val isOverlayShown: Boolean,
    /** A floating view is open that isn't a folder containing the returning app. */
    val blockingFloatingViewOpen: Boolean,
    /** The configuration changed since the launcher was stopped, or the activity is being recreated. */
    val configurationChanged: Boolean,
    /** Animators are enabled (system animations on, "remove animations" off). */
    val animatorsEnabled: Boolean,
)

/** Returns true when the launcher-side return-home animation should play. */
fun shouldAnimateReturn(conditions: AppReturnConditions): Boolean = with(conditions) {
    featureEnabled &&
        !isRecentsProvider &&
        !gestureNavContractHandled &&
        !iconSurfaceOpen &&
        isInNormalState &&
        !isOverlayShown &&
        !blockingFloatingViewOpen &&
        !configurationChanged &&
        animatorsEnabled
}
