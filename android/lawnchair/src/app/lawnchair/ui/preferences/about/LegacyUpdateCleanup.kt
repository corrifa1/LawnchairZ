/*
 * Copyright 2026 Corey Flynn and LawnchairZ contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for LawnchairZ.
 * https://github.com/corrifa1/LawnchairZ
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.ui.preferences.about

import android.app.NotificationManager
import android.app.job.JobScheduler
import android.content.Context
import java.io.File

/**
 * Removes what the inherited Expressive update checker left on the device: its scheduled jobs,
 * its notification channel (and any update notification in it) and its preferences file.
 * Launcher layout and settings are stored elsewhere and are never touched.
 */
internal object LegacyUpdateCleanup {
    private const val LEGACY_JOB_SERVICE = "app.lawnchair.ui.preferences.about.ExpressiveUpdateJobService"
    private const val LEGACY_CHANNEL_ID = "expressive_launcher_updates"
    private const val LEGACY_PREFERENCES_NAME = "expressive_update_notifications"

    fun run(context: Context) {
        context.getSystemService(JobScheduler::class.java)?.let { scheduler ->
            scheduler.allPendingJobs
                .filter { it.service.className == LEGACY_JOB_SERVICE }
                .forEach { scheduler.cancel(it.id) }
        }
        context.getSystemService(NotificationManager::class.java)?.let { notifications ->
            if (notifications.getNotificationChannel(LEGACY_CHANNEL_ID) != null) {
                notifications.deleteNotificationChannel(LEGACY_CHANNEL_ID)
            }
        }
        if (legacyPreferencesFile(context).exists()) {
            context.deleteSharedPreferences(LEGACY_PREFERENCES_NAME)
        }
    }

    internal fun legacyPreferencesFile(context: Context) =
        File(context.applicationInfo.dataDir, "shared_prefs/$LEGACY_PREFERENCES_NAME.xml")
}
