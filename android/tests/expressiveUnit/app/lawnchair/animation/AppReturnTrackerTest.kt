package app.lawnchair.animation

import android.app.Application
import android.os.Process
import android.os.UserHandle
import app.lawnchair.animation.AppReturnTracker.Source
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.util.StableViewInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class AppReturnTrackerTest {

    private var now = 1_000L
    private val tracker = AppReturnTracker(
        expiryMs = EXPIRY_MS,
        launchToStopWindowMs = STOP_WINDOW_MS,
        clock = { now },
    )
    private val user: UserHandle = Process.myUserHandle()

    @Test
    fun launchThenStopThenConsume_returnsLaunchOnce() {
        val info = StableViewInfo(7, Favorites.CONTAINER_DESKTOP, "stable")
        tracker.recordLaunch("com.example.a", user, info, Source.WORKSPACE)
        now += 300
        tracker.onLauncherStopped()
        now += 5_000

        val pending = tracker.consumeReturn()

        assertThat(pending).isNotNull()
        assertThat(pending!!.packageName).isEqualTo("com.example.a")
        assertThat(pending.user).isEqualTo(user)
        assertThat(pending.stableViewInfo).isEqualTo(info)
        assertThat(pending.source).isEqualTo(Source.WORKSPACE)
        assertThat(tracker.consumeReturn()).isNull()
    }

    @Test
    fun withoutStop_returnsNull() {
        tracker.recordLaunch("com.example.a", user, null, Source.HOTSEAT)
        now += 1_000

        assertThat(tracker.consumeReturn()).isNull()
        // Consuming clears the record, so a later stop can't revive it.
        tracker.onLauncherStopped()
        assertThat(tracker.consumeReturn()).isNull()
    }

    @Test
    fun expiredLaunch_returnsNull() {
        tracker.recordLaunch("com.example.a", user, null, Source.WORKSPACE)
        now += 100
        tracker.onLauncherStopped()
        now += EXPIRY_MS

        assertThat(tracker.consumeReturn()).isNull()
    }

    @Test
    fun launchAtExpiryBoundary_isStillReturned() {
        tracker.recordLaunch("com.example.a", user, null, Source.WORKSPACE)
        tracker.onLauncherStopped()
        now += EXPIRY_MS

        assertThat(tracker.consumeReturn()).isNotNull()
    }

    @Test
    fun newerLaunch_replacesOlder() {
        tracker.recordLaunch("com.example.a", user, null, Source.WORKSPACE)
        tracker.onLauncherStopped()
        now += 2_000
        tracker.recordLaunch("com.example.b", user, null, Source.FOLDER)
        tracker.onLauncherStopped()

        val pending = tracker.consumeReturn()

        assertThat(pending!!.packageName).isEqualTo("com.example.b")
        assertThat(pending.source).isEqualTo(Source.FOLDER)
    }

    @Test
    fun newerLaunchWithoutStop_isNotReturned() {
        tracker.recordLaunch("com.example.a", user, null, Source.WORKSPACE)
        tracker.onLauncherStopped()
        tracker.recordLaunch("com.example.b", user, null, Source.WORKSPACE)

        assertThat(tracker.consumeReturn()).isNull()
    }

    @Test
    fun stopLongAfterLaunch_dropsRecord() {
        tracker.recordLaunch("com.example.a", user, null, Source.WORKSPACE)
        now += STOP_WINDOW_MS + 1
        tracker.onLauncherStopped()

        assertThat(tracker.hasRecordedLaunch).isFalse()
        assertThat(tracker.consumeReturn()).isNull()
    }

    @Test
    fun clear_forgetsLaunch() {
        tracker.recordLaunch("com.example.a", user, null, Source.WORKSPACE)
        tracker.onLauncherStopped()
        tracker.clear()

        assertThat(tracker.hasRecordedLaunch).isFalse()
        assertThat(tracker.consumeReturn()).isNull()
    }

    @Test
    fun sourceForContainer_mapsLauncherContainers() {
        assertThat(AppReturnTracker.sourceForContainer(Favorites.CONTAINER_DESKTOP)).isEqualTo(Source.WORKSPACE)
        assertThat(AppReturnTracker.sourceForContainer(Favorites.CONTAINER_HOTSEAT)).isEqualTo(Source.HOTSEAT)
        assertThat(AppReturnTracker.sourceForContainer(Favorites.CONTAINER_HOTSEAT_PREDICTION)).isEqualTo(Source.HOTSEAT)
        assertThat(AppReturnTracker.sourceForContainer(42)).isEqualTo(Source.FOLDER)
        assertThat(AppReturnTracker.sourceForContainer(Favorites.CONTAINER_ALL_APPS)).isEqualTo(Source.ALL_APPS)
        assertThat(AppReturnTracker.sourceForContainer(Favorites.CONTAINER_ALL_APPS_PREDICTION)).isEqualTo(Source.ALL_APPS)
        assertThat(AppReturnTracker.sourceForContainer(Favorites.CONTAINER_SHORTCUTS)).isEqualTo(Source.OTHER)
        assertThat(AppReturnTracker.sourceForContainer(Favorites.CONTAINER_UNKNOWN)).isEqualTo(Source.OTHER)
    }

    private companion object {
        const val EXPIRY_MS = 600_000L
        const val STOP_WINDOW_MS = 10_000L
    }
}
