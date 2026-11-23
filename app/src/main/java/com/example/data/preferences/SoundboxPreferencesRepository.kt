package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "soundbox_prefs")

class SoundboxPreferencesRepository(private val context: Context) {

    private object Keys {
        val SPEAKER_ENABLED = booleanPreferencesKey("speaker_enabled")
        val MERCHANT_NAME = stringPreferencesKey("merchant_name")
        val LANGUAGE = stringPreferencesKey("voice_language")
        val SELECTED_VOICE_NAME = stringPreferencesKey("selected_voice_name")
        val VOICE_SOURCE_MODE = stringPreferencesKey("voice_source_mode")
        val OWN_VOICE_PATH = stringPreferencesKey("own_voice_path")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val BOOST_VOLUME = booleanPreferencesKey("boost_volume")
        val PLAY_CHIME = booleanPreferencesKey("play_chime")
        val ANNOUNCE_SENDER = booleanPreferencesKey("announce_sender")
        val ANNOUNCE_SOURCE = booleanPreferencesKey("announce_source")
        val REPEAT_TWICE = booleanPreferencesKey("repeat_twice")
        val FOREGROUND_GUARD = booleanPreferencesKey("foreground_guard")
        val ENABLED_CHANNELS = stringSetPreferencesKey("enabled_channels")
    }

    val settingsFlow: Flow<SoundboxSettings> = context.dataStore.data.map { prefs ->
        val languageName = prefs[Keys.LANGUAGE] ?: VoiceLanguage.ENGLISH.name
        val language = VoiceLanguage.entries.find { it.name == languageName } ?: VoiceLanguage.ENGLISH
        val savedChannels = prefs[Keys.ENABLED_CHANNELS]
        val enabledChannels = if (savedChannels == null) {
            PaymentChannel.entries.toSet()
        } else {
            savedChannels.mapNotNull { name ->
                PaymentChannel.entries.find { it.name == name }
            }.toSet()
        }

        val modeName = prefs[Keys.VOICE_SOURCE_MODE] ?: VoiceSourceMode.TTS_VOICE.name
        val voiceSourceMode = VoiceSourceMode.entries.find { it.name == modeName }
            ?: VoiceSourceMode.TTS_VOICE

        SoundboxSettings(
            isSpeakerEnabled = prefs[Keys.SPEAKER_ENABLED] ?: true,
            merchantName = prefs[Keys.MERCHANT_NAME] ?: "",
            language = language,
            selectedVoiceName = prefs[Keys.SELECTED_VOICE_NAME]?.takeIf { it.isNotBlank() },
            voiceSourceMode = voiceSourceMode,
            ownVoiceFilePath = prefs[Keys.OWN_VOICE_PATH]?.takeIf { it.isNotBlank() },
            speechRate = (prefs[Keys.SPEECH_RATE] ?: 1.0f).coerceIn(0.6f, 1.6f),
            speechPitch = (prefs[Keys.SPEECH_PITCH] ?: 1.0f).coerceIn(0.7f, 1.4f),
            boostVolume = prefs[Keys.BOOST_VOLUME] ?: true,
            playChime = prefs[Keys.PLAY_CHIME] ?: true,
            announceSender = prefs[Keys.ANNOUNCE_SENDER] ?: true,
            announceSource = prefs[Keys.ANNOUNCE_SOURCE] ?: true,
            repeatTwice = prefs[Keys.REPEAT_TWICE] ?: false,
            foregroundGuardEnabled = prefs[Keys.FOREGROUND_GUARD] ?: false,
            enabledChannels = enabledChannels,
        )
    }

    suspend fun setSpeakerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SPEAKER_ENABLED] = enabled }
    }

    suspend fun setMerchantName(name: String) {
        context.dataStore.edit { it[Keys.MERCHANT_NAME] = name.take(40) }
    }

    suspend fun setLanguage(language: VoiceLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setSelectedVoiceName(voiceName: String?) {
        context.dataStore.edit {
            if (voiceName.isNullOrBlank()) {
                it.remove(Keys.SELECTED_VOICE_NAME)
            } else {
                it[Keys.SELECTED_VOICE_NAME] = voiceName
            }
        }
    }

    suspend fun setVoiceSourceMode(mode: VoiceSourceMode) {
        context.dataStore.edit { it[Keys.VOICE_SOURCE_MODE] = mode.name }
    }

    suspend fun setOwnVoiceFilePath(path: String?) {
        context.dataStore.edit {
            if (path.isNullOrBlank()) {
                it.remove(Keys.OWN_VOICE_PATH)
            } else {
                it[Keys.OWN_VOICE_PATH] = path
            }
        }
    }

    suspend fun setSpeechRate(rate: Float) {
        context.dataStore.edit { it[Keys.SPEECH_RATE] = rate.coerceIn(0.6f, 1.6f) }
    }

    suspend fun setSpeechPitch(pitch: Float) {
        context.dataStore.edit { it[Keys.SPEECH_PITCH] = pitch.coerceIn(0.7f, 1.4f) }
    }

    suspend fun setBoostVolume(boost: Boolean) {
        context.dataStore.edit { it[Keys.BOOST_VOLUME] = boost }
    }

    suspend fun setPlayChime(play: Boolean) {
        context.dataStore.edit { it[Keys.PLAY_CHIME] = play }
    }

    suspend fun setAnnounceSender(announce: Boolean) {
        context.dataStore.edit { it[Keys.ANNOUNCE_SENDER] = announce }
    }

    suspend fun setAnnounceSource(announce: Boolean) {
        context.dataStore.edit { it[Keys.ANNOUNCE_SOURCE] = announce }
    }

    suspend fun setRepeatTwice(repeat: Boolean) {
        context.dataStore.edit { it[Keys.REPEAT_TWICE] = repeat }
    }

    suspend fun setForegroundGuard(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FOREGROUND_GUARD] = enabled }
    }

    suspend fun toggleChannel(channel: PaymentChannel) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_CHANNELS]
                ?: PaymentChannel.entries.map { it.name }.toSet()
            val updated = if (channel.name in current) {
                current - channel.name
            } else {
                current + channel.name
            }
            prefs[Keys.ENABLED_CHANNELS] = updated
        }
    }
}
