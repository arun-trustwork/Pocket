package com.cardvault.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardvault.app.data.model.FilterScope

/**
 * The "menu kind option on top" — a horizontally scrollable filter row directly under the
 * search bar, letting the user switch between All / Vendors / Customers / Businesses / Projects.
 */
@Composable
fun FilterMenu(
    selected: FilterScope,
    onSelect: (FilterScope) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        FilterScope.ALL to "All",
        FilterScope.VENDORS to "Vendors",
        FilterScope.CUSTOMERS to "Customers",
        FilterScope.BUSINESSES to "Businesses",
        FilterScope.PROJECTS to "Projects"
    )

    ScrollableTabRow(
        selectedTabIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0),
        edgePadding = 16.dp,
        modifier = modifier
    ) {
        options.forEach { (scope, label) ->
            Tab(
                selected = scope == selected,
                onClick = { onSelect(scope) },
                text = { Text(label, modifier = Modifier.padding(vertical = 4.dp)) }
            )
        }
    }
}
