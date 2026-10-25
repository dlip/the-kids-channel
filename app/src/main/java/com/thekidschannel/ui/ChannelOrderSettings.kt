package com.thekidschannel.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.thekidschannel.media.ChannelOrder

@Composable
internal fun ChannelOrderSettings(
    order: ChannelOrder,
    descending: Boolean,
    onOrderChanged: (ChannelOrder) -> Unit,
    onDescendingChanged: (Boolean) -> Unit,
) {
    Text("Channel order", style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() })
    Column(Modifier.selectableGroup()) {
        ChannelOrder.entries.forEach { option ->
            OrderOption(
                label = option.label,
                selected = order == option,
                description = if (option == ChannelOrder.DIRECTORY_AGE)
                    "Uses the folder's last-modified date; unknown dates appear last" else null,
                onClick = { onOrderChanged(option) },
            )
        }
    }
    Text("Direction", style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() })
    Column(Modifier.selectableGroup()) {
        listOf(false, true).forEach { option ->
            OrderOption(
                label = if (option) "Descending" else "Ascending",
                selected = descending == option,
                description = when (order) {
                    ChannelOrder.WATCH_TIME -> if (option) "Most watched first" else "Least watched first"
                    ChannelOrder.ALPHABETICAL -> if (option) "Z to A" else "A to Z"
                    ChannelOrder.DIRECTORY_AGE -> if (option) "Newest first" else "Oldest first"
                },
                onClick = { onDescendingChanged(option) },
            )
        }
    }
}

@Composable
private fun OrderOption(label: String, selected: Boolean, description: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = description?.let { { Text(it) } },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = { if (!selected) onClick() },
        ),
    )
}
