package app.lawnchair.ui.preferences.about

import com.android.launcher3.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AboutOwnershipTest {

    @Test
    fun productOwner_isCoreyWithGitHubProfile() {
        val owners = productOwners()

        assertThat(owners).hasSize(1)
        assertThat(owners.single()).isEqualTo(
            TeamMember(
                name = "Corey Flynn",
                role = Role.DesignAndDevelopment,
                photoUrl = "https://github.com/corrifa1.png",
                socialUrl = "https://github.com/corrifa1",
            ),
        )
    }

    @Test
    fun productLinks_useOwnedDestinations() {
        val links = productLinks()

        assertThat(links.map { it.labelResId }).containsExactly(
            R.string.news,
            R.string.support,
            R.string.github,
        ).inOrder()
        assertThat(links.map { it.url }).containsExactly(
            "https://github.com/corrifa1/LawnchairZ/releases",
            "https://github.com/corrifa1/LawnchairZ/issues",
            "https://github.com/corrifa1/LawnchairZ",
        ).inOrder()
    }

    @Test
    fun productCredits_nameEveryUpstreamProject() {
        val credits = productCredits()

        assertThat(credits.map { it.labelResId }).containsExactly(
            R.string.about_credit_expressive,
            R.string.about_credit_lawnchair,
            R.string.about_credit_launcher3,
        ).inOrder()
        assertThat(credits.map { it.url }).containsExactly(
            "https://github.com/denson9874/ExpressiveLauncher",
            "https://github.com/LawnchairLauncher/lawnchair",
            "https://android.googlesource.com/platform/packages/apps/Launcher3",
        ).inOrder()
    }

    @Test
    fun brandingSelection_scopesLawnchairZOwnershipToTheProductBuild() {
        val product = aboutBranding(
            isExpressiveProduct = true,
        )
        val lawnchair = aboutBranding(
            isExpressiveProduct = false,
        )

        assertThat(product.coreTeam.map { it.name }).containsExactly("Corey Flynn")
        assertThat(product.supportAndPr).isEmpty()
        assertThat(product.bottomLinks).isEmpty()
        assertThat(product.credits).isEqualTo(productCredits())
        assertThat(product.topLinks.map { it.url }).doesNotContain(
            "https://www.paypal.com/ncp/payment/9RB3TYYQ6FWE2",
        )
        assertThat(lawnchair.coreTeam.map { it.name }).contains("Amogh Lele")
        assertThat(lawnchair.coreTeam.map { it.name }).doesNotContain("Corey Flynn")
        assertThat(lawnchair.credits).isEmpty()
        assertThat(lawnchair.topLinks.map { it.url })
            .contains("https://github.com/LawnchairLauncher/lawnchair")
    }
}
