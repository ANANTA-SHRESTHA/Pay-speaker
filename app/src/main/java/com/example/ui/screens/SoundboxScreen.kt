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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.PaymentAlertEntity
import com.example.data.preferences.AnnouncementReadMode
import com.example.data.preferences.PaymentChannel
import com.example.domain.PaymentNotificationParser
import com.example.domain.SpeechFormatter
import com.example.domain.StatementExportResult
import com.example.ui.components.BankBalanceCard
import com.example.ui.components.ListenerPermissionCard
import com.example.ui.components.PaymentAlertRow
import com.example.ui.components.PostNotificationPermissionCard
import com.example.ui.components.SimulatePaymentSheet
import com.example.ui.components.SmsPermissionCard
import com.example.ui.components.SoundboxHardwareCard
import com.example.ui.viewmodel.PaySpeakerUiState
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.FilterChip

@Composable
fun SoundboxScreen(
    uiState: PaySpeakerUiState,
    onToggleSpeaker: (Boolean) -> Unit,
    onStopSpeaking: () -> Unit,
    onGrantListenerAccess: () -> Unit,
    onRequestPostNotifications: () -> Unit,
    onRequestSmsPermission: () -> Unit = {},
    onSimulatePayment: (Double, String, String?, PaymentChannel) -> Unit,
    onReplayAlert: (PaymentAlertEntity) -> Unit,
    onViewAllHistory: () -> Unit,
    onSelectReadMode: (AnnouncementReadMode) -> Unit = {},
    onToggleBalanceVisibility: () -> Unit = {},
    onDownloadStatementPdf: () -> Unit = {},
    onOpenExportedStatement: (StatementExportResult) -> Unit = {},
    onShareExportedStatement: (StatementExportResult) -> Unit = {},
    onDismissExportBanner: () -> Unit = {},
    onDismissCloudError: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showSimulateSheet by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top Brand Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.soundbox_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource(R.string.soundbox_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    val listeningActive = uiState.permissions.notificationListenerGranted ||
                        uiState.permissions.smsPermissionGranted
                    if (listeningActive) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "Access Ready",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            // Hero Soundbox Hardware Unit
            item {
                SoundboxHardwareCard(
                    isSpeakerEnabled = uiState.settings.isSpeakerEnabled,
                    isSpeaking = uiState.isSpeaking,
                    currentUtterance = uiState.currentUtterance,
                    merchantName = uiState.settings.merchantName,
                    lastAlert = uiState.recentAlerts.firstOrNull(),
                    listenerGranted = uiState.permissions.notificationListenerGranted ||
                        uiState.permissions.smsPermissionGranted,
                    onToggleSpeaker = onToggleSpeaker,
                    onStopSpeaking = onStopSpeaking,
                )
            }

            // Permissions Setup Cards (when not yet granted)
            if (!uiState.permissions.smsPermissionGranted) {
                item {
                    SmsPermissionCard(onAllowClick = onRequestSmsPermission)
                }
            }

            if (!uiState.permissions.notificationListenerGranted) {
                item {
                    ListenerPermissionCard(onGrantClick = onGrantListenerAccess)
                }
            }

            if (!uiState.permissions.postNotificationsGranted) {
                item {
                    PostNotificationPermissionCard(onAllowClick = onRequestPostNotifications)
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

            // Bank-Style Total Balance & Statement Card
            item {
                BankBalanceCard(
                    merchantName = uiState.settings.merchantName,
                    userDisplayName = uiState.userDisplayName,
                    totalBalance = uiState.totalBalance,
                    totalCount = uiState.totalAlertsCount,
                    todayTotal = uiState.todayTotal,
                    todayCount = uiState.todayCount,
                    thisMonthTotal = uiState.thisMonthTotal,
                    thisMonthCount = uiState.thisMonthCount,
                    showBalance = uiState.showBalance,
                    isExporting = uiState.isExportingStatement,
                    onToggleBalanceVisibility = onToggleBalanceVisibility,
                    onDownloadStatementPdf = onDownloadStatementPdf,
                    onViewStatement = onViewAllHistory,
                )
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
                                TextButton(
                                    onClick = { onOpenExportedStatement(exported) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text(stringResource(R.string.statement_open))
                                }
                                TextButton(
                                    onClick = { onShareExportedStatement(exported) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
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

            // What to Read Aloud Quick Mode Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.settings_read_mode_label),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AnnouncementReadMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = uiState.settings.readMode == mode,
                                    onClick = { onSelectReadMode(mode) },
                                    label = { Text(mode.label) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("soundbox_read_mode_${mode.name.lowercase()}"),
                                )
                            }
                        }

                        Text(
                            text = "Reads: \"${SpeechFormatter.sampleAnnouncement(uiState.settings)}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Quick Test / Simulate Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bolt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.soundbox_quick_test_title),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.soundbox_quick_test_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FilledTonalButton(
                                onClick = { showSimulateSheet = true },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("open_custom_simulate_button"),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .size(18.dp),
                                )
                                Text(stringResource(R.string.soundbox_custom_simulate))
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            QuickPresetChip(
                                label = "Fonepay • Rs. 1 (9864843502)",
                                tag = "preset_fonepay_1_number",
                                onClick = {
                                    onSimulatePayment(1.0, "NPR", "9864843502", PaymentChannel.FONEPAY)
                                },
                            )
                            QuickPresetChip(
                                label = "eSewa • Rs. 3,500",
                                tag = "preset_esewa_3500",
                                onClick = {
                                    onSimulatePayment(3500.0, "NPR", "Ananta Shrest", PaymentChannel.ESEWA)
                                },
                            )
                            QuickPresetChip(
                                label = "Bank • Rs. 4,000",
                                tag = "preset_bank_4000",
                                onClick = {
                                    onSimulatePayment(4000.0, "NPR", null, PaymentChannel.BANK_ALERT)
                                },
                            )
                        }
                    }
                }
            }

            // Recent Alerts Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.soundbox_recent_alerts),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (uiState.recentAlerts.isNotEmpty()) {
                        TextButton(
                            onClick = onViewAllHistory,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("view_all_history_button"),
                        ) {
                            Text(stringResource(R.string.soundbox_view_all))
                        }
                    }
                }
            }

            if (uiState.recentAlerts.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_soundbox_empty),
                                contentDescription = "Merchant Soundbox Illustration",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(156.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Text(
                                text = "Tap a quick test chip above to hear your Soundbox announce a payment right away.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(
                    items = uiState.recentAlerts,
                    key = { it.cloudId.ifBlank { it.id.toString() } },
                ) { alert ->
                    PaymentAlertRow(
                        alert = alert,
                        onReplayClick = { onReplayAlert(alert) },
                    )
                }
            }
        }
    }

    if (showSimulateSheet) {
        SimulatePaymentSheet(
            onDismiss = { showSimulateSheet = false },
            onSimulate = onSimulatePayment,
        )
    }
}

@Composable
private fun QuickPresetChip(
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            leadingIconContentColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(tag),
    )
}
