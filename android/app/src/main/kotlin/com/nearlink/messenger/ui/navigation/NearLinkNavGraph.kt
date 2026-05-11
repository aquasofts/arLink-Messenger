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
import com.nearlink.messenger.ui.screens.qr.QrContactScreen
import com.nearlink.messenger.ui.screens.qr.QrMessageMode
import com.nearlink.messenger.ui.screens.qr.QrMessageScreen
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
    const val QrContact = "qr/contact"
    const val QrMessageShow = "qr/message/show/{convId}"
    fun qrMessageShow(convId: String) = "qr/message/show/$convId"
    const val QrMessageScan = "qr/message/scan/{convId}"
    fun qrMessageScan(convId: String) = "qr/message/scan/$convId"
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
                onDone = { nav.navigate(Routes.Home) { popUpTo(Routes.Onboarding) { inclusive = true } } },
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
                onOpenQrContact = { nav.navigate(Routes.QrContact) },
                onOpenHotspotPair = { nav.navigate(Routes.Pair) },
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
                onShowQrMessage = { nav.navigate(Routes.qrMessageShow(it)) },
                onScanQrMessage = { nav.navigate(Routes.qrMessageScan(it)) },
                onOpenContactQr = { nav.navigate(Routes.QrContact) },
            )
        }
        composable(Routes.QrContact) {
            QrContactScreen(
                viewModel = hiltViewModel(),
                onBack = { nav.popBackStack() },
                onImported = { convId -> nav.navigate(Routes.chat(convId)) },
            )
        }
        composable(
            Routes.QrMessageShow,
            arguments = listOf(navArgument("convId") { type = NavType.StringType }),
        ) {
            QrMessageScreen(
                viewModel = hiltViewModel(),
                mode = QrMessageMode.SHOW,
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            Routes.QrMessageScan,
            arguments = listOf(navArgument("convId") { type = NavType.StringType }),
        ) {
            QrMessageScreen(
                viewModel = hiltViewModel(),
                mode = QrMessageMode.SCAN,
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
