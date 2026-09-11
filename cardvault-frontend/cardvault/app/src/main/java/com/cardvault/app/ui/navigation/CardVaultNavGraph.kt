package com.cardvault.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.viewmodel.compose.viewModel
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

private sealed class Dest(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home   : Dest("home",   "Home",    Icons.Filled.Home)
    object Scan   : Dest("scan",   "Scan",    Icons.Filled.CameraAlt)
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

    // Single HomeViewModel instance shared between HomeScreen and form route
    // so we can do O(1) contact lookup by id without a second network call.
    val homeVm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(repository))

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
                    vm = homeVm,
                    onOpenContact = { contact ->
                        // Fix D: pass only the ID — the ViewModel already holds the full object
                        navController.navigate("form/${contact.id}")
                    },
                    onScanClick = { navController.navigate(Dest.Scan.route) }
                )
            }
            composable(Dest.Scan.route) {
                ScanScreen(
                    repository = repository,
                    onExtracted = { contact ->
                        // Pre-stash the freshly scanned contact into the ViewModel cache
                        // so the form route can look it up by ID instantly (no second network call).
                        homeVm.stashContact(contact)
                        navController.navigate("form/${contact.id}")
                    }
                )
            }
            composable(Dest.MyCard.route) {
                MyCardScreen(user = user as User)
            }
            // Route carries only the UUID — no JSON in the URL
            composable("form/{contactId}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("contactId").orEmpty()
                // O(1) lookup from in-memory StateFlow cache (works for both tapped & scanned contacts)
                val contact = homeVm.getContactById(id) ?: Contact(id = id)
                ContactFormScreen(
                    repository = repository,
                    initial = contact,
                    vm = homeVm,   // needed for optimistic save
                    onSaved = { navController.popBackStack(Dest.Home.route, inclusive = false) }
                )
            }
        }
    }
}
