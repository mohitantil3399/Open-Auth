package com.example.authentication.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.authentication.ui.screens.AddAccountScreen
import com.example.authentication.ui.screens.MainScreen
import com.example.authentication.viewmodel.AuthViewModel

object Routes {
    const val MAIN = "main"
    const val ADD_ACCOUNT = "add_account"
}

@Composable
fun OpenAuthNavGraph(
    navController: NavHostController,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        modifier = modifier
    ) {
        composable(Routes.MAIN) {
            MainScreen(
                viewModel = viewModel,
                onAddAccountClick = { navController.navigate(Routes.ADD_ACCOUNT) }
            )
        }
        composable(Routes.ADD_ACCOUNT) {
            AddAccountScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
