package zed.rainxch.githubstore.feature.settings.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import zed.rainxch.githubstore.core.data.data_source.TokenDataSource
import zed.rainxch.githubstore.feature.settings.domain.repository.SettingsRepository

class SettingsRepositoryImpl(
    private val tokenDataSource: TokenDataSource,
) : SettingsRepository {
    override val isUserLoggedIn: Flow<Boolean>
        get() = tokenDataSource
            .tokenFlow
            .map {
                it != null
            }
            .flowOn(Dispatchers.IO)

    override suspend fun logout() {
        tokenDataSource.clear()
    }
}