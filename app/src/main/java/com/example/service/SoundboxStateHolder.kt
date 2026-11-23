package com.example.service

import com.example.data.preferences.VoiceLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TtsVoiceOption(
    val name: String,
    val displayLabel: String,
    val localeTag: String,
    val requiresNetwork: Boolean,
)

class SoundboxStateHolder {
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentUtterance = MutableStateFlow<String?>(null)
    val currentUtterance: StateFlow<String?> = _currentUtterance.asStateFlow()

    private val _ttsReady = MutableStateFlow(false)
    val ttsReady: StateFlow<Boolean> = _ttsReady.asStateFlow()

    private val _ttsEngineLabel = MutableStateFlow("Initializing...")
    val ttsEngineLabel: StateFlow<String> = _ttsEngineLabel.asStateFlow()

    private val _availableLanguages = MutableStateFlow(setOf(VoiceLanguage.ENGLISH))
    val availableLanguages: StateFlow<Set<VoiceLanguage>> = _availableLanguages.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoiceOption>> = _availableVoices.asStateFlow()

    private val _listenerConnected = MutableStateFlow(false)
    val listenerConnected: StateFlow<Boolean> = _listenerConnected.asStateFlow()

    private val _foregroundGuardRunning = MutableStateFlow(false)
    val foregroundGuardRunning: StateFlow<Boolean> = _foregroundGuardRunning.asStateFlow()

    fun setSpeaking(speaking: Boolean, utterance: String? = null) {
        _isSpeaking.value = speaking
        _currentUtterance.value = if (speaking) utterance else null
    }

    fun setTtsReady(
        ready: Boolean,
        languages: Set<VoiceLanguage>,
        engineLabel: String,
        voices: List<TtsVoiceOption> = emptyList(),
    ) {
        _ttsReady.value = ready
        _availableLanguages.value = languages
        _ttsEngineLabel.value = engineLabel
        _availableVoices.value = voices
    }

    fun setListenerConnected(connected: Boolean) {
        _listenerConnected.value = connected
    }

    fun setForegroundGuardRunning(running: Boolean) {
        _foregroundGuardRunning.value = running
    }
}
