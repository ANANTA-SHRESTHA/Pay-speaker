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
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FilterListOff
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.PaymentAlertEntity
import com.example.data.preferences.PaymentChannel
import com.example.domain.DateFilterOption
import com.example.domain.PaymentNotificationParser
import com.example.domain.StatementExportResult
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
    onDeleteAlert: (PaymentAlertEntity) -> Unit,
    onClearHistory: () -> Unit,
    onToggleBalanceVisibility: () -> Unit = {},
    onDownloadStatementPdf: () -> Unit = {},
    onDownloadStatementCsv: () -> Unit = {},
    onOpenExportedStatement: (StatementExportResult) -> Unit = {},
    onShareExportedStatement: (StatementExportResult) -> Unit = {},
    onDismissExportBanner: () -> Unit = {},
    onDismissCloudError: () -> Unit = {},
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

            uiState.cloudSyncError?.let { errorMsg ->
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onDismissCloudError) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }

            // Bank-Style Account Total Balance & Statement Download Card
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccountBalance,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource(R.string.bank_total_balance),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    val countText = if (hasActiveFilters && uiState.totalAlertsCount > 0) {
                                        "${uiState.filteredAlerts.size} of ${uiState.totalAlertsCount}"
                                    } else {
                                        "${uiState.totalAlertsCount} credits"
                                    }
                                    Text(
                                        text = countText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                                IconButton(
                                    onClick = onToggleBalanceVisibility,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("history_toggle_balance_button"),
                                ) {
                                    Icon(
                                        imageVector = if (uiState.showBalance) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                        contentDescription = stringResource(
                                            if (uiState.showBalance) R.string.bank_hide_balance_cd else R.string.bank_show_balance_cd
                                        ),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (uiState.showBalance) {
                                "NPR ${PaymentNotificationParser.formatAmount(uiState.totalBalance)}"
                            } else {
                                "NPR ••••••"
                            },
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (hasActiveFilters) stringResource(R.string.history_all_time_total) else stringResource(R.string.bank_today_inflow),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = if (uiState.showBalance) {
                                        "Rs. ${PaymentNotificationParser.formatAmount(if (hasActiveFilters) uiState.filteredTotal else uiState.todayTotal)}"
                                    } else {
                                        "Rs. ••••"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.bank_month_inflow),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = if (uiState.showBalance) {
                                        "Rs. ${PaymentNotificationParser.formatAmount(uiState.thisMonthTotal)}"
                                    } else {
                                        "Rs. ••••"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = onDownloadStatementPdf,
                                enabled = !uiState.isExportingStatement && uiState.filteredAlerts.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("download_pdf_statement_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .size(18.dp),
                                )
                                Text(
                                    text = if (uiState.isExportingStatement) "Exporting…" else stringResource(R.string.statement_download_pdf),
                                    maxLines = 1,
                                )
                            }

                            OutlinedButton(
                                onClick = onDownloadStatementCsv,
                                enabled = !uiState.isExportingStatement && uiState.filteredAlerts.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("download_csv_statement_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.TableChart,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .size(18.dp),
                                )
                                Text(stringResource(R.string.statement_download_csv), maxLines = 1)
                            }
                        }
                    }
                }
            }

            uiState.statementExportError?.let { exportErr ->
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = exportErr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onDismissExportBanner) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }

            uiState.lastExportResult?.let { exported ->
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = exported.savedLocationLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilledTonalButton(
                                    onClick = { onOpenExportedStatement(exported) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("open_exported_statement_button"),
                                ) {
                                    Text(stringResource(R.string.statement_open))
                                }
                                OutlinedButton(
                                    onClick = { onShareExportedStatement(exported) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("share_exported_statement_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .size(16.dp),
                                    )
                                    Text(stringResource(R.string.statement_share))
                                }
                                TextButton(
                                    onClick = onDismissExportBanner,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text("Dismiss")
                                }
                            }
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
                    key = { _, alert -> alert.cloudId.ifBlank { alert.id.toString() } },
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
                            onDeleteClick = { onDeleteAlert(alert) },
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
