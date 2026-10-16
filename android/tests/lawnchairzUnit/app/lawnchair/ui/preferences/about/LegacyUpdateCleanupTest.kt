/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.ui.preferences.about

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class LegacyUpdateCleanupTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val scheduler = application.getSystemService(JobScheduler::class.java)
    private val notifications = application.getSystemService(NotificationManager::class.java)

    @Test
    fun removesTheLegacyJobsChannelAndPreferences() {
        val legacy = ComponentName(application, "app.lawnchair.ui.preferences.about.ExpressiveUpdateJobService")
        listOf(0x455851, 0x455852, 0x455853).forEach { id ->
            scheduler.schedule(JobInfo.Builder(id, legacy).setMinimumLatency(60_000L).build())
        }
        notifications.createNotificationChannel(
            NotificationChannel("expressive_launcher_updates", "Updates", NotificationManager.IMPORTANCE_DEFAULT),
        )
        application.getSharedPreferences("expressive_update_notifications", Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", true).commit()
        assertThat(LegacyUpdateCleanup.legacyPreferencesFile(application).exists()).isTrue()

        LegacyUpdateCleanup.run(application)

        assertThat(scheduler.allPendingJobs).isEmpty()
        assertThat(notifications.getNotificationChannel("expressive_launcher_updates")).isNull()
        assertThat(LegacyUpdateCleanup.legacyPreferencesFile(application).exists()).isFalse()
    }

    @Test
    fun leavesOtherJobsChannelsAndPreferencesAlone() {
        val other = ComponentName(application, "app.lawnchair.SomeOtherJobService")
        scheduler.schedule(JobInfo.Builder(0x455851, other).setMinimumLatency(60_000L).build())
        notifications.createNotificationChannel(
            NotificationChannel("bug_reports", "Reports", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val launcherPrefs = application.getSharedPreferences("com.android.launcher3.prefs", Context.MODE_PRIVATE)
        launcherPrefs.edit().putString("pref_workspaceColumns", "4").commit()

        LegacyUpdateCleanup.run(application)

        assertThat(scheduler.allPendingJobs.map { it.id }).containsExactly(0x455851)
        assertThat(notifications.getNotificationChannel("bug_reports")).isNotNull()
        assertThat(launcherPrefs.getString("pref_workspaceColumns", null)).isEqualTo("4")
    }

    @Test
    fun runsCleanlyWhenNothingIsLeft() {
        LegacyUpdateCleanup.run(application)

        assertThat(scheduler.allPendingJobs).isEmpty()
        assertThat(LegacyUpdateCleanup.legacyPreferencesFile(application).exists()).isFalse()
    }
}
