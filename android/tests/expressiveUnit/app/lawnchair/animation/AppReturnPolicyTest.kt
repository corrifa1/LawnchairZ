package app.lawnchair.animation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppReturnPolicyTest {

    private val allowed = AppReturnConditions(
        featureEnabled = true,
        isRecentsProvider = false,
        gestureNavContractHandled = false,
        iconSurfaceOpen = false,
        isInNormalState = true,
        isOverlayShown = false,
        blockingFloatingViewOpen = false,
        configurationChanged = false,
        animatorsEnabled = true,
    )

    @Test
    fun animatesWhenNothingBlocks() {
        assertThat(shouldAnimateReturn(allowed)).isTrue()
    }

    @Test
    fun eachSkipConditionBlocksTheAnimation() {
        val blocked = listOf(
            allowed.copy(featureEnabled = false),
            allowed.copy(isRecentsProvider = true),
            allowed.copy(gestureNavContractHandled = true),
            allowed.copy(iconSurfaceOpen = true),
            allowed.copy(isInNormalState = false),
            allowed.copy(isOverlayShown = true),
            allowed.copy(blockingFloatingViewOpen = true),
            allowed.copy(configurationChanged = true),
            allowed.copy(animatorsEnabled = false),
        )

        blocked.forEach { assertThat(shouldAnimateReturn(it)).isFalse() }
    }
}
