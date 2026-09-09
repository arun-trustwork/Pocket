package com.cardvault.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardvault.app.data.model.Contact
import com.cardvault.app.data.model.FilterScope
import com.cardvault.app.data.repository.ContactRepository
import com.cardvault.app.ui.components.FilterMenu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: ContactRepository,
    onOpenContact: (Contact) -> Unit,
    onScanClick: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(FilterScope.ALL) }
    val contacts by repository.observeContacts(scope, query)
        .collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(if (scope == FilterScope.ALL) "CardVault" else "CardVault: ${scope.name.lowercase().replaceFirstChar(Char::uppercase)}") 
                },
                actions = {
                    FilterMenu(selected = scope, onSelect = { scope = it })
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
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search name, tag, company...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            if (contacts.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp)) {
                    Text("No contacts yet — tap + to scan your first card.")
                }
            } else {
                LazyColumn {
                    items(contacts, key = { it.id }) { contact ->
                        ListItem(
                            headlineContent = { Text(contact.name) },
                            supportingContent = {
                                Text(listOfNotNull(contact.company, contact.tags.firstOrNull())
                                    .joinToString(" · "))
                            },
                            modifier = Modifier.clickableRow { onOpenContact(contact) }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

// Small helper so ListItem stays readable above without importing clickable inline each time.
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
