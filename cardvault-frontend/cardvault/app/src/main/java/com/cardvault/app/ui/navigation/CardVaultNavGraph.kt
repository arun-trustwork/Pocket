package com.cardvault.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cardvault.app.data.model.Contact
import com.cardvault.app.data.model.User
import com.cardvault.app.data.repository.AuthRepository
import com.cardvault.app.data.repository.ContactRepository
import com.cardvault.app.ui.screens.*
import com.google.gson.Gson

private sealed class Dest(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Dest("home", "Home", Icons.Filled.Home)
    object Scan : Dest("scan", "Scan", Icons.Filled.CameraAlt)
    object MyCard : Dest("mycard", "My card", Icons.Filled.QrCode)
}

private val bottomDestinations = listOf(Dest.Home, Dest.Scan, Dest.MyCard)

@Composable
fun CardVaultApp(auth: AuthRepository, repository: ContactRepository) {
    val user by auth.currentUser.collectAsState(initial = null)

    if (user == null) {
        LoginScreen(auth = auth, onLoggedIn = { /* currentUser flow updates automatically */ })
        return
    }

    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val current = backStack?.destination?.route
            NavigationBar {
                bottomDestinations.forEach { dest ->
                    NavigationBarItem(
                        selected = current == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(Dest.Home.route) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Home.route,
            modifier = androidx.compose.ui.Modifier.padding(padding)
        ) {
            composable(Dest.Home.route) {
                HomeScreen(
                    repository = repository,
                    onOpenContact = { contact ->
                        navController.navigate("form/${Gson().toJson(contact)}")
                    },
                    onScanClick = { navController.navigate(Dest.Scan.route) }
                )
            }
            composable(Dest.Scan.route) {
                ScanScreen(
                    repository = repository,
                    onExtracted = { contact ->
                        navController.navigate("form/${Gson().toJson(contact)}")
                    }
                )
            }
            composable(Dest.MyCard.route) {
                MyCardScreen(user = user as User)
            }
            composable("form/{contactJson}") { backStackEntry ->
                val json = backStackEntry.arguments?.getString("contactJson").orEmpty()
                val contact = Gson().fromJson(json, Contact::class.java)
                ContactFormScreen(
                    repository = repository,
                    initial = contact,
                    onSaved = { navController.popBackStack(Dest.Home.route, inclusive = false) }
                )
            }
        }
    }
}
