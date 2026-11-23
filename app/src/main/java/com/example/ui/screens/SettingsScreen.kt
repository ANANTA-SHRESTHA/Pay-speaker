package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.PaymentChannel
import com.example.data.preferences.VoiceLanguage
import com.example.data.preferences.VoiceSourceMode
import com.example.ui.theme.channelAccentColor
import com.example.ui.viewmodel.PaySpeakerUiState
import java.util.Locale

@Composable
fun SettingsScreen(
    uiState: PaySpeakerUiState,
    onMerchantNameChange: (String) -> Unit,
    onLanguageChange: (VoiceLanguage) -> Unit,
    onSelectedVoiceChange: (String?) -> Unit,
    onVoiceSourceModeChange: (VoiceSourceMode) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onSpeechPitchChange: (Float) -> Unit,
    onApplyVoicePreset: (Float, Float) -> Unit,
    onPlayChimeChange: (Boolean) -> Unit,
    onBoostVolumeChange: (Boolean) -> Unit,
    onAnnounceSenderChange: (Boolean) -> Unit,
    onAnnounceSourceChange: (Boolean) -> Unit,
    onRepeatTwiceChange: (Boolean) -> Unit,
    onForegroundGuardChange: (Boolean) -> Unit,
    onToggleChannel: (PaymentChannel) -> Unit,
    onTestVoice: () -> Unit,
    onInstallTtsData: () -> Unit,
    onStartRecordingOwnVoice: () -> Unit,
    onStopRecordingOwnVoice: () -> Unit,
    onImportCustomAudio: () -> Unit,
    onPreviewOwnVoice: () -> Unit,
    onDeleteOwnVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = uiState.settings
    val selectedLangAvailable = settings.language in uiState.availableLanguages
    var showVoicePicker by remember { mutableStateOf(false) }

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
            item {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            // Voice & Text-to-Speech Engine Section
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = stringResource(R.string.settings_voice_section),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (uiState.ttsReady) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ) {
                                Text(
                                    text = if (uiState.ttsReady) "TTS Ready" else "Loading",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.ttsReady) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }

                        OutlinedTextField(
                            value = settings.merchantName,
                            onValueChange = onMerchantNameChange,
                            label = { Text(stringResource(R.string.settings_merchant_name_label)) },
                            placeholder = { Text(stringResource(R.string.settings_merchant_name_hint)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Storefront,
                                    contentDescription = null,
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("merchant_name_input"),
                        )

                        Text(
                            text = stringResource(R.string.settings_language_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            VoiceLanguage.entries.forEach { lang ->
                                FilterChip(
                                    selected = settings.language == lang,
                                    onClick = { onLanguageChange(lang) },
                                    label = { Text(lang.label.substringBefore(" ")) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("lang_chip_${lang.name.lowercase()}"),
                                )
                            }
                        }

                        if (!selectedLangAvailable) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "${settings.language.label} TTS pack not detected on device (uses English fallback until installed).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        onClick = onInstallTtsData,
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Download,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .padding(end = 4.dp)
                                                .size(16.dp),
                                        )
                                        Text("Install")
                                    }
                                }
                            }
                        }

                        // Custom TTS Voice Picker
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.settings_tts_voice_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val activeVoiceLabel = uiState.availableVoices
                                .firstOrNull { it.name == settings.selectedVoiceName }
                                ?.displayLabel
                                ?: stringResource(R.string.settings_tts_voice_default)

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { showVoicePicker = true },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("tts_voice_picker_button"),
                                ) {
                                    Text(
                                        text = activeVoiceLabel,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = null,
                                    )
                                }

                                DropdownMenu(
                                    expanded = showVoicePicker,
                                    onDismissRequest = { showVoicePicker = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.settings_tts_voice_default)) },
                                        leadingIcon = {
                                            if (settings.selectedVoiceName == null) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        },
                                        onClick = {
                                            onSelectedVoiceChange(null)
                                            showVoicePicker = false
                                        },
                                    )
                                    uiState.availableVoices.forEach { voice ->
                                        DropdownMenuItem(
                                            text = { Text(voice.displayLabel) },
                                            leadingIcon = {
                                                if (settings.selectedVoiceName == voice.name) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            },
                                            onClick = {
                                                onSelectedVoiceChange(voice.name)
                                                showVoicePicker = false
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        // Voice Clarity Presets
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AssistChip(
                                onClick = { onApplyVoicePreset(1.0f, 1.0f) },
                                label = { Text("Clear Standard") },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("preset_clear_standard"),
                            )
                            AssistChip(
                                onClick = { onApplyVoicePreset(1.2f, 1.05f) },
                                label = { Text("Fast Counter") },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("preset_fast_counter"),
                            )
                            AssistChip(
                                onClick = { onApplyVoicePreset(0.95f, 0.85f) },
                                label = { Text("Deep Formal") },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("preset_deep_formal"),
                            )
                        }

                        // Speech Speed & Pitch Sliders
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_speech_rate),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = String.format(Locale.US, "%.2fx", settings.speechRate),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Slider(
                                value = settings.speechRate,
                                onValueChange = onSpeechRateChange,
                                valueRange = 0.7f..1.4f,
                                steps = 6,
                                modifier = Modifier.testTag("speech_rate_slider"),
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_speech_pitch),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = String.format(Locale.US, "%.2fx", settings.speechPitch),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Slider(
                                value = settings.speechPitch,
                                onValueChange = onSpeechPitchChange,
                                valueRange = 0.75f..1.35f,
                                steps = 5,
                                modifier = Modifier.testTag("speech_pitch_slider"),
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        SettingToggleRow(
                            title = stringResource(R.string.settings_chime_title),
                            description = stringResource(R.string.settings_chime_desc),
                            checked = settings.playChime,
                            onCheckedChange = onPlayChimeChange,
                            tag = "toggle_play_chime",
                        )
                        SettingToggleRow(
                            title = stringResource(R.string.settings_boost_title),
                            description = stringResource(R.string.settings_boost_desc),
                            checked = settings.boostVolume,
                            onCheckedChange = onBoostVolumeChange,
                            tag = "toggle_boost_volume",
                        )
                        SettingToggleRow(
                            title = stringResource(R.string.settings_sender_title),
                            description = stringResource(R.string.settings_sender_desc),
                            checked = settings.announceSender,
                            onCheckedChange = onAnnounceSenderChange,
                            tag = "toggle_announce_sender",
                        )
                        SettingToggleRow(
                            title = stringResource(R.string.settings_source_title),
                            description = stringResource(R.string.settings_source_desc),
                            checked = settings.announceSource,
                            onCheckedChange = onAnnounceSourceChange,
                            tag = "toggle_announce_source",
                        )
                        SettingToggleRow(
                            title = stringResource(R.string.settings_repeat_title),
                            description = stringResource(R.string.settings_repeat_desc),
                            checked = settings.repeatTwice,
                            onCheckedChange = onRepeatTwiceChange,
                            tag = "toggle_repeat_twice",
                        )

                        Button(
                            onClick = onTestVoice,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("test_voice_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(stringResource(R.string.settings_test_voice))
                        }
                    }
                }
            }

            // Own Voice & Custom Audio Studio Card
            item {
                val hasClip = !settings.ownVoiceFilePath.isNullOrBlank()
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RecordVoiceOver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.settings_own_voice_section),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            text = stringResource(R.string.settings_own_voice_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            VoiceSourceMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.voiceSourceMode == mode,
                                    onClick = { onVoiceSourceModeChange(mode) },
                                    label = { Text(mode.label) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("voice_mode_${mode.name.lowercase()}"),
                                )
                            }
                        }
                        Text(
                            text = settings.voiceSourceMode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (uiState.isRecordingOwnVoice) {
                                Button(
                                    onClick = onStopRecordingOwnVoice,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("stop_recording_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Stop,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 6.dp),
                                    )
                                    Text("${stringResource(R.string.settings_record_stop)} (${uiState.recordingSeconds}s)")
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = onStartRecordingOwnVoice,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("start_recording_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Mic,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 6.dp),
                                    )
                                    Text(stringResource(R.string.settings_record_start))
                                }
                            }

                            OutlinedButton(
                                onClick = onImportCustomAudio,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("import_audio_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AudioFile,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                                Text(stringResource(R.string.settings_import_audio))
                            }
                        }

                        uiState.voiceRecorderError?.let { errorMsg ->
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        if (hasClip) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Own Voice Clip Ready",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        onClick = onPreviewOwnVoice,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("preview_own_voice_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 4.dp),
                                        )
                                        Text(stringResource(R.string.settings_preview_own_voice))
                                    }
                                    IconButton(
                                        onClick = onDeleteOwnVoice,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("delete_own_voice_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = stringResource(R.string.settings_delete_own_voice),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Background Soundbox Guard
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.settings_service_section),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        SettingToggleRow(
                            title = stringResource(R.string.settings_foreground_title),
                            description = stringResource(R.string.settings_foreground_desc),
                            checked = settings.foregroundGuardEnabled,
                            onCheckedChange = onForegroundGuardChange,
                            tag = "toggle_foreground_guard",
                        )
                    }
                }
            }

            // Monitored Payment Channels
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_channels_section),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_channels_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        PaymentChannel.entries.forEachIndexed { index, channel ->
                            val enabled = channel in settings.enabledChannels
                            val accent = channelAccentColor(channel)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(accent),
                                    )
                                    Column {
                                        Text(
                                            text = channel.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            text = channel.keywordHints.take(3).joinToString(", "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = { onToggleChannel(channel) },
                                    modifier = Modifier.testTag("channel_switch_${channel.name.lowercase()}"),
                                )
                            }
                            if (index < PaymentChannel.entries.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
        )
    }
}
