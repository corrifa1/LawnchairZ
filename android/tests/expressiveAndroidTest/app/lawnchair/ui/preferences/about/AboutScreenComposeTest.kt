package app.lawnchair.ui.preferences.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.About
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutScreenComposeTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun aboutRoute_rendersOwnedProfileAndUpstreamCredits() {
        assumeTrue("LawnchairZ-only ownership test", BuildConfig.IS_EXPRESSIVE_PRODUCT)

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = PreferenceActivity.createIntent(context, About)

        ActivityScenario.launch<PreferenceActivity>(intent).use {
            assertTrue(composeRule.onAllNodesWithText("Amogh Lele").fetchSemanticsNodes().isEmpty())
            assertTrue(
                composeRule.onAllNodesWithText(context.getString(R.string.support_and_pr))
                    .fetchSemanticsNodes().isEmpty(),
            )
            assertTrue(
                composeRule.onAllNodesWithText(context.getString(R.string.community))
                    .fetchSemanticsNodes().isEmpty(),
            )
            assertTrue(
                composeRule.onAllNodesWithText(context.getString(R.string.donate))
                    .fetchSemanticsNodes().isEmpty(),
            )
            composeRule.onNodeWithText(context.getString(R.string.news)).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.support)).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.github)).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Corey Flynn").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.design_and_development))
                .assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.about_based_on))
                .performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.about_credit_expressive))
                .performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.about_credit_lawnchair))
                .performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.about_credit_launcher3))
                .performScrollTo().assertIsDisplayed()
        }
    }
}
