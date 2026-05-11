package com.nearlink.messenger.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nearlink.messenger.ui.screens.chat.ChatScreen
import com.nearlink.messenger.ui.screens.contacts.ContactsScreen
import com.nearlink.messenger.ui.screens.home.HomeScreen
import com.nearlink.messenger.ui.screens.onboarding.OnboardingScreen
import com.nearlink.messenger.ui.screens.pair.PairScreen
import com.nearlink.messenger.ui.screens.pair.SafetyNumberScreen
import com.nearlink.messenger.ui.screens.permission.PermissionScreen
import com.nearlink.messenger.ui.screens.profile.ProfileScreen
import com.nearlink.messenger.ui.screens.settings.SettingsScreen

object Routes {
    const val Onboarding = "onboarding"
    const val Permission = "permission"
    const val Home = "home"
    const val Contacts = "contacts"
    const val Pair = "pair"
    const val Safety = "safety/{deviceId}"
    fun safety(deviceId: String) = "safety/$deviceId"
    const val Chat = "chat/{convId}"
    fun chat(convId: String) = "chat/$convId"
    const val Profile = "profile"
    const val Settings = "settings"
}

@Composable
fun NearLinkNavGraph(startDestination: String = Routes.Onboarding) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = startDestination) {
        composable(Routes.Onboarding) {
            OnboardingScreen(
                viewModel = hiltViewModel(),
                onDone = { nav.navigate(Routes.Permission) { popUpTo(Routes.Onboarding) { inclusive = true } } },
            )
        }
        composable(Routes.Permission) {
            PermissionScreen(onDone = {
                nav.navigate(Routes.Home) { popUpTo(Routes.Permission) { inclusive = true } }
            })
        }
        composable(Routes.Home) {
            HomeScreen(
                viewModel = hiltViewModel(),
                onOpenChat = { nav.navigate(Routes.chat(it)) },
                onOpenContacts = { nav.navigate(Routes.Contacts) },
                onOpenProfile = { nav.navigate(Routes.Profile) },
                onOpenSettings = { nav.navigate(Routes.Settings) },
                onOpenPair = { nav.navigate(Routes.Pair) },
            )
        }
        composable(Routes.Contacts) {
            ContactsScreen(
                viewModel = hiltViewModel(),
                onBack = { nav.popBackStack() },
                onOpenChat = { nav.navigate(Routes.chat(it)) },
                onPair = { nav.navigate(Routes.Pair) },
            )
        }
        composable(Routes.Pair) {
            PairScreen(
                viewModel = hiltViewModel(),
                onBack = { nav.popBackStack() },
                onPaired = { peerDeviceId -> nav.navigate(Routes.safety(peerDeviceId)) },
            )
        }
        composable(
            Routes.Safety,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType }),
        ) {
            SafetyNumberScreen(
                viewModel = hiltViewModel(),
                onBack = { nav.popBackStack() },
                onConfirmed = { convId -> nav.navigate(Routes.chat(convId)) {
                    popUpTo(Routes.Home)
                } },
            )
        }
        composable(
            Routes.Chat,
            arguments = listOf(navArgument("convId") { type = NavType.StringType }),
        ) {
            ChatScreen(
                viewModel = hiltViewModel(),
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.Profile) {
            ProfileScreen(viewModel = hiltViewModel(), onBack = { nav.popBackStack() })
        }
        composable(Routes.Settings) {
            SettingsScreen(viewModel = hiltViewModel(), onBack = { nav.popBackStack() })
        }
    }
}
