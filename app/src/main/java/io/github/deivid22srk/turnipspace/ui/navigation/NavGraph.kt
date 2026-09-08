package io.github.deivid22srk.turnipspace.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.deivid22srk.turnipspace.TurnipSpaceApp
import io.github.deivid22srk.turnipspace.ui.drivers.DriverManagerScreen
import io.github.deivid22srk.turnipspace.ui.home.HomeScreen
import io.github.deivid22srk.turnipspace.ui.onboarding.OnboardingScreen
import io.github.deivid22srk.turnipspace.ui.settings.SettingsScreen
import io.github.deivid22srk.turnipspace.ui.space.SpaceDetailScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val DRIVERS = "drivers"
    const val SETTINGS = "settings"
    const val SPACE = "space/{spaceId}"
    fun space(spaceId: String) = "space/$spaceId"
}

@Composable
fun TurnipSpaceAppRoot(startOnHome: Boolean) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = if (startOnHome) Routes.HOME else Routes.ONBOARDING,
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    // Persist so onboarding plays only once per install.
                    (navController.context.applicationContext as TurnipSpaceApp)
                        .container.settings.onboardingDone = true
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenDrivers = { navController.navigate(Routes.DRIVERS) },
                onOpenSpace = { spaceId -> navController.navigate(Routes.space(spaceId)) },
            )
        }
        composable(Routes.DRIVERS) {
            DriverManagerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.SPACE,
            arguments = listOf(navArgument("spaceId") { type = NavType.StringType }),
        ) { entry ->
            val spaceId = entry.arguments?.getString("spaceId").orEmpty()
            SpaceDetailScreen(spaceId = spaceId, onBack = { navController.popBackStack() })
        }
    }
}
