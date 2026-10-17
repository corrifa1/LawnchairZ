/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 */

package app.lawnchair.fold.duo

import android.content.Context
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.util.IntArray as LauncherIntArray

/**
 * What the workspace asks about Duo continuity. Reads the "Unfolded layout" preference and turns
 * [DuoPairing] answers into the types the workspace uses. Everything here is a no-op while the
 * preference is on "Page pairs", which is the default.
 */
object DuoContinuity {

    /** Whether the mode is on. It only exists on a foldable. */
    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY &&
            PreferenceManager.getInstance(context).unfoldedLayout.get() ==
            PreferenceManager.UNFOLDED_LAYOUT_CONTINUITY

    @JvmStatic
    fun dockSide(context: Context): DockSide =
        when (PreferenceManager.getInstance(context).dockPosition.get()) {
            PreferenceManager.DOCK_POSITION_LEFT -> DockSide.LEFT
            PreferenceManager.DOCK_POSITION_RIGHT -> DockSide.RIGHT
            else -> DockSide.BOTTOM
        }

    /**
     * Whether the two screens of each pair swap places in the workspace's view order. Only when
     * unfolded: the cover screen shows one page at a time.
     */
    @JvmStatic
    fun swapsViewOrder(context: Context, isTwoPanels: Boolean, isRtl: Boolean): Boolean =
        isTwoPanels && isEnabled(context) &&
            DuoPairing.swapsViewOrder(DuoPairing.coverPane(dockSide(context)), isRtl)

    /** Whether the cover screen is showing, with the mode on: companions are not bound. */
    @JvmStatic
    fun hidesCompanions(context: Context, isTwoPanels: Boolean): Boolean =
        !isTwoPanels && isEnabled(context)

    /** [order] arranged for the view order, or unchanged when the mode is off. */
    @JvmStatic
    fun arrangeForViews(
        context: Context,
        isTwoPanels: Boolean,
        isRtl: Boolean,
        order: LauncherIntArray,
    ): LauncherIntArray {
        if (!isTwoPanels || !isEnabled(context)) return order
        return fromList(DuoPairing.arrangePairs(toList(order), swapsViewOrder(context, isTwoPanels, isRtl)))
    }

    /** The screens to add to the cover screen: only dock-side screens. */
    @JvmStatic
    fun coverScreens(order: LauncherIntArray): LauncherIntArray =
        fromList(DuoPairing.coverScreens(toList(order)))

    /** The companions in [order]. */
    @JvmStatic
    fun hiddenCompanions(order: LauncherIntArray): LauncherIntArray =
        fromList(DuoPairing.hiddenCompanions(toList(order)))

    /**
     * The order to save: pairs un-swapped and unbound companions kept in place. Unchanged when the
     * mode is off and nothing is hidden.
     */
    @JvmStatic
    fun persistableOrder(
        context: Context,
        shown: LauncherIntArray,
        hidden: LauncherIntArray,
    ): LauncherIntArray {
        if (hidden.isEmpty && !isEnabled(context)) return shown
        return fromList(DuoPairing.persistableOrder(toList(shown), toList(hidden)))
    }

    /**
     * The screen that stands for the page being shown: the dock-side screen of its pair, or
     * [screenId] itself when the mode is off.
     */
    @JvmStatic
    fun currentScreenFor(context: Context, screenId: Int): Int =
        if (isEnabled(context)) DuoPairing.dockSideScreenOf(screenId) else screenId

    private fun toList(array: LauncherIntArray): List<Int> =
        List(array.size()) { array.get(it) }

    private fun fromList(list: List<Int>): LauncherIntArray {
        val result = LauncherIntArray(list.size)
        list.forEach { result.add(it) }
        return result
    }
}
