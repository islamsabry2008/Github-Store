package zed.rainxch.githubstore.feature.home.data.data_source

/**
 * Data source for fetching pre-cached trending repositories from GitHub
 */
import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import zed.rainxch.githubstore.core.domain.Platform
import zed.rainxch.githubstore.core.domain.model.GithubRepoSummary
import zed.rainxch.githubstore.core.domain.model.PlatformType

/**
 * Data source for fetching pre-cached trending repositories from GitHub
 * Uses a dedicated HTTP client (not the GitHub API client) since this fetches
 * static JSON files from raw.githubusercontent.com (no auth or rate limits needed)
 */
class CachedTrendingDataSource(
    private val platform: Platform
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }

        install(HttpRequestRetry) {
            maxRetries = 2
            retryOnServerErrors(maxRetries = 2)
            exponentialDelay()
        }

        expectSuccess = false
    }

    private val baseUrl = "https://raw.githubusercontent.com/rainxchzed/Github-Store/main/cached-data/trending"

    /**
     * Fetch cached trending repositories for the current platform
     * Returns null if fetch fails or data is unavailable
     */
    suspend fun getCachedTrendingRepos(): CachedRepoResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val platformName = when (platform.type) {
                    PlatformType.ANDROID -> "android"
                    PlatformType.WINDOWS -> "windows"
                    PlatformType.MACOS -> "macos"
                    PlatformType.LINUX -> "linux"
                }

                val url = "$baseUrl/$platformName.json"

                Logger.d { "Fetching cached trending repos from: $url" }

                val response: HttpResponse = httpClient.get(url)

                when {
                    response.status.isSuccess() -> {
                        val cachedData = response.body<CachedRepoResponse>()

                        Logger.d { "✓ Successfully loaded ${cachedData.repositories.size} cached repos" }
                        Logger.d { "Last updated: ${cachedData.lastUpdated}" }

                        cachedData
                    }
                    response.status.value == 404 -> {
                        Logger.w { "Cached data not found (404) - may not be generated yet" }
                        null
                    }
                    else -> {
                        Logger.e { "Failed to fetch cached repos: HTTP ${response.status.value}" }
                        null
                    }
                }
            } catch (e: HttpRequestTimeoutException) {
                Logger.e { "Timeout fetching cached trending repos" }
                null
            } catch (e: Exception) {
                Logger.e { "Error fetching cached trending repos: ${e.message}" }
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Clean up resources when done
     */
    fun close() {
        httpClient.close()
    }
}

@Serializable
data class CachedRepoResponse(
    val platform: String,
    val lastUpdated: String,
    val totalCount: Int,
    val repositories: List<GithubRepoSummary>
)