package com.cardvault.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardvault.app.data.model.Contact
import com.cardvault.app.data.model.FilterScope
import com.cardvault.app.data.repository.ContactRepository
import com.cardvault.app.ui.components.FilterMenu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: ContactRepository,
    onOpenContact: (Contact) -> Unit,
    onScanClick: () -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(repository))
) {
    // Collect from ViewModel StateFlow — survives recomposition & back-navigation
    val contacts by vm.contacts.collectAsState()
    val scope    by vm.scope.collectAsState()
    val query    by vm.query.collectAsState()
    val loading  by vm.isLoading.collectAsState()
    val error    by vm.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (scope == FilterScope.ALL) "CardVault"
                        else "CardVault: ${scope.name.lowercase().replaceFirstChar(Char::uppercase)}"
                    )
                },
                actions = {
                    FilterMenu(selected = scope, onSelect = { vm.setScope(it) })
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onScanClick) {
                Icon(Icons.Filled.Add, contentDescription = "Scan a card")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.setQuery(it) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search name, tag, company...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            when {
                // ── Loading state ──────────────────────────────────────────────
                loading && contacts.isEmpty() -> {
                    Box(
                        Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Loading contacts…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ── Error state ────────────────────────────────────────────────
                error != null && contacts.isEmpty() -> {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // ── Empty state ────────────────────────────────────────────────
                contacts.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().padding(32.dp)) {
                        Text("No contacts yet — tap + to scan your first card.")
                    }
                }

                // ── Contact list ───────────────────────────────────────────────
                else -> {
                    LazyColumn(Modifier.weight(1f)) {
                        items(contacts, key = { it.id }) { contact ->
                            ListItem(
                                headlineContent = { Text(contact.name) },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(contact.company, contact.tags.firstOrNull())
                                            .joinToString(" · ")
                                    )
                                },
                                modifier = Modifier.clickableRow { onOpenContact(contact) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

// Small helper so ListItem stays readable above without importing clickable inline each time.
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
