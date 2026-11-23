package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FilterListOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.PaymentAlertEntity
import com.example.data.preferences.PaymentChannel
import com.example.domain.DateFilterOption
import com.example.domain.PaymentNotificationParser
import com.example.domain.TransactionSortOption
import com.example.ui.components.PaymentAlertRow
import com.example.ui.viewmodel.PaySpeakerUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    uiState: PaySpeakerUiState,
    onSearchQueryChange: (String) -> Unit,
    onFilterChannelSelect: (PaymentChannel?) -> Unit,
    onDateFilterSelect: (DateFilterOption) -> Unit,
    onSortOptionSelect: (TransactionSortOption) -> Unit,
    onResetFilters: () -> Unit,
    onReplayAlert: (PaymentAlertEntity) -> Unit,
    onDeleteAlert: (Long) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val showDateHeaders = uiState.selectedSortOption == TransactionSortOption.NEWEST_FIRST ||
        uiState.selectedSortOption == TransactionSortOption.OLDEST_FIRST

    val hasActiveFilters = uiState.searchQuery.isNotBlank() ||
        uiState.selectedFilterChannel != null ||
        uiState.selectedDateFilter != DateFilterOption.ALL_TIME

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.history_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    if (uiState.totalAlertsCount > 0) {
                        TextButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("clear_history_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                            Text(stringResource(R.string.history_clear_all))
                        }
                    }
                }
            }

            // Summary Banner
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.history_all_time_total),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Rs. ${PaymentNotificationParser.formatAmount(uiState.filteredTotal)}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            val countText = if (hasActiveFilters && uiState.totalAlertsCount > 0) {
                                "${uiState.filteredAlerts.size} of ${uiState.totalAlertsCount}"
                            } else {
                                "${uiState.filteredAlerts.size} records"
                            }
                            Text(
                                text = countText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            // Search Input + Sort Selector Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text(stringResource(R.string.history_search_hint)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear search",
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("history_search_input"),
                    )

                    Box {
                        OutlinedButton(
                            onClick = { showSortMenu = true },
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                            modifier = Modifier
                                .heightIn(min = 56.dp)
                                .testTag("history_sort_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = stringResource(R.string.history_sort_label),
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            TransactionSortOption.entries.forEach { option ->
                                val selected = uiState.selectedSortOption == option
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    leadingIcon = {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSortOptionSelect(option)
                                        showSortMenu = false
                                    },
                                    modifier = Modifier.testTag("sort_option_${option.name.lowercase()}"),
                                )
                            }
                        }
                    }
                }
            }

            // Date Range Filter Chips + Active Sort Pill
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DateFilterOption.entries.forEach { dateOption ->
                        FilterChip(
                            selected = uiState.selectedDateFilter == dateOption,
                            onClick = { onDateFilterSelect(dateOption) },
                            label = { Text(dateOption.label) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("date_filter_${dateOption.name.lowercase()}"),
                        )
                    }
                }
            }

            // Channel Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = uiState.selectedFilterChannel == null,
                        onClick = { onFilterChannelSelect(null) },
                        label = { Text("All Apps") },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("filter_all"),
                    )
                    PaymentChannel.entries.forEach { channel ->
                        FilterChip(
                            selected = uiState.selectedFilterChannel == channel,
                            onClick = {
                                onFilterChannelSelect(
                                    if (uiState.selectedFilterChannel == channel) null else channel
                                )
                            },
                            label = { Text(channel.displayName) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("filter_${channel.name.lowercase()}"),
                        )
                    }
                }
            }

            // Active Sort Indicator Strip
            if (uiState.filteredAlerts.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Sorted by ${uiState.selectedSortOption.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (hasActiveFilters) {
                            TextButton(
                                onClick = onResetFilters,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("inline_reset_filters_button"),
                            ) {
                                Text(stringResource(R.string.history_reset_filters))
                            }
                        }
                    }
                }
            }

            // Ledger List or Empty / No-Matches State
            if (uiState.filteredAlerts.isEmpty()) {
                item {
                    if (uiState.totalAlertsCount == 0) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_soundbox_empty),
                                    contentDescription = "No payment history yet",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Text(
                                    text = stringResource(R.string.history_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = stringResource(R.string.history_empty_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FilterListOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp),
                                )
                                Text(
                                    text = stringResource(R.string.history_no_matches_title),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = stringResource(R.string.history_no_matches_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                FilledTonalButton(
                                    onClick = onResetFilters,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("reset_filters_button"),
                                ) {
                                    Text(stringResource(R.string.history_reset_filters))
                                }
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = uiState.filteredAlerts,
                    key = { _, alert -> alert.id },
                ) { index, alert ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showDateHeaders) {
                            val currentLabel = formatDateGroupHeader(alert.timestamp)
                            val prevLabel = if (index > 0) {
                                formatDateGroupHeader(uiState.filteredAlerts[index - 1].timestamp)
                            } else {
                                null
                            }
                            if (currentLabel != prevLabel) {
                                Text(
                                    text = currentLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 4.dp),
                                )
                            }
                        }
                        PaymentAlertRow(
                            alert = alert,
                            onReplayClick = { onReplayAlert(alert) },
                            onDeleteClick = { onDeleteAlert(alert.id) },
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Payment Ledger?") },
            text = { Text("This will remove all stored payment alert records on this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearDialog = false
                    },
                    modifier = Modifier.testTag("confirm_clear_history_button"),
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun formatDateGroupHeader(timestamp: Long): String {
    val calNow = Calendar.getInstance()
    val calTarget = Calendar.getInstance().apply { timeInMillis = timestamp }

    val sameYear = calNow.get(Calendar.YEAR) == calTarget.get(Calendar.YEAR)
    val dayDiff = calNow.get(Calendar.DAY_OF_YEAR) - calTarget.get(Calendar.DAY_OF_YEAR)

    return when {
        sameYear && dayDiff == 0 -> "Today"
        sameYear && dayDiff == 1 -> "Yesterday"
        else -> SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
