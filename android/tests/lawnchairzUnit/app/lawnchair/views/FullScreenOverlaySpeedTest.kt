/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.views

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FullScreenOverlaySpeedTest {

    @Test
    fun defaultSpeed_keepsDuration() {
        assertThat(scaledOverlayDuration(500, 1f)).isEqualTo(500)
    }

    @Test
    fun fasterSpeed_shortensAndSlowerSpeed_lengthens() {
        assertThat(scaledOverlayDuration(500, 2f)).isEqualTo(250)
        assertThat(scaledOverlayDuration(500, 0.5f)).isEqualTo(1000)
        assertThat(scaledOverlayDuration(200, 1.25f)).isEqualTo(160)
    }

    @Test
    fun outOfRangeSpeed_isClamped() {
        assertThat(scaledOverlayDuration(500, 10f)).isEqualTo(250)
        assertThat(scaledOverlayDuration(500, 0f)).isEqualTo(1000)
    }
}
