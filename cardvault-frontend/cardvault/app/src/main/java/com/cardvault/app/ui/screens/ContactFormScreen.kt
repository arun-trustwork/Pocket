package com.cardvault.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardvault.app.data.model.Contact
import com.cardvault.app.data.model.ContactType
import com.cardvault.app.data.repository.ContactRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContactFormScreen(
    repository: ContactRepository,
    initial: Contact,
    onSaved: () -> Unit
) {
    var contact by remember { mutableStateOf(initial) }
    var tagInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = { Text("Review details") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = contact.name, onValueChange = { contact = contact.copy(name = it) },
                label = { Text("Name") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = contact.company.orEmpty(),
                onValueChange = { contact = contact.copy(company = it) },
                label = { Text("Company") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = contact.phones.firstOrNull().orEmpty(),
                onValueChange = { contact = contact.copy(phones = listOf(it)) },
                label = { Text("Phone") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            Text("Type", style = MaterialTheme.typography.labelMedium)
            Row {
                ContactType.entries.forEach { type ->
                    FilterChip(
                        selected = contact.type == type,
                        onClick = { contact = contact.copy(type = type) },
                        label = { Text(type.name.lowercase().replaceFirstChar(Char::uppercase)) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Text("Tags", style = MaterialTheme.typography.labelMedium)
            FlowRow(modifier = Modifier.padding(vertical = 4.dp)) {
                contact.tags.forEach { tag ->
                    AssistChip(
                        onClick = { contact = contact.copy(tags = contact.tags - tag) },
                        label = { Text(tag) },
                        modifier = Modifier.padding(end = 6.dp, bottom = 6.dp)
                    )
                }
            }
            OutlinedTextField(
                value = tagInput,
                onValueChange = { tagInput = it },
                label = { Text("Add a tag, press enter") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                    if (tagInput.isNotBlank()) {
                        contact = contact.copy(tags = contact.tags + tagInput.trim())
                        tagInput = ""
                    }
                })
            )

            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    scope.launch {
                        repository.saveContact(contact)
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save contact") }
        }
    }
}
