package zed.rainxch.githubstore.feature.settings.presentation

sealed interface SettingsEvent {
    data object OnLogoutSuccessful : SettingsEvent
    data class OnLogoutError(val message: String) : SettingsEvent
}