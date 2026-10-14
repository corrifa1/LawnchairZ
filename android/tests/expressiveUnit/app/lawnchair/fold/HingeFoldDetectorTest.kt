package app.lawnchair.fold

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HingeFoldDetectorTest {

    private val detector = HingeFoldDetector(minDrop = 20f, maxTriggerAngle = 140f, closedAngle = 5f)

    @Test
    fun foldFromFlat_firesOnceBelowTheMaxTriggerAngle() {
        assertThat(detector.onAngle(180f)).isFalse()
        assertThat(detector.onAngle(150f)).isFalse()
        assertThat(detector.onAngle(138f)).isTrue()
        assertThat(detector.onAngle(125f)).isFalse()
    }

    @Test
    fun foldAfterHoldingHalfFolded_fires() {
        // The launcher comes back (reset) while the device is held at ~100 degrees.
        detector.reset()
        assertThat(detector.onAngle(100f)).isFalse()
        assertThat(detector.onAngle(95f)).isFalse()
        assertThat(detector.onAngle(78f)).isTrue()
    }

    @Test
    fun smallWobbles_doNotFire() {
        detector.onAngle(110f)
        assertThat(detector.onAngle(100f)).isFalse()
        assertThat(detector.onAngle(108f)).isFalse()
        assertThat(detector.onAngle(95f)).isFalse()
    }

    @Test
    fun keepsClosingAfterFiring_needsAnotherFullDropToFireAgain() {
        detector.onAngle(180f)
        assertThat(detector.onAngle(130f)).isTrue()
        assertThat(detector.onAngle(120f)).isFalse()
        assertThat(detector.onAngle(105f)).isTrue()
    }

    @Test
    fun unfolding_neverFires() {
        detector.onAngle(0f)
        assertThat(detector.onAngle(40f)).isFalse()
        assertThat(detector.onAngle(120f)).isFalse()
        assertThat(detector.onAngle(180f)).isFalse()
    }

    @Test
    fun alreadyClosed_doesNotFire() {
        detector.onAngle(30f)
        assertThat(detector.onAngle(4f)).isFalse()
    }
}
