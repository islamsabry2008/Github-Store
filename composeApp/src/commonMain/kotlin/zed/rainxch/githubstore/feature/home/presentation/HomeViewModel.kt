package zed.rainxch.githubstore.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.githubstore.core.data.TokenDataSource
import zed.rainxch.githubstore.feature.home.domain.repository.HomeRepository
import zed.rainxch.githubstore.feature.home.presentation.model.HomeCategory

class HomeViewModel(
    private val homeRepository: HomeRepository,
    private val tokenDataSource: TokenDataSource
) : ViewModel() {

    private var hasLoadedInitialData = false
    private var currentJob: Job? = null
    private var currentPage = 1

    private val _state = MutableStateFlow(HomeState())
    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                loadRepos(isInitial = true)
                hasLoadedInitialData = true
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = HomeState()
        )

    private fun loadRepos(isInitial: Boolean = false, category: HomeCategory? = null) {
        currentJob?.cancel()

        if (isInitial) {
            currentPage = 1
        }

        val targetCategory = category ?: _state.value.currentCategory

        currentJob = viewModelScope.launch {
            val token = tokenDataSource.current()

            if (token == null) {
                _state.update { it.copy(needsAuth = true, isLoading = false, isLoadingMore = false) }
                return@launch
            }

            _state.update {
                it.copy(
                    isLoading = isInitial,
                    isLoadingMore = !isInitial,
                    errorMessage = null,
                    needsAuth = false,
                    currentCategory = targetCategory,
                    repos = if (isInitial) emptyList() else it.repos
                )
            }

            try {
                val flow = when (targetCategory) {
                    HomeCategory.POPULAR -> homeRepository.getTrendingRepositories(currentPage)
                    HomeCategory.LATEST_UPDATED -> homeRepository.getLatestUpdated(currentPage)
                    HomeCategory.NEW -> homeRepository.getNew(currentPage)
                }

                flow.collect { paginatedRepos ->
                    Logger.d("Home viewmodel") {
                        "Loaded repos count=${paginatedRepos.repos.size}, hasMore=${paginatedRepos.hasMore}"
                    }

                    _state.update { currentState ->
                        val updatedRepos = if (isInitial) {
                            paginatedRepos.repos
                        } else {
                            currentState.repos + paginatedRepos.repos
                        }

                        currentState.copy(
                            repos = updatedRepos,
                            isLoading = false,
                            isLoadingMore = false,
                            hasMorePages = paginatedRepos.hasMore,
                        )
                    }
                }

            } catch (t: Throwable) {
                Logger.w("Home viewmodel", t) { "Failed to load repos" }

                if (t is CancellationException) {
                    throw t
                }

                Logger.w("Home viewmodel", t) { "Failed" }

                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = t.message ?: "Failed to load data"
                    )
                }
            }
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Refresh -> {
                currentPage = 1
                loadRepos(isInitial = true)
            }

            HomeAction.Retry -> {
                loadRepos(isInitial = true)
            }

            HomeAction.LoadMore -> {
                if (currentJob?.isActive == true) return

                if (!_state.value.isLoadingMore && _state.value.hasMorePages) {
                    currentPage++
                    loadRepos(isInitial = false)
                }
            }

            is HomeAction.SwitchCategory -> {
                currentPage = 1
                loadRepos(isInitial = true, category = action.category)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
    }
}