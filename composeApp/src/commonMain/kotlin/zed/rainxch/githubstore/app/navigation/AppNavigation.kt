package zed.rainxch.githubstore.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import zed.rainxch.githubstore.MainAction
import zed.rainxch.githubstore.MainState
import zed.rainxch.githubstore.app.app_state.components.RateLimitDialog
import zed.rainxch.githubstore.feature.auth.presentation.AuthenticationRoot
import zed.rainxch.githubstore.feature.details.presentation.DetailsRoot
import zed.rainxch.githubstore.feature.home.presentation.HomeRoot
import zed.rainxch.githubstore.feature.search.presentation.SearchRoot
import zed.rainxch.githubstore.feature.settings.presentation.SettingsRoot

@Composable
fun AppNavigation(
    onAuthenticationChecked: () -> Unit = { },
    state: MainState,
    onAction: (MainAction) -> Unit
) {
    val navHostController = rememberNavController()

    LaunchedEffect(state.isCheckingAuth) {
        if (!state.isCheckingAuth) {
            onAuthenticationChecked()
        }
    }

    if (state.isCheckingAuth) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        return
    }


    if (state.showRateLimitDialog && state.rateLimitInfo != null) {
        RateLimitDialog(
            rateLimitInfo = state.rateLimitInfo,
            isAuthenticated = state.isLoggedIn,
            onDismiss = {
                onAction(MainAction.DismissRateLimitDialog)
            },
            onSignIn = {
                onAction(MainAction.DismissRateLimitDialog)

                navHostController.navigate(GithubStoreGraph.AuthenticationScreen) {
                    popUpTo(0)
                }
            }
        )
    }

    NavHost(
        navController = navHostController,
        startDestination = GithubStoreGraph.HomeScreen
    ) {
        composable<GithubStoreGraph.HomeScreen> {
            HomeRoot(
                onNavigateToSearch = {
                    navHostController.navigate(GithubStoreGraph.SearchScreen)
                },
                onNavigateToSettings = {
                    navHostController.navigate(GithubStoreGraph.SettingsScreen)
                },
                onNavigateToDetails = { repo ->
                    navHostController.navigate(
                        GithubStoreGraph.DetailsScreen(
                            repositoryId = repo.id.toInt()
                        )
                    )
                }
            )
        }

        composable<GithubStoreGraph.SearchScreen> {
            SearchRoot(
                onNavigateBack = {
                    navHostController.navigateUp()
                },
                onNavigateToDetails = { repo ->
                    navHostController.navigate(
                        GithubStoreGraph.DetailsScreen(
                            repositoryId = repo.id.toInt()
                        )
                    )
                }
            )
        }

        composable<GithubStoreGraph.DetailsScreen> { backStackEntry ->
            val args = backStackEntry.toRoute<GithubStoreGraph.DetailsScreen>()

            DetailsRoot(
                onNavigateBack = {
                    navHostController.navigateUp()
                },
                onOpenRepositoryInApp = { repoId ->
                    navHostController.navigate(
                        GithubStoreGraph.DetailsScreen(
                            repositoryId = repoId
                        )
                    )
                },
                viewModel = koinViewModel {
                    parametersOf(args.repositoryId)
                }
            )
        }

        composable<GithubStoreGraph.AuthenticationScreen> {
            AuthenticationRoot(
                onNavigateToHome = {
                    navHostController.navigate(GithubStoreGraph.HomeScreen) {
                        popUpTo(GithubStoreGraph.AuthenticationScreen) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<GithubStoreGraph.SettingsScreen> {
            SettingsRoot(
                onNavigateBack = {
                    navHostController.navigateUp()
                }
            )
        }
    }
}
