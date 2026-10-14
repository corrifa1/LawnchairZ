/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for Expressive Launcher.
 * https://github.com/denson9874/ExpressiveLauncher
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import android.graphics.Point

/** Pure sizing rules for widget stacks. */
object WidgetStackSizing {

    /**
     * The smallest span every child supports: the largest of the children's minimum spans, per
     * axis. Non-positive spans count as 1.
     */
    fun intersectMinSpans(minSpans: Collection<Point>): Point = Point(
        minSpans.maxOfOrNull { it.x.coerceAtLeast(1) } ?: 1,
        minSpans.maxOfOrNull { it.y.coerceAtLeast(1) } ?: 1,
    )
}
