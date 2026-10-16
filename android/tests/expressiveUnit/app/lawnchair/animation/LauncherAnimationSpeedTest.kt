/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.animation

import android.app.Application
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class LauncherAnimationSpeedTest {

    @Test
    fun enabled_restoresNormalSpeedWhenSystemSpedUp() {
        assertThat(LauncherAnimationSpeed.targetScale(0.25f, enabled = true)).isEqualTo(1f)
        assertThat(LauncherAnimationSpeed.targetScale(0.5f, enabled = true)).isEqualTo(1f)
    }

    @Test
    fun enabled_leavesAnimationsOffAndSlowerScalesAlone() {
        assertThat(LauncherAnimationSpeed.targetScale(0f, enabled = true)).isEqualTo(0f)
        assertThat(LauncherAnimationSpeed.targetScale(1f, enabled = true)).isEqualTo(1f)
        assertThat(LauncherAnimationSpeed.targetScale(2f, enabled = true)).isEqualTo(2f)
    }

    @Test
    fun disabled_followsSystem() {
        assertThat(LauncherAnimationSpeed.targetScale(0.25f, enabled = false)).isEqualTo(0.25f)
        assertThat(LauncherAnimationSpeed.targetScale(0f, enabled = false)).isEqualTo(0f)
    }
}
