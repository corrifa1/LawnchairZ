package app.lawnchair.animation

import android.app.Application
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class AppReturnIconTargetTest {

    @Test
    fun iconCurve_startsLargeAndInvisible_andSettlesAtRest() {
        assertThat(AppReturnAnimator.iconScale(0f)).isEqualTo(AppReturnAnimator.ICON_START_SCALE)
        assertThat(AppReturnAnimator.iconAlpha(0f)).isEqualTo(0f)
        assertThat(AppReturnAnimator.iconScale(1f)).isEqualTo(1f)
        assertThat(AppReturnAnimator.iconAlpha(1f)).isEqualTo(1f)
        // Spring overshoot never pushes alpha past opaque.
        assertThat(AppReturnAnimator.iconAlpha(1.1f)).isEqualTo(1f)
    }

    @Test
    fun viewTarget_restoreAfterCancelMidAnimation_leavesViewAtRest() {
        val view = View(ApplicationProvider.getApplicationContext())
        view.pivotX = 12f
        view.pivotY = 34f
        val target = AppReturnAnimator.ViewIconTarget(view)

        target.apply(0f)
        assertThat(view.alpha).isEqualTo(0f)
        target.apply(0.3f)
        assertThat(view.scaleX).isGreaterThan(1f)

        target.restore()

        assertThat(view.scaleX).isEqualTo(1f)
        assertThat(view.scaleY).isEqualTo(1f)
        assertThat(view.alpha).isEqualTo(1f)
        assertThat(view.visibility).isEqualTo(View.VISIBLE)
        assertThat(view.pivotX).isEqualTo(12f)
        assertThat(view.pivotY).isEqualTo(34f)
    }
}
