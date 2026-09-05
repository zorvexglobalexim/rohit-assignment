package com.rohit.videoprocessor.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rohit.videoprocessor.ui.debug.DebugScreen
import com.rohit.videoprocessor.ui.debug.DebugSettingsScreen
import com.rohit.videoprocessor.ui.home.HomeScreen
import com.rohit.videoprocessor.ui.person.PersonDetailScreen
import com.rohit.videoprocessor.ui.processing.ProcessingScreen
import com.rohit.videoprocessor.ui.result.ResultScreen
import com.rohit.videoprocessor.viewmodel.VideoViewModel

/**
 * A single [VideoViewModel] instance (Activity-scoped, created here) backs
 * every screen in the flow so selection/processing state survives
 * navigation between them.
 */
@Composable
fun AppNavGraph(viewModel: VideoViewModel = viewModel()) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onProcessRequested = { navController.navigate(Screen.Processing.route) },
                onDebugSettingsRequested = { navController.navigate(Screen.DebugSettings.route) },
            )
        }
        composable(Screen.DebugSettings.route) {
            DebugSettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Debug.route) {
            DebugScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Processing.route) {
            ProcessingScreen(
                viewModel = viewModel,
                onFinished = {
                    navController.navigate(Screen.Result.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onCancelled = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
            )
        }
        composable(Screen.Result.route) {
            ResultScreen(
                viewModel = viewModel,
                onReset = {
                    viewModel.reset()
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onBack = { navController.popBackStack() },
                onDebugInfoRequested = { navController.navigate(Screen.Debug.route) },
                onPersonSelected = { personId -> navController.navigate(Screen.PersonDetail.route(personId)) },
            )
        }
        composable(
            route = Screen.PersonDetail.route,
            arguments = listOf(navArgument(Screen.PersonDetail.ARG_PERSON_ID) { type = NavType.IntType }),
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getInt(Screen.PersonDetail.ARG_PERSON_ID) ?: return@composable
            PersonDetailScreen(
                viewModel = viewModel,
                personId = personId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
