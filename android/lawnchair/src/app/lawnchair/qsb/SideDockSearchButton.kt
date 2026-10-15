/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.qsb

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.Insettable
import com.android.launcher3.R
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The dock's search bar while the dock is on a side (iPhone Duo-style): a round button at the
 * bottom edge of the dock column, so the home grid keeps all its space.
 *
 * It shows the icon the dock search bar shows on its left (the search provider's icon, themed the
 * same way), and a tap or long-press does what it does on the search bar (see
 * [LawnQsbLayout.launchHotseatSearch] and [LawnQsbLayout.openHotseatSearchOptions]). It is hidden
 * when the dock is at the bottom or the dock search bar is turned off, and its alpha and
 * translation follow the dock between launcher states (WorkspaceStateTransitionAnimation).
 */
class SideDockSearchButton(private val launcher: LawnchairLauncher) :
    FrameLayout(launcher),
    Insettable {

    private val prefs2 = PreferenceManager2.getInstance(launcher)
    private val icon = ImageView(launcher)

    init {
        id = R.id.side_dock_search_button
        layoutParams = LayoutParams(0, 0, Gravity.TOP or Gravity.LEFT)
        visibility = GONE
        contentDescription = launcher.getString(R.string.label_search)
        isClickable = true
        isLongClickable = true
        addView(icon, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        setOnClickListener {
            LawnQsbLayout.launchHotseatSearch(launcher, currentProvider())
        }
        setOnLongClickListener {
            LawnQsbLayout.openHotseatSearchOptions(launcher, this)
            true
        }

        // Follow the dock search bar's provider and theming, like LawnQsbLayout does.
        combine(prefs2.hotseatQsbProvider.get(), prefs2.themedHotseatQsb.get()) { _, themed -> themed }
            .onEach(::updateLook)
            .launchIn(launcher.lifecycleScope)
    }

    private fun currentProvider() = LawnQsbLayout.getSearchProvider(launcher, prefs2)

    private fun updateLook(themed: Boolean) {
        val provider = currentProvider()
        // Same icon as the search bar's start icon (rememberHotseatQsbState).
        val iconRes = if (themed) provider.themedIcon else provider.icon
        icon.setImageDrawable(
            setThemedIconResource(
                launcher,
                iconRes,
                themed = themed || iconRes == R.drawable.ic_qsb_search,
                method = provider.themingMethod,
            ),
        )

        // Same fill as the search bar.
        val fill = getHotseatBackgroundColor(launcher, themed)
        val alpha = (PreferenceManager.getInstance(launcher).hotseatQsbAlpha.get() * 255 / 100)
            .coerceIn(0, 255)
        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(alpha, Color.red(fill), Color.green(fill), Color.blue(fill)))
        }
        val rippleColor = ColorStateList.valueOf(Color.argb(0x33, 0xFF, 0xFF, 0xFF))
        background = RippleDrawable(rippleColor, circle, null)
    }

    override fun setInsets(insets: Rect) {
        val dp = launcher.deviceProfile
        val bounds = dp.getSideDockSearchButtonBounds(launcher)
        if (bounds.isEmpty) {
            visibility = GONE
            return
        }
        visibility = VISIBLE

        val lp = layoutParams as LayoutParams
        lp.gravity = Gravity.TOP or Gravity.LEFT
        lp.width = bounds.width()
        lp.height = bounds.height()
        lp.leftMargin = bounds.left
        lp.topMargin = bounds.top
        layoutParams = lp

        val iconPadding = (bounds.width() * ICON_PADDING).roundToInt()
        icon.setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
    }

    private companion object {
        // Space around the provider icon inside the round button, as a share of its size.
        const val ICON_PADDING = 0.22f
    }
}
