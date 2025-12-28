package zed.rainxch.githubstore.feature.details.data.repository

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import zed.rainxch.githubstore.app.app_state.AppStateManager
import zed.rainxch.githubstore.core.data.services.LocalizationManager
import zed.rainxch.githubstore.core.domain.model.GithubRelease
import zed.rainxch.githubstore.core.domain.model.GithubRepoSummary
import zed.rainxch.githubstore.core.domain.model.GithubUser
import zed.rainxch.githubstore.core.domain.model.GithubUserProfile
import zed.rainxch.githubstore.feature.details.data.dto.ReleaseNetwork
import zed.rainxch.githubstore.feature.details.data.dto.RepoByIdNetwork
import zed.rainxch.githubstore.feature.details.data.dto.RepoInfoNetwork
import zed.rainxch.githubstore.feature.details.data.dto.UserProfileNetwork
import zed.rainxch.githubstore.feature.details.data.mappers.toDomain
import zed.rainxch.githubstore.feature.details.data.utils.ReadmeLocalizationHelper
import zed.rainxch.githubstore.feature.details.data.utils.preprocessMarkdown
import zed.rainxch.githubstore.feature.details.domain.model.RepoStats
import zed.rainxch.githubstore.feature.details.domain.repository.DetailsRepository
import zed.rainxch.githubstore.network.RateLimitException
import zed.rainxch.githubstore.network.safeApiCall

class DetailsRepositoryImpl(
    private val github: HttpClient,
    private val appStateManager: AppStateManager,
    private val localizationManager: LocalizationManager
) : DetailsRepository {

    private val readmeHelper = ReadmeLocalizationHelper(localizationManager)

    override suspend fun getRepositoryById(id: Long): GithubRepoSummary {
        val repoResult = github.safeApiCall<RepoByIdNetwork>(
            rateLimitHandler = appStateManager.rateLimitHandler,
            autoRetryOnRateLimit = false
        ) {
            get("/repositories/$id") {
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }
        }

        val repo = repoResult.getOrElse { error ->
            if (error is RateLimitException) {
                appStateManager.updateRateLimit(error.rateLimitInfo)
            }
            throw error
        }

        return GithubRepoSummary(
            id = repo.id,
            name = repo.name,
            fullName = repo.fullName,
            owner = GithubUser(
                id = repo.owner.id,
                login = repo.owner.login,
                avatarUrl = repo.owner.avatarUrl,
                htmlUrl = repo.owner.htmlUrl
            ),
            description = repo.description,
            htmlUrl = repo.htmlUrl,
            stargazersCount = repo.stars,
            forksCount = repo.forks,
            language = repo.language,
            topics = repo.topics,
            releasesUrl = "https://api.github.com/repos/${repo.owner.login}/${repo.name}/releases{/id}",
            updatedAt = repo.updatedAt,
            defaultBranch = repo.defaultBranch
        )
    }

    override suspend fun getLatestPublishedRelease(
        owner: String,
        repo: String,
        defaultBranch: String
    ): GithubRelease? {
        val releasesResult = github.safeApiCall<List<ReleaseNetwork>>(
            rateLimitHandler = appStateManager.rateLimitHandler,
            autoRetryOnRateLimit = false
        ) {
            get("/repos/$owner/$repo/releases") {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                parameter("per_page", 10)
            }
        }

        releasesResult.onFailure { error ->
            if (error is RateLimitException) {
                appStateManager.updateRateLimit(error.rateLimitInfo)
            }
        }

        val releases = releasesResult.getOrNull() ?: return null

        val latest = releases
            .asSequence()
            .filter { (it.draft != true) && (it.prerelease != true) }
            .sortedByDescending { it.publishedAt ?: it.createdAt ?: "" }
            .firstOrNull()
            ?: return null

        val processedLatestRelease = latest.copy(
            body = latest.body?.replace("<details>", "")
                ?.replace("</details>", "")
                ?.replace("<summary>", "")
                ?.replace("</summary>", "")
                ?.replace("\r\n", "\n")
                ?.let { rawMarkdown ->
                    preprocessMarkdown(
                        markdown = rawMarkdown,
                        baseUrl = "https://raw.githubusercontent.com/$owner/$repo/${defaultBranch}/"
                    )
                }
        )

        return processedLatestRelease.toDomain()
    }

    override suspend fun getReadme(
        owner: String,
        repo: String,
        defaultBranch: String
    ): Triple<String, String?, String>? {
        val attempts = readmeHelper.generateReadmeAttempts()
        val baseUrl = "https://raw.githubusercontent.com/$owner/$repo/$defaultBranch/"

        val primaryLang = localizationManager.getPrimaryLanguageCode()
        Logger.d {
            "Attempting to fetch README for language preference: ${localizationManager.getCurrentLanguageCode()}"
        }

        var lastError: Throwable? = null
        val foundReadmes = mutableMapOf<String, Pair<String, String?>>()

        for (attempt in attempts) {
            try {
                Logger.d { "Trying ${attempt.path} (priority: ${attempt.priority})..." }

                val rawMarkdownResult = github.safeApiCall<String>(
                    rateLimitHandler = appStateManager.rateLimitHandler,
                    autoRetryOnRateLimit = false
                ) {
                    get("$baseUrl${attempt.path}")
                }

                rawMarkdownResult.onFailure { error ->
                    if (error is RateLimitException) {
                        appStateManager.updateRateLimit(error.rateLimitInfo)
                    }
                }

                val rawMarkdown = rawMarkdownResult.getOrNull()

                if (rawMarkdown != null) {
                    Logger.d { "Successfully fetched ${attempt.path}" }

                    val processed = preprocessMarkdown(
                        markdown = rawMarkdown,
                        baseUrl = baseUrl
                    )

                    val detectedLang = readmeHelper.detectReadmeLanguage(processed)
                    Logger.d { "Detected language: ${detectedLang ?: "unknown"} for ${attempt.path}" }

                    foundReadmes[attempt.path] = Pair(processed, detectedLang)


                    if (attempt.filename != "README.md" && detectedLang == primaryLang) {
                        Logger.d { "Found localized README matching user language: ${attempt.path}" }
                        return Triple(processed, detectedLang, attempt.path)
                    }

                    if (attempt.filename.contains(".${primaryLang}.", ignoreCase = true) ||
                        attempt.filename.contains("-${primaryLang.uppercase()}.", ignoreCase = true)) {
                        Logger.d { "Found explicit language file for user: ${attempt.path}" }
                        return Triple(processed, detectedLang ?: primaryLang, attempt.path)
                    }

                    if (attempt.filename == "README.md") {
                        if (detectedLang == primaryLang) {
                            Logger.d { "Default README matches user language: ${attempt.path}" }
                            return Triple(processed, detectedLang, attempt.path)
                        }

                        if (primaryLang == "en" && detectedLang != null) {
                            Logger.d { "Default README is $detectedLang, continuing search for English version" }
                            continue
                        }

                        if (primaryLang != "en" && detectedLang == "en") {
                            Logger.d { "Default README is English, continuing search for $primaryLang version" }
                            continue
                        }

                        if (attempt.path == "README.md" && attempts.any { it.path.startsWith("docs/") }) {
                            Logger.d { "Found root README.md, but checking docs/ folder first" }
                            continue
                        }
                    }

                    if (primaryLang == "en" &&
                        (attempt.filename.contains(".en.", ignoreCase = true) ||
                                attempt.filename.contains("-EN.", ignoreCase = true))) {
                        Logger.d { "Found English README for English user: ${attempt.path}" }
                        return Triple(processed, "en", attempt.path)
                    }
                }
            } catch (e: Throwable) {
                lastError = e
                Logger.d { "Failed to fetch ${attempt.path}: ${e.message}" }
            }
        }

        if (foundReadmes.isNotEmpty()) {
            foundReadmes.entries.firstOrNull { it.value.second == primaryLang }?.let {
                Logger.d { "Fallback: Using README matching user language: ${it.key}" }
                return Triple(it.value.first, it.value.second, it.key)
            }

            if (primaryLang == "en") {
                foundReadmes.entries.firstOrNull { it.value.second == "en" }?.let {
                    Logger.d { "Fallback: Using English README: ${it.key}" }
                    return Triple(it.value.first, it.value.second, it.key)
                }
            }

            foundReadmes.entries.firstOrNull { it.key == "README.md" }?.let {
                Logger.d { "Fallback: Using root README.md (language: ${it.value.second}): ${it.key}" }
                return Triple(it.value.first, it.value.second, it.key)
            }

            foundReadmes.entries.firstOrNull { it.key.startsWith(".github/") }?.let {
                Logger.d { "Fallback: Using .github README: ${it.key}" }
                return Triple(it.value.first, it.value.second, it.key)
            }

            foundReadmes.entries.first().let {
                Logger.d { "Fallback: Using first found README: ${it.key}" }
                return Triple(it.value.first, it.value.second, it.key)
            }
        }

        Logger.e {
            "Failed to fetch any README variant. Last error: ${lastError?.message}"
        }
        return null
    }

    override suspend fun getRepoStats(owner: String, repo: String): RepoStats {
        val infoResult = github.safeApiCall<RepoInfoNetwork>(
            rateLimitHandler = appStateManager.rateLimitHandler,
            autoRetryOnRateLimit = false
        ) {
            get("/repos/$owner/$repo") {
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }
        }

        val info = infoResult.getOrElse { error ->
            if (error is RateLimitException) {
                appStateManager.updateRateLimit(error.rateLimitInfo)
            }
            throw error
        }

        return RepoStats(
            stars = info.stars,
            forks = info.forks,
            openIssues = info.openIssues,
        )
    }

    override suspend fun getUserProfile(username: String): GithubUserProfile {
        val userResult = github.safeApiCall<UserProfileNetwork>(
            rateLimitHandler = appStateManager.rateLimitHandler,
            autoRetryOnRateLimit = false
        ) {
            get("/users/$username") {
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }
        }

        val user = userResult.getOrElse { error ->
            if (error is RateLimitException) {
                appStateManager.updateRateLimit(error.rateLimitInfo)
            }
            throw error
        }

        return GithubUserProfile(
            id = user.id,
            login = user.login,
            name = user.name,
            bio = user.bio,
            avatarUrl = user.avatarUrl,
            htmlUrl = user.htmlUrl,
            followers = user.followers,
            following = user.following,
            publicRepos = user.publicRepos,
            location = user.location,
            company = user.company,
            blog = user.blog,
            twitterUsername = user.twitterUsername
        )
    }
}