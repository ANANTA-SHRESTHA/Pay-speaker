package com.example.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.PaymentAlertEntity
import com.example.data.preferences.PaymentChannel
import com.example.data.preferences.SoundboxSettings
import com.example.data.preferences.VoiceLanguage
import com.example.data.preferences.VoiceSourceMode
import com.example.domain.DateFilterOption
import com.example.domain.PaymentNotificationParser
import com.example.domain.SpeechFormatter
import com.example.domain.TransactionHistoryFilter
import com.example.domain.TransactionSortOption
import com.example.service.SoundboxForegroundService
import com.example.service.TtsVoiceOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionStatus(
    val notificationListenerGranted: Boolean = false,
    val postNotificationsGranted: Boolean = true,
    val recordAudioGranted: Boolean = false,
)

data class PaySpeakerUiState(
    val settings: SoundboxSettings = SoundboxSettings(),
    val recentAlerts: List<PaymentAlertEntity> = emptyList(),
    val filteredAlerts: List<PaymentAlertEntity> = emptyList(),
    val totalAlertsCount: Int = 0,
    val todayTotal: Double = 0.0,
    val todayCount: Int = 0,
    val filteredTotal: Double = 0.0,
    val searchQuery: String = "",
    val selectedFilterChannel: PaymentChannel? = null,
    val selectedDateFilter: DateFilterOption = DateFilterOption.ALL_TIME,
    val selectedSortOption: TransactionSortOption = TransactionSortOption.NEWEST_FIRST,
    val isSpeaking: Boolean = false,
    val currentUtterance: String? = null,
    val ttsReady: Boolean = false,
    val ttsEngineLabel: String = "Initializing...",
    val availableLanguages: Set<VoiceLanguage> = setOf(VoiceLanguage.ENGLISH),
    val availableVoices: List<TtsVoiceOption> = emptyList(),
    val isRecordingOwnVoice: Boolean = false,
    val recordingSeconds: Int = 0,
    val voiceRecorderError: String? = null,
    val foregroundGuardRunning: Boolean = false,
    val permissions: PermissionStatus = PermissionStatus(),
)

private data class RuntimeSnapshot(
    val isSpeaking: Boolean,
    val currentUtterance: String?,
    val ttsReady: Boolean,
    val ttsEngineLabel: String,
    val availableLanguages: Set<VoiceLanguage>,
    val availableVoices: List<TtsVoiceOption>,
    val isRecordingOwnVoice: Boolean,
    val recordingSeconds: Int,
    val voiceRecorderError: String?,
    val foregroundRunning: Boolean,
    val permissions: PermissionStatus,
)

private data class HistorySnapshot(
    val allAlerts: List<PaymentAlertEntity>,
    val filteredAlerts: List<PaymentAlertEntity>,
    val filteredTotal: Double,
    val query: String,
    val channelFilter: PaymentChannel?,
    val dateFilter: DateFilterOption,
    val sortOption: TransactionSortOption,
)

class PaySpeakerViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {

    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow<PaymentChannel?>(null)
    private val selectedDateFilter = MutableStateFlow(DateFilterOption.ALL_TIME)
    private val selectedSortOption = MutableStateFlow(TransactionSortOption.NEWEST_FIRST)
    private val permissionStatus = MutableStateFlow(readPermissionStatus())

    private val voiceRecorderError = MutableStateFlow<String?>(null)

    private val runtimeFlow = combine(
        container.stateHolder.isSpeaking,
        container.stateHolder.currentUtterance,
        container.stateHolder.ttsReady,
        container.stateHolder.availableLanguages,
        container.stateHolder.foregroundGuardRunning,
    ) { speaking, utterance, ttsReady, languages, fgRunning ->
        RuntimeSnapshot(
            isSpeaking = speaking,
            currentUtterance = utterance,
            ttsReady = ttsReady,
            ttsEngineLabel = container.stateHolder.ttsEngineLabel.value,
            availableLanguages = languages,
            availableVoices = container.stateHolder.availableVoices.value,
            isRecordingOwnVoice = container.ownVoiceRecorder.isRecording.value,
            recordingSeconds = container.ownVoiceRecorder.recordingSeconds.value,
            voiceRecorderError = voiceRecorderError.value,
            foregroundRunning = fgRunning,
            permissions = permissionStatus.value,
        )
    }.combine(container.stateHolder.availableVoices) { snapshot, voices ->
        snapshot.copy(
            availableVoices = voices,
            ttsEngineLabel = container.stateHolder.ttsEngineLabel.value,
        )
    }.combine(container.ownVoiceRecorder.isRecording) { snapshot, recording ->
        snapshot.copy(isRecordingOwnVoice = recording)
    }.combine(container.ownVoiceRecorder.recordingSeconds) { snapshot, seconds ->
        snapshot.copy(recordingSeconds = seconds)
    }.combine(voiceRecorderError) { snapshot, err ->
        snapshot.copy(voiceRecorderError = err)
    }.combine(permissionStatus) { snapshot, perms ->
        snapshot.copy(permissions = perms)
    }

    private val historyFlow = combine(
        container.paymentRepository.allAlerts,
        searchQuery,
        selectedFilter,
        selectedDateFilter,
        selectedSortOption,
    ) { allAlerts, query, channelFilter, dateFilter, sortOption ->
        val filtered = TransactionHistoryFilter.apply(
            alerts = allAlerts,
            query = query,
            channelFilter = channelFilter,
            dateFilter = dateFilter,
            sortOption = sortOption,
        )
        HistorySnapshot(
            allAlerts = allAlerts,
            filteredAlerts = filtered,
            filteredTotal = filtered.sumOf { it.amount },
            query = query,
            channelFilter = channelFilter,
            dateFilter = dateFilter,
            sortOption = sortOption,
        )
    }

    val uiState: StateFlow<PaySpeakerUiState> = combine(
        container.preferencesRepository.settingsFlow,
        historyFlow,
        container.paymentRepository.observeTodayTotal(),
        container.paymentRepository.observeTodayCount(),
        runtimeFlow,
    ) { settings, history, todayTotal, todayCount, runtime ->
        PaySpeakerUiState(
            settings = settings,
            recentAlerts = history.allAlerts.take(5),
            filteredAlerts = history.filteredAlerts,
            totalAlertsCount = history.allAlerts.size,
            todayTotal = todayTotal,
            todayCount = todayCount,
            filteredTotal = history.filteredTotal,
            searchQuery = history.query,
            selectedFilterChannel = history.channelFilter,
            selectedDateFilter = history.dateFilter,
            selectedSortOption = history.sortOption,
            isSpeaking = runtime.isSpeaking,
            currentUtterance = runtime.currentUtterance,
            ttsReady = runtime.ttsReady,
            ttsEngineLabel = runtime.ttsEngineLabel,
            availableLanguages = runtime.availableLanguages,
            availableVoices = runtime.availableVoices,
            isRecordingOwnVoice = runtime.isRecordingOwnVoice,
            recordingSeconds = runtime.recordingSeconds,
            voiceRecorderError = runtime.voiceRecorderError,
            foregroundGuardRunning = runtime.foregroundRunning,
            permissions = runtime.permissions,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PaySpeakerUiState(),
    )

    fun refreshPermissions() {
        permissionStatus.value = readPermissionStatus()
        container.speakerEngine.refreshLanguages()
    }

    private fun readPermissionStatus(): PermissionStatus {
        val context = getApplication<Application>()
        val listenerEnabled = NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)

        val postGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val recordAudioGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        return PermissionStatus(
            notificationListenerGranted = listenerEnabled,
            postNotificationsGranted = postGranted,
            recordAudioGranted = recordAudioGranted,
        )
    }

    fun toggleSpeakerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setSpeakerEnabled(enabled)
            if (!enabled) {
                container.speakerEngine.stopSpeaking()
            }
        }
    }

    fun setMerchantName(name: String) {
        viewModelScope.launch {
            container.preferencesRepository.setMerchantName(name)
        }
    }

    fun setLanguage(language: VoiceLanguage) {
        viewModelScope.launch {
            container.preferencesRepository.setLanguage(language)
        }
    }

    fun setSelectedVoiceName(voiceName: String?) {
        viewModelScope.launch {
            container.preferencesRepository.setSelectedVoiceName(voiceName)
        }
    }

    fun setVoiceSourceMode(mode: VoiceSourceMode) {
        viewModelScope.launch {
            container.preferencesRepository.setVoiceSourceMode(mode)
        }
    }

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch {
            container.preferencesRepository.setSpeechRate(rate)
        }
    }

    fun setSpeechPitch(pitch: Float) {
        viewModelScope.launch {
            container.preferencesRepository.setSpeechPitch(pitch)
        }
    }

    fun applyVoicePreset(rate: Float, pitch: Float) {
        viewModelScope.launch {
            container.preferencesRepository.setSpeechRate(rate)
            container.preferencesRepository.setSpeechPitch(pitch)
        }
    }

    fun startRecordingOwnVoice() {
        voiceRecorderError.value = null
        container.speakerEngine.stopSpeaking()
        container.ownVoiceRecorder.startRecording(
            onAutoStopped = { savedPath ->
                if (savedPath != null) {
                    viewModelScope.launch {
                        container.preferencesRepository.setOwnVoiceFilePath(savedPath)
                        if (uiState.value.settings.voiceSourceMode == VoiceSourceMode.TTS_VOICE) {
                            container.preferencesRepository.setVoiceSourceMode(VoiceSourceMode.OWN_VOICE_PLUS_AMOUNT)
                        }
                    }
                }
            },
            onError = { err ->
                voiceRecorderError.value = err
            },
        )
    }

    fun stopRecordingOwnVoice() {
        val savedPath = container.ownVoiceRecorder.stopRecording()
        if (savedPath != null) {
            voiceRecorderError.value = null
            viewModelScope.launch {
                container.preferencesRepository.setOwnVoiceFilePath(savedPath)
                if (uiState.value.settings.voiceSourceMode == VoiceSourceMode.TTS_VOICE) {
                    container.preferencesRepository.setVoiceSourceMode(VoiceSourceMode.OWN_VOICE_PLUS_AMOUNT)
                }
            }
        } else {
            voiceRecorderError.value = "Recording too short. Hold for at least 1 second."
        }
    }

    fun importCustomVoiceUri(uri: Uri) {
        voiceRecorderError.value = null
        viewModelScope.launch {
            val savedPath = container.ownVoiceRecorder.importAudioFromUri(uri)
            if (savedPath != null) {
                container.preferencesRepository.setOwnVoiceFilePath(savedPath)
                if (uiState.value.settings.voiceSourceMode == VoiceSourceMode.TTS_VOICE) {
                    container.preferencesRepository.setVoiceSourceMode(VoiceSourceMode.OWN_VOICE_PLUS_AMOUNT)
                }
            } else {
                voiceRecorderError.value = "Could not import selected audio file."
            }
        }
    }

    fun previewOwnVoice() {
        val path = uiState.value.settings.ownVoiceFilePath ?: return
        container.speakerEngine.previewAudioClip(path, uiState.value.settings.boostVolume)
    }

    fun deleteOwnVoice() {
        val path = uiState.value.settings.ownVoiceFilePath
        container.ownVoiceRecorder.deleteClip(path)
        viewModelScope.launch {
            container.preferencesRepository.setOwnVoiceFilePath(null)
            container.preferencesRepository.setVoiceSourceMode(VoiceSourceMode.TTS_VOICE)
        }
    }

    fun setVoiceRecorderError(message: String?) {
        voiceRecorderError.value = message
    }

    fun stopSpeaking() {
        container.speakerEngine.stopSpeaking()
    }

    fun setBoostVolume(boost: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setBoostVolume(boost)
        }
    }

    fun setPlayChime(play: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setPlayChime(play)
        }
    }

    fun setAnnounceSender(announce: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setAnnounceSender(announce)
        }
    }

    fun setAnnounceSource(announce: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setAnnounceSource(announce)
        }
    }

    fun setRepeatTwice(repeat: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setRepeatTwice(repeat)
        }
    }

    fun toggleChannel(channel: PaymentChannel) {
        viewModelScope.launch {
            container.preferencesRepository.toggleChannel(channel)
        }
    }

    fun toggleForegroundGuard(enabled: Boolean) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            container.preferencesRepository.setForegroundGuard(enabled)
            if (enabled) {
                SoundboxForegroundService.start(context)
            } else {
                SoundboxForegroundService.stop(context)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectFilterChannel(channel: PaymentChannel?) {
        selectedFilter.value = channel
    }

    fun selectDateFilter(dateFilter: DateFilterOption) {
        selectedDateFilter.value = dateFilter
    }

    fun selectSortOption(sortOption: TransactionSortOption) {
        selectedSortOption.value = sortOption
    }

    fun resetHistoryFilters() {
        searchQuery.value = ""
        selectedFilter.value = null
        selectedDateFilter.value = DateFilterOption.ALL_TIME
        selectedSortOption.value = TransactionSortOption.NEWEST_FIRST
    }

    fun testCurrentVoice() {
        val settings = uiState.value.settings
        val localized = SpeechFormatter.sampleAnnouncement(settings)
        val englishFallback = SpeechFormatter.sampleAnnouncement(
            settings.copy(language = VoiceLanguage.ENGLISH)
        )
        container.speakerEngine.announceText(localized, englishFallback, settings)
    }

    fun simulatePayment(
        amount: Double,
        currency: String,
        senderName: String?,
        channel: PaymentChannel,
    ) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            val settings = uiState.value.settings
            val formatted = PaymentNotificationParser.formatAmount(amount)
            val cleanSender = senderName?.trim()?.takeIf { it.isNotEmpty() }
            val simulatedTitle = "${channel.displayName} Payment Received"
            val simulatedBody = buildString {
                append("You have received $currency $formatted")
                if (cleanSender != null) append(" from $cleanSender")
                append(" on ${channel.displayName}.")
            }

            // Verify through the exact same parser used for live notifications
            val parsed = PaymentNotificationParser.parse(
                packageName = channel.packageHints.firstOrNull() ?: "com.payspeaker.test",
                title = simulatedTitle,
                text = simulatedBody,
            )

            val finalAmount = parsed?.amount ?: amount
            val finalCurrency = parsed?.currency ?: currency
            val finalFormatted = parsed?.formattedAmount ?: formatted
            val finalSender = parsed?.senderName ?: cleanSender
            val finalChannel = parsed?.channel ?: channel

            val spokenText = if (settings.isSpeakerEnabled) {
                container.speakerEngine.announcePayment(
                    amount = finalAmount,
                    currency = finalCurrency,
                    senderName = finalSender,
                    sourceLabel = finalChannel.displayName,
                    settings = settings,
                )
            } else {
                SpeechFormatter.formatAnnouncement(
                    amount = finalAmount,
                    currency = finalCurrency,
                    senderName = finalSender,
                    sourceLabel = finalChannel.displayName,
                    settings = settings,
                )
            }

            container.paymentRepository.recordAlert(
                PaymentAlertEntity(
                    amount = finalAmount,
                    currency = finalCurrency,
                    formattedAmount = finalFormatted,
                    senderName = finalSender,
                    channelName = finalChannel.name,
                    sourceLabel = finalChannel.displayName,
                    packageName = channel.packageHints.firstOrNull() ?: "com.payspeaker.test",
                    rawTitle = simulatedTitle,
                    rawMessage = simulatedBody,
                    spokenText = spokenText,
                    wasSpoken = settings.isSpeakerEnabled,
                    isSimulated = true,
                )
            )
        }
    }

    fun replayAlert(alert: PaymentAlertEntity) {
        val settings = uiState.value.settings
        container.speakerEngine.announcePayment(
            amount = alert.amount,
            currency = alert.currency,
            senderName = alert.senderName,
            sourceLabel = alert.sourceLabel,
            settings = settings,
        )
    }

    fun deleteAlert(id: Long) {
        viewModelScope.launch {
            container.paymentRepository.deleteAlert(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.paymentRepository.clearAll()
        }
    }

    class Factory(
        private val application: Application,
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PaySpeakerViewModel(application, container) as T
        }
    }
}
