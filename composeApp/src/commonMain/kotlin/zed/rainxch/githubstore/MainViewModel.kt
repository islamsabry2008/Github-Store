package zed.rainxch.githubstore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.githubstore.core.data.TokenDataSource

class MainViewModel(
    private val tokenDataSource: TokenDataSource
) : ViewModel() {

    private val _state = MutableStateFlow(MainState())
    val state = _state.asStateFlow()

    init {
        // 1) Perform an explicit load for the initial state to avoid UI flicker.
        viewModelScope.launch {
            val initialToken = tokenDataSource.load()
            _state.update {
                it.copy(
                    isCheckingAuth = false,
                    isLoggedIn = initialToken != null
                )
            }
            Logger.d("MainViewmodel") { initialToken.toString() }
        }

        // 2) Observe subsequent updates (skip the initial cached null value from StateFlow).
        viewModelScope.launch {
            tokenDataSource
                .tokenFlow
                .drop(1) // skip the initial placeholder value to prevent auth->home flicker
                .distinctUntilChanged()
                .collect { authInfo ->
                    _state.update { it.copy(isLoggedIn = authInfo != null) }
                    Logger.d("MainViewmodel") { authInfo.toString() }
                }
        }
    }
}