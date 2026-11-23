package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.PaymentAlertEntity
import com.example.ui.theme.LocalPaySpeakerColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SoundboxHardwareCard(
    isSpeakerEnabled: Boolean,
    isSpeaking: Boolean,
    currentUtterance: String?,
    merchantName: String,
    lastAlert: PaymentAlertEntity?,
    listenerGranted: Boolean,
    onToggleSpeaker: (Boolean) -> Unit,
    onStopSpeaking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalPaySpeakerColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "soundbox_pulse")

    val waveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isSpeaking) 1100 else 2800,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave_progress",
    )

    val ledAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "led_alpha",
    )

    val cardBrush = Brush.linearGradient(
        colors = listOf(ext.hardwareStart, ext.hardwareEnd),
    )

    val statusText = when {
        isSpeaking -> stringResource(R.string.soundbox_speaking_badge)
        !isSpeakerEnabled -> stringResource(R.string.soundbox_paused_badge)
        listenerGranted -> stringResource(R.string.soundbox_active_badge)
        else -> stringResource(R.string.soundbox_standby_badge)
    }

    val statusColor = when {
        isSpeaking -> MaterialTheme.colorScheme.tertiary
        !isSpeakerEnabled -> ext.hardwareOnSurface.copy(alpha = 0.45f)
        listenerGranted -> ext.hardwareAccent
        else -> MaterialTheme.colorScheme.tertiary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(cardBrush)
            .border(
                width = 1.dp,
                color = ext.hardwareAccent.copy(alpha = if (isSpeaking) 0.55f else 0.22f),
                shape = RoundedCornerShape(28.dp),
            )
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top Bezel Row: Status LED Pill + Power Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = ext.hardwareOnSurface.copy(alpha = 0.1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(
                                    statusColor.copy(
                                        alpha = if (isSpeakerEnabled) ledAlpha else 0.45f
                                    )
                                ),
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = ext.hardwareOnSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (isSpeakerEnabled) "ON" else "OFF",
                        style = MaterialTheme.typography.labelMedium,
                        color = ext.hardwareOnSurface.copy(alpha = 0.8f),
                    )
                    Switch(
                        checked = isSpeakerEnabled,
                        onCheckedChange = onToggleSpeaker,
                        modifier = Modifier.testTag("master_speaker_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ext.hardwareEnd,
                            checkedTrackColor = ext.hardwareAccent,
                            uncheckedThumbColor = ext.hardwareOnSurface.copy(alpha = 0.7f),
                            uncheckedTrackColor = ext.hardwareOnSurface.copy(alpha = 0.18f),
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }
            }

            // Center Acoustic Grille & Animated Soundwaves
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Box(
                    modifier = Modifier.size(116.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxRadius = size.minDimension / 2f

                        // Expanding animated soundwave rings when active
                        if (isSpeakerEnabled) {
                            val ringCount = if (isSpeaking) 3 else 1
                            for (ring in 0 until ringCount) {
                                val phase = (waveProgress + ring / ringCount.toFloat()) % 1f
                                val r = maxRadius * (0.56f + 0.44f * phase)
                                val alpha = (1f - phase) * if (isSpeaking) 0.65f else 0.18f
                                drawCircle(
                                    color = statusColor.copy(alpha = alpha),
                                    radius = r,
                                    center = center,
                                    style = Stroke(width = if (isSpeaking) 3.dp.toPx() else 1.5.dp.toPx()),
                                )
                            }
                        }

                        // Speaker hardware bezel circle
                        val coreRadius = maxRadius * 0.56f
                        drawCircle(
                            color = ext.hardwareOnSurface.copy(alpha = 0.08f),
                            radius = coreRadius,
                            center = center,
                        )
                        drawCircle(
                            color = ext.hardwareAccent.copy(alpha = if (isSpeakerEnabled) 0.45f else 0.15f),
                            radius = coreRadius,
                            center = center,
                            style = Stroke(width = 1.5.dp.toPx()),
                        )

                        // Perforated speaker mesh dots
                        val dotColor = ext.hardwareOnSurface.copy(
                            alpha = if (isSpeakerEnabled) 0.28f else 0.12f
                        )
                        val rings = listOf(0.24f to 6, 0.42f to 12)
                        rings.forEach { (ratio, count) ->
                            val rr = maxRadius * ratio
                            for (i in 0 until count) {
                                val angle = (2.0 * Math.PI * i) / count
                                val dx = (center.x + rr * cos(angle)).toFloat()
                                val dy = (center.y + rr * sin(angle)).toFloat()
                                drawCircle(
                                    color = dotColor,
                                    radius = 1.8.dp.toPx(),
                                    center = Offset(dx, dy),
                                )
                            }
                        }
                    }

                    Icon(
                        imageVector = when {
                            !isSpeakerEnabled -> Icons.AutoMirrored.Rounded.VolumeOff
                            isSpeaking -> Icons.Rounded.GraphicEq
                            else -> Icons.AutoMirrored.Rounded.VolumeUp
                        },
                        contentDescription = statusText,
                        tint = if (isSpeakerEnabled) statusColor else ext.hardwareOnSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(30.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.soundbox_last_payment).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.hardwareOnSurface.copy(alpha = 0.65f),
                    )
                    AnimatedContent(
                        targetState = lastAlert,
                        label = "last_amount_anim",
                    ) { alert ->
                        if (alert != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "${currencySymbol(alert.currency)} ${alert.formattedAmount}",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = ext.hardwareOnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = buildString {
                                        append(alert.sourceLabel)
                                        if (!alert.senderName.isNullOrBlank()) {
                                            append(" • ${alert.senderName}")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ext.hardwareAccent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.soundbox_no_payments_yet),
                                style = MaterialTheme.typography.titleLarge,
                                color = ext.hardwareOnSurface.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }

            // Live Speech Ticker Bar
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ext.hardwareOnSurface.copy(alpha = 0.09f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = if (isSpeaking) statusColor else ext.hardwareAccent.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = currentUtterance
                            ?: if (isSpeakerEnabled) {
                                val shopPrefix = merchantName.trim().takeIf { it.isNotEmpty() }
                                    ?.let { "$it • " } ?: ""
                                "${shopPrefix}Ready • Voice announces incoming payments"
                            } else {
                                "Speaker muted • Alerts still save to your ledger"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.hardwareOnSurface.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSpeaking) {
                        TextButton(
                            onClick = onStopSpeaking,
                            modifier = Modifier.testTag("stop_speaking_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = stringResource(R.string.soundbox_stop_speaking),
                                tint = ext.hardwareAccent,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.soundbox_stop_speaking),
                                style = MaterialTheme.typography.labelMedium,
                                color = ext.hardwareAccent,
                            )
                        }
                    }
                }
            }
        }
    }
}

fun currencySymbol(currency: String): String = when (currency.uppercase()) {
    "INR" -> "₹"
    "USD" -> "$"
    else -> "Rs."
}
