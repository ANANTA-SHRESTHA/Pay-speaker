package com.example.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.PaymentAlertEntity
import com.example.data.preferences.AnnouncementReadMode
import com.example.data.preferences.PaymentChannel
import com.example.data.preferences.SoundboxSettings
import com.example.data.preferences.VoiceLanguage
import com.example.data.preferences.VoiceSourceMode
import com.example.data.remote.friendlyFirestoreMessage
import com.example.domain.DateFilterOption
import com.example.domain.PaymentNotificationParser
import com.example.domain.SpeechFormatter
import com.example.domain.StatementExportResult
import com.example.domain.StatementExporter
import com.example.domain.TransactionHistoryFilter
import com.example.domain.TransactionSortOption
import com.example.service.DailySummaryScheduler
import com.example.service.SoundboxForegroundService
import com.example.service.TtsVoiceOption
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionStatus(
    val notificationListenerGranted: Boolean = false,
    val postNotificationsGranted: Boolean = true,
    val recordAudioGranted: Boolean = false,
    val smsPermissionGranted: Boolean = false,
)

data class PaySpeakerUiState(
    val settings: SoundboxSettings = SoundboxSettings(),
    val recentAlerts: List<PaymentAlertEntity> = emptyList(),
    val filteredAlerts: List<PaymentAlertEntity> = emptyList(),
    val totalAlertsCount: Int = 0,
    val totalBalance: Double = 0.0,
    val todayTotal: Double = 0.0,
    val todayCount: Int = 0,
    val thisMonthTotal: Double = 0.0,
    val thisMonthCount: Int = 0,
    val filteredTotal: Double = 0.0,
    val showBalance: Boolean = true,
    val isExportingStatement: Boolean = false,
    val lastExportResult: StatementExportResult? = null,
    val statementExportError: String? = null,
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
    val userEmail: String? = null,
    val userDisplayName: String? = null,
    val cloudAlertsLoaded: Boolean = false,
    val cloudSyncError: String? = null,
    val dailySummaryFeedback: String? = null,
    val adSdkInitialized: Boolean = false,
    val isUsingTestAdUnits: Boolean = true,
    val bannerAdStatus: String = "Initializing Google Mobile Ads…",
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
    val adSdkInitialized: Boolean,
    val bannerAdStatus: String,
)

private data class StatementUiSnapshot(
    val showBalance: Boolean = true,
    val isExporting: Boolean = false,
    val lastExportResult: StatementExportResult? = null,
    val exportError: String? = null,
    val dailySummaryFeedback: String? = null,
)

private data class CloudAlertsResult(
    val alerts: List<PaymentAlertEntity>,
    val isLoaded: Boolean = false,
    val error: String? = null,
)

private data class HistorySnapshot(
    val allAlerts: List<PaymentAlertEntity>,
    val filteredAlerts: List<PaymentAlertEntity>,
    val totalBalance: Double,
    val filteredTotal: Double,
    val todayTotal: Double,
    val todayCount: Int,
    val thisMonthTotal: Double,
    val thisMonthCount: Int,
    val query: String,
    val channelFilter: PaymentChannel?,
    val dateFilter: DateFilterOption,
    val sortOption: TransactionSortOption,
    val isLoaded: Boolean,
    val cloudError: String?,
)

private data class CloudSyncableSettings(
    val merchantName: String,
    val language: VoiceLanguage,
    val speechRate: Float,
    val speechPitch: Float,
    val boostVolume: Boolean,
    val playChime: Boolean,
    val announceSender: Boolean,
    val announceSource: Boolean,
    val repeatTwice: Boolean,
    val enabledChannels: Set<PaymentChannel>,
)

@OptIn(FlowPreview::class)
class PaySpeakerViewModel(
    application: Application,
    private val container: AppContainer,
    private val currentUserId: String,
    private val userEmail: String?,
    private val userDisplayName: String?,
) : AndroidViewModel(application) {

    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow<PaymentChannel?>(null)
    private val selectedDateFilter = MutableStateFlow(DateFilterOption.ALL_TIME)
    private val selectedSortOption = MutableStateFlow(TransactionSortOption.NEWEST_FIRST)
    private val permissionStatus = MutableStateFlow(readPermissionStatus())
    private val voiceRecorderError = MutableStateFlow<String?>(null)
    private val mutationError = MutableStateFlow<String?>(null)
    private val showBalanceState = MutableStateFlow(true)
    private val isExportingStatementState = MutableStateFlow(false)
    private val lastExportResultState = MutableStateFlow<StatementExportResult?>(null)
    private val statementExportErrorState = MutableStateFlow<String?>(null)
    private val dailySummaryFeedbackState = MutableStateFlow<String?>(null)
    @Volatile
    private var latestAllAlerts: List<PaymentAlertEntity> = emptyList()

    private val statementUiFlow = combine(
        showBalanceState,
        isExportingStatementState,
        lastExportResultState,
        statementExportErrorState,
        dailySummaryFeedbackState,
    ) { showBal, exporting, exportResult, exportErr, summaryMsg ->
        StatementUiSnapshot(
            showBalance = showBal,
            isExporting = exporting,
            lastExportResult = exportResult,
            exportError = exportErr,
            dailySummaryFeedback = summaryMsg,
        )
    }

    private val cloudAlertsFlow = container.firestorePaymentRepository
        .observeAlerts(currentUserId)
        .map { remoteList ->
            CloudAlertsResult(
                alerts = remoteList.map { it.toEntity() },
                isLoaded = true,
                error = null,
            )
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing cloud payment alerts", rootError)
            emit(
                CloudAlertsResult(
                    alerts = emptyList(),
                    isLoaded = true,
                    error = friendlyFirestoreMessage(rootError),
                )
            )
        }

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
            adSdkInitialized = container.adMobManager.isSdkInitialized.value,
            bannerAdStatus = container.adMobManager.bannerStatusMessage.value,
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
    }.combine(container.adMobManager.isSdkInitialized) { snapshot, adsReady ->
        snapshot.copy(adSdkInitialized = adsReady)
    }.combine(container.adMobManager.bannerStatusMessage) { snapshot, adStatus ->
        snapshot.copy(bannerAdStatus = adStatus)
    }

    private val historyFlow = combine(
        cloudAlertsFlow,
        searchQuery,
        selectedFilter,
        selectedDateFilter,
        selectedSortOption,
    ) { cloudResult, query, channelFilter, dateFilter, sortOption ->
        val allAlerts = cloudResult.alerts
        latestAllAlerts = allAlerts
        val filtered = TransactionHistoryFilter.apply(
            alerts = allAlerts,
            query = query,
            channelFilter = channelFilter,
            dateFilter = dateFilter,
            sortOption = sortOption,
        )
        val nowMillis = System.currentTimeMillis()
        val startOfToday = TransactionHistoryFilter.startTimestampFor(
            DateFilterOption.TODAY,
            nowMillis,
        ) ?: 0L
        val startOfMonth = TransactionHistoryFilter.startTimestampFor(
            DateFilterOption.THIS_MONTH,
            nowMillis,
        ) ?: 0L
        val todayAlerts = allAlerts.filter { it.timestamp >= startOfToday }
        val monthAlerts = allAlerts.filter { it.timestamp >= startOfMonth }

        HistorySnapshot(
            allAlerts = allAlerts,
            filteredAlerts = filtered,
            totalBalance = allAlerts.sumOf { it.amount },
            filteredTotal = filtered.sumOf { it.amount },
            todayTotal = todayAlerts.sumOf { it.amount },
            todayCount = todayAlerts.size,
            thisMonthTotal = monthAlerts.sumOf { it.amount },
            thisMonthCount = monthAlerts.size,
            query = query,
            channelFilter = channelFilter,
            dateFilter = dateFilter,
            sortOption = sortOption,
            isLoaded = cloudResult.isLoaded,
            cloudError = cloudResult.error,
        )
    }

    val uiState: StateFlow<PaySpeakerUiState> = combine(
        container.preferencesRepository.settingsFlow,
        historyFlow,
        runtimeFlow,
        mutationError,
        statementUiFlow,
    ) { settings, history, runtime, writeErr, statementUi ->
        PaySpeakerUiState(
            settings = settings,
            recentAlerts = history.allAlerts.take(5),
            filteredAlerts = history.filteredAlerts,
            totalAlertsCount = history.allAlerts.size,
            totalBalance = history.totalBalance,
            todayTotal = history.todayTotal,
            todayCount = history.todayCount,
            thisMonthTotal = history.thisMonthTotal,
            thisMonthCount = history.thisMonthCount,
            filteredTotal = history.filteredTotal,
            showBalance = statementUi.showBalance,
            isExportingStatement = statementUi.isExporting,
            lastExportResult = statementUi.lastExportResult,
            statementExportError = statementUi.exportError,
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
            userEmail = userEmail,
            userDisplayName = userDisplayName,
            cloudAlertsLoaded = history.isLoaded,
            cloudSyncError = writeErr ?: history.cloudError,
            dailySummaryFeedback = statementUi.dailySummaryFeedback,
            adSdkInitialized = runtime.adSdkInitialized,
            isUsingTestAdUnits = container.adMobManager.isUsingTestIds,
            bannerAdStatus = runtime.bannerAdStatus,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PaySpeakerUiState(
            userEmail = userEmail,
            userDisplayName = userDisplayName,
            adSdkInitialized = container.adMobManager.isSdkInitialized.value,
            isUsingTestAdUnits = container.adMobManager.isUsingTestIds,
            bannerAdStatus = container.adMobManager.bannerStatusMessage.value,
        ),
    )

    init {
        viewModelScope.launch {
            // 1. Hydrate local settings from cloud on account sign-in if remote config exists
            val remoteResult = container.firestorePaymentRepository.fetchMerchantSettings(currentUserId)
            val remote = remoteResult.getOrNull()
            if (remote != null) {
                container.preferencesRepository.applyCloudSettings(
                    merchantName = remote.merchantName,
                    language = remote.safeLanguage(),
                    speechRate = remote.speechRate.toFloat(),
                    speechPitch = remote.speechPitch.toFloat(),
                    boostVolume = remote.boostVolume,
                    playChime = remote.playChime,
                    announceSender = remote.announceSender,
                    announceSource = remote.announceSource,
                    repeatTwice = remote.repeatTwice,
                    enabledChannels = remote.safeChannels(),
                )
            }

            // 2. Debounced outbound sync when user modifies merchant voice/channel preferences
            container.preferencesRepository.settingsFlow
                .map { s ->
                    s to CloudSyncableSettings(
                        merchantName = s.merchantName,
                        language = s.language,
                        speechRate = s.speechRate,
                        speechPitch = s.speechPitch,
                        boostVolume = s.boostVolume,
                        playChime = s.playChime,
                        announceSender = s.announceSender,
                        announceSource = s.announceSource,
                        repeatTwice = s.repeatTwice,
                        enabledChannels = s.enabledChannels,
                    )
                }
                .distinctUntilChanged { old, new -> old.second == new.second }
                .drop(1)
                .debounce(800L)
                .collect { (fullSettings, _) ->
                    container.firestorePaymentRepository
                        .saveMerchantSettings(currentUserId, fullSettings)
                        .onFailure { err ->
                            mutationError.value = friendlyFirestoreMessage(err)
                        }
                }
        }
    }

    fun dismissCloudError() {
        mutationError.value = null
    }

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
        val smsGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS,
        ) == PackageManager.PERMISSION_GRANTED

        return PermissionStatus(
            notificationListenerGranted = listenerEnabled,
            postNotificationsGranted = postGranted,
            recordAudioGranted = recordAudioGranted,
            smsPermissionGranted = smsGranted,
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

    fun setAnnouncementReadMode(mode: AnnouncementReadMode) {
        viewModelScope.launch {
            when (mode) {
                AnnouncementReadMode.PAYMENT_ONLY -> {
                    container.preferencesRepository.setAnnounceSender(false)
                    container.preferencesRepository.setAnnounceSource(false)
                }
                AnnouncementReadMode.PAYMENT_AND_SENDER -> {
                    container.preferencesRepository.setAnnounceSender(true)
                    container.preferencesRepository.setAnnounceSource(false)
                }
            }
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

    fun setDailySummaryEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            container.preferencesRepository.setDailySummaryEnabled(enabled)
            val updated = uiState.value.settings.copy(dailySummaryEnabled = enabled)
            DailySummaryScheduler.syncSchedule(context, updated, replaceExisting = true)
            dailySummaryFeedbackState.value = null
        }
    }

    fun setDailySummaryTime(hour: Int, minute: Int) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            container.preferencesRepository.setDailySummaryTime(hour, minute)
            val updated = uiState.value.settings.copy(
                dailySummaryHour = hour.coerceIn(0, 23),
                dailySummaryMinute = minute.coerceIn(0, 59),
            )
            DailySummaryScheduler.syncSchedule(context, updated, replaceExisting = true)
            dailySummaryFeedbackState.value = null
        }
    }

    fun sendDailySummaryNow() {
        val context = getApplication<Application>()
        DailySummaryScheduler.triggerImmediateSummary(context)
        dailySummaryFeedbackState.value = "Daily balance summary notification sent."
    }

    fun dismissDailySummaryFeedback() {
        dailySummaryFeedbackState.value = null
    }

    fun retryBannerAd() {
        container.adMobManager.retryBannerAd()
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
            mutationError.value = null
            val settings = uiState.value.settings
            val formatted = PaymentNotificationParser.formatAmount(amount)
            val cleanSender = senderName?.trim()?.takeIf { it.isNotEmpty() }
            val simulatedTitle = "${channel.displayName} Payment Received"
            val simulatedBody = buildString {
                append("You have received $currency $formatted")
                if (cleanSender != null) append(" from $cleanSender")
                append(" on ${channel.displayName}.")
            }

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

            val entity = PaymentAlertEntity(
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
            container.paymentRepository.recordAlert(entity)
            container.firestorePaymentRepository
                .addAlert(currentUserId, entity)
                .onFailure { err ->
                    mutationError.value = friendlyFirestoreMessage(err)
                }
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

    fun deleteAlert(alert: PaymentAlertEntity) {
        viewModelScope.launch {
            mutationError.value = null
            if (alert.cloudId.isNotBlank()) {
                container.firestorePaymentRepository
                    .deleteAlert(currentUserId, alert.cloudId)
                    .onFailure { err ->
                        mutationError.value = friendlyFirestoreMessage(err)
                    }
            }
            if (alert.id > 0L) {
                container.paymentRepository.deleteAlert(alert.id)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            mutationError.value = null
            container.paymentRepository.clearAll()
            container.firestorePaymentRepository
                .clearAllAlerts(currentUserId)
                .onFailure { err ->
                    mutationError.value = friendlyFirestoreMessage(err)
                }
        }
    }

    fun toggleBalanceVisibility() {
        showBalanceState.value = !showBalanceState.value
    }

    fun dismissStatementExportBanner() {
        lastExportResultState.value = null
        statementExportErrorState.value = null
    }

    fun exportStatementPdf(
        targetUri: Uri? = null,
        includeFilteredOnly: Boolean = true,
    ) {
        exportStatementInternal(
            isPdf = true,
            targetUri = targetUri,
            includeFilteredOnly = includeFilteredOnly,
        )
    }

    fun exportStatementCsv(
        targetUri: Uri? = null,
        includeFilteredOnly: Boolean = true,
    ) {
        exportStatementInternal(
            isPdf = false,
            targetUri = targetUri,
            includeFilteredOnly = includeFilteredOnly,
        )
    }

    private fun exportStatementInternal(
        isPdf: Boolean,
        targetUri: Uri?,
        includeFilteredOnly: Boolean,
    ) {
        if (isExportingStatementState.value) return
        val state = uiState.value
        val alertsToExport = if (includeFilteredOnly) {
            state.filteredAlerts
        } else {
            TransactionHistoryFilter.apply(
                alerts = latestAllAlerts,
                query = "",
                channelFilter = null,
                dateFilter = DateFilterOption.ALL_TIME,
                sortOption = TransactionSortOption.NEWEST_FIRST,
            )
        }
        val effectiveAlerts = alertsToExport.ifEmpty { state.filteredAlerts }
        if (effectiveAlerts.isEmpty()) {
            statementExportErrorState.value = "No transactions available to export."
            return
        }

        val periodLabel = if (includeFilteredOnly) {
            state.selectedDateFilter.label
        } else {
            DateFilterOption.ALL_TIME.label
        }
        val channelLabel = if (includeFilteredOnly) {
            state.selectedFilterChannel?.displayName ?: "All Apps"
        } else {
            "All Apps"
        }

        val summary = StatementExporter.buildStatementSummary(
            alerts = effectiveAlerts,
            overallBalance = state.totalBalance,
            merchantName = state.settings.merchantName.ifBlank {
                state.userDisplayName ?: "PaySpeaker Merchant"
            },
            accountEmail = state.userEmail,
            periodLabel = periodLabel,
            channelLabel = channelLabel,
        )

        isExportingStatementState.value = true
        statementExportErrorState.value = null

        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val ext = if (isPdf) "pdf" else "csv"
                val mimeType = if (isPdf) "application/pdf" else "text/csv"
                val fileName = StatementExporter.buildFileName(ext)

                val result = withContext(Dispatchers.IO) {
                    val writer: (java.io.OutputStream) -> Unit = { out ->
                        if (isPdf) {
                            StatementExporter.writePdfStatement(out, effectiveAlerts, summary)
                        } else {
                            out.write(StatementExporter.buildCsvContent(effectiveAlerts, summary).toByteArray(Charsets.UTF_8))
                        }
                    }
                    if (targetUri != null) {
                        StatementExporter.exportToPickedUri(
                            context = context,
                            targetUri = targetUri,
                            fileName = fileName,
                            mimeType = mimeType,
                            writer = writer,
                        )
                    } else {
                        StatementExporter.exportToDownloads(
                            context = context,
                            fileName = fileName,
                            mimeType = mimeType,
                            writer = writer,
                        )
                    }
                }
                lastExportResultState.value = result
            } catch (e: Exception) {
                Log.w(TAG, "Statement export failed", e)
                statementExportErrorState.value = e.message ?: "Failed to export statement."
            } finally {
                isExportingStatementState.value = false
            }
        }
    }

    class Factory(
        private val application: Application,
        private val container: AppContainer,
        private val currentUserId: String,
        private val userEmail: String?,
        private val userDisplayName: String?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PaySpeakerViewModel(
                application = application,
                container = container,
                currentUserId = currentUserId,
                userEmail = userEmail,
                userDisplayName = userDisplayName,
            ) as T
        }
    }

    private companion object {
        const val TAG = "PaySpeakerVM"
    }
}
