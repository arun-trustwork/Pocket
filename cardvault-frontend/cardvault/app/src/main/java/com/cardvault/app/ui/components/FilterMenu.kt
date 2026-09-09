package com.cardvault.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.cardvault.app.data.model.FilterScope

/**
 * The filter dropdown menu located in the top app bar.
 */
@Composable
fun FilterMenu(
    selected: FilterScope,
    onSelect: (FilterScope) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        FilterScope.ALL to "All Contacts",
        FilterScope.VENDORS to "Vendors",
        FilterScope.CONSUMERS to "Consumers",
        FilterScope.BUSINESSES to "Businesses"
    )

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.FilterList, contentDescription = "Filter")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (scope, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(scope)
                        expanded = false
                    },
                    trailingIcon = if (selected == scope) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else null
                )
            }
        }
    }
}
