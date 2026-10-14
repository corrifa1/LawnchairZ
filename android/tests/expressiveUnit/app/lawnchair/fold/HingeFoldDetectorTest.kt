package app.lawnchair.fold

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HingeFoldDetectorTest {

    private val detector = HingeFoldDetector(readyAngle = 150f, triggerAngle = 130f)

    @Test
    fun foldFromFlat_firesOnce() {
        assertThat(detector.onAngle(180f)).isFalse()
        assertThat(detector.onAngle(140f)).isFalse()
        assertThat(detector.onAngle(125f)).isTrue()
        assertThat(detector.onAngle(60f)).isFalse()
        assertThat(detector.onAngle(0f)).isFalse()
    }

    @Test
    fun withoutSeeingTheHingeOpen_neverFires() {
        assertThat(detector.onAngle(100f)).isFalse()
        assertThat(detector.onAngle(0f)).isFalse()
    }

    @Test
    fun halfFoldedWobble_doesNotRefire() {
        detector.onAngle(180f)
        assertThat(detector.onAngle(120f)).isTrue()
        // Hovering around the trigger angle, e.g. in Flex mode.
        assertThat(detector.onAngle(135f)).isFalse()
        assertThat(detector.onAngle(110f)).isFalse()
    }

    @Test
    fun rearmsAfterOpeningAgain() {
        detector.onAngle(180f)
        assertThat(detector.onAngle(120f)).isTrue()
        detector.onAngle(170f)
        assertThat(detector.onAngle(120f)).isTrue()
    }

    @Test
    fun reset_requiresSeeingTheHingeOpenAgain() {
        detector.onAngle(180f)
        detector.reset()
        assertThat(detector.onAngle(120f)).isFalse()
    }
}
