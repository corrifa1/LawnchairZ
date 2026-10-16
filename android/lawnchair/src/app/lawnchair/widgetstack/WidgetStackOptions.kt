/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

/**
 * Layout of a widget stack's `options` column.
 *
 * - bit 0: Smart Rotate enabled
 * - bits 8-15: rank of the visible child
 *
 * The downgrade script in res/raw/downgrade_schema.json reads bits 8-15 to decide which child to
 * keep, so keep the two in sync.
 */
object WidgetStackOptions {
    const val FLAG_SMART_ROTATE = 1
    const val ACTIVE_RANK_SHIFT = 8
    const val ACTIVE_RANK_MASK = 0xFF

    fun isSmartRotate(options: Int) = options and FLAG_SMART_ROTATE != 0

    fun withSmartRotate(options: Int, enabled: Boolean) =
        if (enabled) options or FLAG_SMART_ROTATE else options and FLAG_SMART_ROTATE.inv()

    fun activeRank(options: Int) = (options ushr ACTIVE_RANK_SHIFT) and ACTIVE_RANK_MASK

    fun withActiveRank(options: Int, rank: Int) =
        (options and (ACTIVE_RANK_MASK shl ACTIVE_RANK_SHIFT).inv()) or
            ((rank and ACTIVE_RANK_MASK) shl ACTIVE_RANK_SHIFT)
}
