package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.PaymentNotificationParser
import com.example.ui.theme.LocalPaySpeakerColors

@Composable
fun BankBalanceCard(
    merchantName: String,
    userDisplayName: String?,
    totalBalance: Double,
    totalCount: Int,
    todayTotal: Double,
    todayCount: Int,
    thisMonthTotal: Double,
    thisMonthCount: Int,
    showBalance: Boolean,
    isExporting: Boolean,
    onToggleBalanceVisibility: () -> Unit,
    onDownloadStatementPdf: () -> Unit,
    onViewStatement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalPaySpeakerColors.current
    val cardBrush = Brush.linearGradient(
        colors = listOf(ext.hardwareStart, ext.hardwareEnd),
    )
    val accountTitle = merchantName.trim().takeIf { it.isNotEmpty() }
        ?: userDisplayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: stringResource(R.string.bank_merchant_account)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cardBrush)
            .border(
                width = 1.dp,
                color = ext.hardwareAccent.copy(alpha = 0.25f),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(18.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Bank Account Header + Eye Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ext.hardwareAccent.copy(alpha = 0.16f),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccountBalance,
                            contentDescription = null,
                            tint = ext.hardwareAccent,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(20.dp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = accountTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = ext.hardwareOnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${stringResource(R.string.bank_total_balance)} • $totalCount credits",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.hardwareOnSurface.copy(alpha = 0.7f),
                        )
                    }
                }

                IconButton(
                    onClick = onToggleBalanceVisibility,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_balance_visibility_button"),
                ) {
                    Icon(
                        imageVector = if (showBalance) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = stringResource(
                            if (showBalance) R.string.bank_hide_balance_cd else R.string.bank_show_balance_cd
                        ),
                        tint = ext.hardwareOnSurface.copy(alpha = 0.88f),
                    )
                }
            }

            // Prominent Bank Total Balance Figure
            Text(
                text = if (showBalance) {
                    "NPR ${PaymentNotificationParser.formatAmount(totalBalance)}"
                } else {
                    "NPR ••••••"
                },
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = ext.hardwareOnSurface,
                modifier = Modifier.testTag("bank_total_balance_text"),
            )

            // Today & This Month Breakdown Strip
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ext.hardwareOnSurface.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.bank_today_inflow),
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.hardwareOnSurface.copy(alpha = 0.68f),
                        )
                        Text(
                            text = if (showBalance) {
                                "Rs. ${PaymentNotificationParser.formatAmount(todayTotal)}"
                            } else {
                                "Rs. ••••"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = ext.hardwareAccent,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.soundbox_tx_count, todayCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.hardwareOnSurface.copy(alpha = 0.65f),
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.bank_month_inflow),
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.hardwareOnSurface.copy(alpha = 0.68f),
                        )
                        Text(
                            text = if (showBalance) {
                                "Rs. ${PaymentNotificationParser.formatAmount(thisMonthTotal)}"
                            } else {
                                "Rs. ••••"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = ext.hardwareOnSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.soundbox_tx_count, thisMonthCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.hardwareOnSurface.copy(alpha = 0.65f),
                        )
                    }
                }
            }

            // Quick Bank Statement Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = onDownloadStatementPdf,
                    enabled = !isExporting && totalCount > 0,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ext.hardwareAccent,
                        contentColor = ext.hardwareEnd,
                        disabledContainerColor = ext.hardwareOnSurface.copy(alpha = 0.14f),
                        disabledContentColor = ext.hardwareOnSurface.copy(alpha = 0.45f),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("soundbox_download_statement_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(18.dp),
                    )
                    Text(
                        text = if (isExporting) "Preparing…" else stringResource(R.string.statement_download_pdf),
                        maxLines = 1,
                    )
                }

                FilledTonalButton(
                    onClick = onViewStatement,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ext.hardwareOnSurface.copy(alpha = 0.14f),
                        contentColor = ext.hardwareOnSurface,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("soundbox_view_statement_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(18.dp),
                    )
                    Text("Statement", maxLines = 1)
                }
            }
        }
    }
}
