/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.qsb

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import app.lawnchair.hotseat.LawnchairHotseat
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.BaseActivity
import com.android.launcher3.Flags
import com.android.launcher3.Insettable
import com.android.launcher3.R
import com.android.launcher3.views.ActivityContext
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The dock's search bar while the dock is on a side (iPhone Duo-style): a pill centered under the
 * home grid, beside the dock.
 *
 * It inflates the same layout as the dock's own search bar (see Hotseat), so it shows the same
 * search provider icon, mic and lens buttons, and opens the same search on tap. It is hidden
 * when the dock is at the bottom, and its alpha and translation follow the dock between launcher
 * states (WorkspaceStateTransitionAnimation).
 */
class SideDockSearchPill(context: Context) :
    FrameLayout(context),
    Insettable {

    private val activity: ActivityContext = ActivityContext.lookupContext<BaseActivity>(context)

    init {
        id = R.id.side_dock_search_pill
        clipChildren = false
        clipToPadding = false
        layoutParams = LayoutParams(0, 0, Gravity.BOTTOM or Gravity.START)
        visibility = GONE
        val layoutId = searchLayoutId(context)
        if (layoutId != R.layout.empty_view) {
            val qsb = LayoutInflater.from(context).inflate(layoutId, this, false)
            addView(qsb, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }

    override fun setInsets(insets: Rect) {
        val dp = activity.deviceProfile
        val lp = layoutParams as LayoutParams
        if (!dp.isSideDock || dp.sideDockSearchPillHeightPx <= 0 || childCount == 0) {
            visibility = GONE
            return
        }
        visibility = VISIBLE

        // Center the pill under the grid: between the workspace paddings, so beside the dock.
        val padding = dp.workspacePadding
        val contentLeft = insets.left + padding.left
        val contentWidth = dp.deviceProperties.availableWidthPx - padding.left - padding.right
        val width = min(contentWidth, (dp.cellLayoutWidth * WIDTH_OF_PANE).roundToInt())

        lp.gravity = Gravity.BOTTOM or Gravity.LEFT
        lp.width = width
        lp.height = dp.sideDockSearchPillHeightPx
        lp.leftMargin = contentLeft + (contentWidth - width) / 2
        lp.rightMargin = 0
        lp.bottomMargin = insets.bottom + dp.edgeMarginPx
        layoutParams = lp
    }

    companion object {
        // Share of one workspace pane the pill spans.
        private const val WIDTH_OF_PANE = 0.75f

        /** The dock search bar's layout, chosen the same way Hotseat chooses it. */
        private fun searchLayoutId(context: Context): Int {
            if (Flags.enableQsbOnHotseat()) return R.layout.qsb_container_hotseat
            val prefs2 = PreferenceManager2.getInstance(context)
            var mode = prefs2.hotseatMode.firstCached()
            if (!mode.isAvailable(context)) mode = LawnchairHotseat
            return mode.layoutResourceId
        }
    }
}
