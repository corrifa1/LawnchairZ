package app.lawnchair.ui.preferences.about

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.create

/**
 * Product-owned destinations shown by the About screen.
 * LawnchairZ modification: LawnchairZ's own maintainer and repository replace Expressive's.
 */
internal object AboutDestinations {
    const val GITHUB_PROFILE_URL = "https://github.com/corrifa1"
    const val GITHUB_REPOSITORY_URL = "https://github.com/corrifa1/LawnchairZ"
    const val RELEASE_BUILDS_URL = "$GITHUB_REPOSITORY_URL/releases"
    const val EXPRESSIVE_REPOSITORY_URL = "https://github.com/denson9874/ExpressiveLauncher"
    const val LAWNCHAIR_REPOSITORY_URL = "https://github.com/LawnchairLauncher/lawnchair"
    const val LAUNCHER3_REPOSITORY_URL = "https://android.googlesource.com/platform/packages/apps/Launcher3"
}

internal fun productOwners(): List<TeamMember> = listOf(
    TeamMember(
        name = "Corey Flynn",
        role = Role.DesignAndDevelopment,
        photoUrl = "${AboutDestinations.GITHUB_PROFILE_URL}.png",
        socialUrl = AboutDestinations.GITHUB_PROFILE_URL,
    ),
)

internal fun productLinks(): List<Link> = listOf(
    Link(
        iconResId = R.drawable.ic_new_releases,
        labelResId = R.string.news,
        url = AboutDestinations.RELEASE_BUILDS_URL,
    ),
    Link(
        iconResId = R.drawable.ic_help,
        labelResId = R.string.support,
        url = "${AboutDestinations.GITHUB_REPOSITORY_URL}/issues",
    ),
    Link(
        iconResId = R.drawable.ic_github,
        labelResId = R.string.github,
        url = AboutDestinations.GITHUB_REPOSITORY_URL,
    ),
)

/** The projects LawnchairZ is based on, credited on its About screen. */
internal fun productCredits(): List<Link> = listOf(
    Link(
        iconResId = R.drawable.ic_github,
        labelResId = R.string.about_credit_expressive,
        url = AboutDestinations.EXPRESSIVE_REPOSITORY_URL,
    ),
    Link(
        iconResId = R.drawable.ic_github,
        labelResId = R.string.about_credit_lawnchair,
        url = AboutDestinations.LAWNCHAIR_REPOSITORY_URL,
    ),
    Link(
        iconResId = R.drawable.ic_website,
        labelResId = R.string.about_credit_launcher3,
        url = AboutDestinations.LAUNCHER3_REPOSITORY_URL,
    ),
)

internal data class AboutBranding(
    val coreTeam: List<TeamMember>,
    val supportAndPr: List<TeamMember>,
    val topLinks: List<Link>,
    val bottomLinks: List<Link>,
    val credits: List<Link>,
)

/** Keeps LawnchairZ ownership isolated without changing attribution in sibling Lawnchair builds. */
internal fun aboutBranding(
    isLawnchairzProduct: Boolean,
): AboutBranding = if (isLawnchairzProduct) {
    AboutBranding(
        coreTeam = productOwners(),
        supportAndPr = emptyList(),
        topLinks = productLinks(),
        bottomLinks = emptyList(),
        credits = productCredits(),
    )
} else {
    AboutBranding(
        coreTeam = lawnchairTeam,
        supportAndPr = lawnchairSupportAndPr,
        topLinks = lawnchairTopLinks,
        bottomLinks = lawnchairBottomLinks,
        credits = emptyList(),
    )
}

class AboutViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val api: GitHubService = gitHubApiRetrofit.create()
    private val prefs: PreferenceManager = PreferenceManager.getInstance(application)
    private val prefs2: PreferenceManager2 = PreferenceManager2.getInstance(application)
    private val nightlyBuildsRepository = NightlyBuildsRepository(
        applicationContext = application,
        api = api,
    )

    val uiState: StateFlow<AboutUiState>
        field = MutableStateFlow(AboutUiState())

    val updateState = nightlyBuildsRepository.updateState

    init {
        val branding = aboutBranding(
            isLawnchairzProduct = BuildConfig.IS_LAWNCHAIRZ_PRODUCT,
        )
        uiState.update {
            it.copy(
                versionName = if (prefs.hideVersionInfo.get()) {
                    prefs.pseudonymVersion.get() + " (pseudonym)"
                } else {
                    BuildConfig.VERSION_NAME
                },
                commitHash = BuildConfig.COMMIT_HASH,
                coreTeam = branding.coreTeam,
                supportAndPr = branding.supportAndPr,
                topLinks = branding.topLinks,
                bottomLinks = branding.bottomLinks,
                credits = branding.credits,
            )
        }

        // LawnchairZ has its own owner card and must never query Lawnchair's activity feed.
        // Other flavors retain their existing attribution and active-contributor behavior.
        if (!BuildConfig.IS_LAWNCHAIRZ_PRODUCT) {
            viewModelScope.launch(Dispatchers.Default) {
                val activeContributors = fetchActiveContributors()
                val updatedCoreTeam = uiState.value.coreTeam.map { member ->
                    val status = if (
                        member.githubUsername != null &&
                        activeContributors.contains(member.githubUsername.lowercase())
                    ) {
                        ContributorStatus.Active
                    } else {
                        ContributorStatus.Idle
                    }
                    member.copy(status = status)
                }
                uiState.update { it.copy(coreTeam = updatedCoreTeam) }
            }
        }

        // LawnchairZ modification: LawnchairZ has no update channel. Only Lawnchair nightly
        // builds check Lawnchair's own nightly releases.
        val shouldCheckForUpdates = !BuildConfig.IS_LAWNCHAIRZ_PRODUCT &&
            BuildConfig.APPLICATION_ID.contains("nightly") && prefs2.autoUpdaterNightly.firstCached()
        if (shouldCheckForUpdates) {
            nightlyBuildsRepository.checkForUpdate()
            viewModelScope.launch {
                nightlyBuildsRepository.updateState.collect { state ->
                    uiState.update { it.copy(updateState = state) }
                }
            }
        }
    }

    fun downloadUpdate() {
        nightlyBuildsRepository.downloadUpdate()
    }

    fun installUpdate(file: File, forceInstall: Boolean = false) {
        nightlyBuildsRepository.installUpdate(file, forceInstall)
    }

    fun resetToDownloaded(file: File) {
        nightlyBuildsRepository.resetToDownloaded(file)
    }

    private suspend fun fetchActiveContributors(): Set<String> {
        return runCatching {
            nightlyBuildsRepository.api.getRepositoryEvents("LawnchairLauncher", "lawnchair")
                .map { it.actor.login.lowercase() }
                .toSet()
        }.getOrDefault(emptySet())
    }
}
