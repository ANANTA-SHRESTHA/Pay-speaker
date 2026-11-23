package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.data.preferences.SoundboxSettings
import com.example.data.preferences.VoiceLanguage
import com.example.data.preferences.VoiceSourceMode
import com.example.domain.SpeechFormatter
import com.example.service.SoundboxStateHolder
import com.example.service.TtsVoiceOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class PaymentSpeakerEngine(
    private val context: Context,
    private val stateHolder: SoundboxStateHolder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val speakMutex = Mutex()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var isInitialized = false

    @Volatile
    private var ttsInitSignal = CompletableDeferred<Boolean>()

    @Volatile
    private var activeFinalUtteranceId: String? = null

    @Volatile
    private var activeUtteranceDeferred: CompletableDeferred<Unit>? = null

    @Volatile
    private var activeMediaPlayer: MediaPlayer? = null

    @Volatile
    private var activeMediaDeferred: CompletableDeferred<Unit>? = null

    private var savedPreviousVolume: Int? = null
    private var focusRequest: AudioFocusRequest? = null

    init {
        initTts()
    }

    private fun initTts() {
        try {
            ttsInitSignal = CompletableDeferred()
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    val available = detectAvailableLanguages()
                    val voices = extractAvailableVoices()
                    val enginePkg = tts?.defaultEngine.orEmpty().ifBlank { "Android TTS" }
                    stateHolder.setTtsReady(true, available, enginePkg, voices)
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}

                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == activeFinalUtteranceId) {
                                activeUtteranceDeferred?.complete(Unit)
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (utteranceId == activeFinalUtteranceId || utteranceId != null) {
                                activeUtteranceDeferred?.complete(Unit)
                            }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            Log.w(TAG, "TTS utterance error code=$errorCode id=$utteranceId")
                            activeUtteranceDeferred?.complete(Unit)
                        }

                        override fun onStop(utteranceId: String?, interrupted: Boolean) {
                            activeUtteranceDeferred?.complete(Unit)
                        }
                    })
                    ttsInitSignal.complete(true)
                } else {
                    Log.w(TAG, "TextToSpeech init failed with status $status")
                    stateHolder.setTtsReady(false, emptySet(), "TTS unavailable (status $status)")
                    ttsInitSignal.complete(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to initialize TTS", e)
            stateHolder.setTtsReady(false, emptySet(), "TTS unavailable")
            ttsInitSignal.complete(false)
        }
    }

    private fun detectAvailableLanguages(): Set<VoiceLanguage> {
        val engine = tts ?: return setOf(VoiceLanguage.ENGLISH)
        val supported = mutableSetOf<VoiceLanguage>()
        VoiceLanguage.entries.forEach { lang ->
            val locale = Locale(lang.languageTag, lang.countryTag)
            val result = try {
                engine.isLanguageAvailable(locale)
            } catch (_: Exception) {
                TextToSpeech.LANG_NOT_SUPPORTED
            }
            if (result >= TextToSpeech.LANG_AVAILABLE) {
                supported.add(lang)
            }
        }
        if (supported.isEmpty()) {
            supported.add(VoiceLanguage.ENGLISH)
        }
        return supported
    }

    private fun extractAvailableVoices(): List<TtsVoiceOption> {
        val rawVoices = try {
            tts?.voices.orEmpty()
        } catch (_: Exception) {
            emptySet()
        }
        val supportedLangs = setOf("en", "ne", "hi")
        return rawVoices
            .filter { voice ->
                voice.locale.language.lowercase(Locale.ROOT) in supportedLangs
            }
            .sortedWith(
                compareBy<Voice>(
                    { it.isNetworkConnectionRequired },
                    { it.locale.language },
                    { -it.quality },
                    { it.name },
                )
            )
            .distinctBy { it.name }
            .take(24)
            .mapIndexed { idx, voice ->
                val qualityLabel = when {
                    voice.quality >= Voice.QUALITY_HIGH -> "HD"
                    voice.quality >= Voice.QUALITY_NORMAL -> "Standard"
                    else -> "Compact"
                }
                val networkTag = if (voice.isNetworkConnectionRequired) "Online" else "Offline"
                val localeTag = voice.locale.toLanguageTag()
                TtsVoiceOption(
                    name = voice.name,
                    displayLabel = "${voice.locale.displayLanguage} (${voice.locale.country.ifBlank { localeTag }}) • $qualityLabel $networkTag #${idx + 1}",
                    localeTag = localeTag,
                    requiresNetwork = voice.isNetworkConnectionRequired,
                )
            }
    }

    fun announceText(
        spokenText: String,
        fallbackEnglishText: String,
        settings: SoundboxSettings,
    ) {
        scope.launch {
            speakMutex.withLock {
                if (!isInitialized) {
                    withTimeoutOrNull(1800L) { ttsInitSignal.await() }
                }

                stateHolder.setSpeaking(true, spokenText)
                requestAudioFocusAndBoost(settings.boostVolume)

                val clipPath = settings.ownVoiceFilePath?.takeIf { File(it).exists() }
                val useOwnVoice = clipPath != null && settings.voiceSourceMode != VoiceSourceMode.TTS_VOICE

                if (useOwnVoice && clipPath != null) {
                    playAudioClip(clipPath)
                    if (settings.voiceSourceMode == VoiceSourceMode.OWN_VOICE_ONLY) {
                        if (settings.repeatTwice) {
                            delay(280)
                            playAudioClip(clipPath)
                        }
                        onPlaybackFinished()
                        return@withLock
                    } else {
                        delay(150)
                        speakViaTts(spokenText, fallbackEnglishText, settings)
                        onPlaybackFinished()
                        return@withLock
                    }
                }

                if (settings.playChime) {
                    playMerchantChime()
                    delay(100)
                }

                speakViaTts(spokenText, fallbackEnglishText, settings)
                onPlaybackFinished()
            }
        }
    }

    fun previewAudioClip(filePath: String, boostVolume: Boolean) {
        scope.launch {
            speakMutex.withLock {
                if (!File(filePath).exists()) return@withLock
                stateHolder.setSpeaking(true, "Playing your custom voice clip…")
                requestAudioFocusAndBoost(boostVolume)
                playAudioClip(filePath)
                onPlaybackFinished()
            }
        }
    }

    private suspend fun speakViaTts(
        spokenText: String,
        fallbackEnglishText: String,
        settings: SoundboxSettings,
    ) {
        val engine = tts
        if (engine != null && isInitialized) {
            val requestedLocale = Locale(settings.language.languageTag, settings.language.countryTag)
            val availability = try {
                engine.isLanguageAvailable(requestedLocale)
            } catch (_: Exception) {
                TextToSpeech.LANG_NOT_SUPPORTED
            }

            val customVoice = settings.selectedVoiceName?.let { targetName ->
                runCatching { engine.voices?.firstOrNull { it.name == targetName } }.getOrNull()
            }

            val textToSpeak = if (customVoice != null && availability >= TextToSpeech.LANG_AVAILABLE) {
                engine.voice = customVoice
                spokenText
            } else if (availability >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = requestedLocale
                spokenText
            } else {
                engine.language = Locale.US
                fallbackEnglishText
            }

            engine.setSpeechRate(settings.speechRate)
            engine.setPitch(settings.speechPitch)

            val baseId = UUID.randomUUID().toString()
            val finalId = "${baseId}_FINAL"
            val completion = CompletableDeferred<Unit>()
            activeFinalUtteranceId = finalId
            activeUtteranceDeferred = completion

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }

            val speakResult = if (settings.repeatTwice && settings.voiceSourceMode == VoiceSourceMode.TTS_VOICE) {
                engine.speak(textToSpeak, TextToSpeech.QUEUE_ADD, params, "${baseId}_1")
                engine.playSilentUtterance(420L, TextToSpeech.QUEUE_ADD, "${baseId}_GAP")
                engine.speak(textToSpeak, TextToSpeech.QUEUE_ADD, params, finalId)
            } else {
                engine.speak(textToSpeak, TextToSpeech.QUEUE_ADD, params, finalId)
            }

            if (speakResult == TextToSpeech.SUCCESS) {
                withTimeoutOrNull(if (settings.repeatTwice) 14_000L else 8_000L) {
                    completion.await()
                }
            } else {
                Log.w(TAG, "TextToSpeech.speak returned error=$speakResult")
                delay(800L)
            }

            activeFinalUtteranceId = null
            activeUtteranceDeferred = null
        } else {
            delay(1200L)
        }
    }

    private suspend fun playAudioClip(filePath: String) {
        val doneSignal = CompletableDeferred<Unit>()
        activeMediaDeferred = doneSignal
        var player: MediaPlayer? = null
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(filePath)
                setOnCompletionListener {
                    doneSignal.complete(Unit)
                }
                setOnErrorListener { _, _, _ ->
                    doneSignal.complete(Unit)
                    true
                }
                prepare()
                start()
            }
            activeMediaPlayer = player
            withTimeoutOrNull(16_000L) {
                doneSignal.await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed playing custom audio clip: ${e.message}")
            doneSignal.complete(Unit)
        } finally {
            activeMediaPlayer = null
            activeMediaDeferred = null
            runCatching { player?.stop() }
            runCatching { player?.release() }
        }
    }

    fun announcePayment(
        amount: Double,
        currency: String,
        senderName: String?,
        sourceLabel: String,
        settings: SoundboxSettings,
    ): String {
        val localized = SpeechFormatter.formatAnnouncement(
            amount = amount,
            currency = currency,
            senderName = senderName,
            sourceLabel = sourceLabel,
            settings = settings,
        )
        val englishFallback = SpeechFormatter.formatAnnouncement(
            amount = amount,
            currency = currency,
            senderName = senderName,
            sourceLabel = sourceLabel,
            settings = settings.copy(language = VoiceLanguage.ENGLISH),
        )
        announceText(localized, englishFallback, settings)
        return localized
    }

    fun stopSpeaking() {
        runCatching { tts?.stop() }
        runCatching { activeMediaPlayer?.stop() }
        activeUtteranceDeferred?.complete(Unit)
        activeMediaDeferred?.complete(Unit)
        onPlaybackFinished()
    }

    private fun requestAudioFocusAndBoost(boostVolume: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                focusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                )
            }

            if (boostVolume) {
                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val target = (max * 0.85f).toInt().coerceAtLeast(1)
                if (current < target) {
                    savedPreviousVolume = current
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus/boost adjustment skipped: ${e.message}")
        }
    }

    private fun onPlaybackFinished() {
        stateHolder.setSpeaking(false, null)
        try {
            savedPreviousVolume?.let { prev ->
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, prev, 0)
                savedPreviousVolume = null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                focusRequest = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed restoring audio state: ${e.message}")
        }
    }

    private fun playMerchantChime() {
        try {
            val sampleRate = 22050
            val note1Samples = (sampleRate * 0.14).toInt()
            val note2Samples = (sampleRate * 0.24).toInt()
            val totalSamples = note1Samples + note2Samples
            val buffer = ShortArray(totalSamples)

            for (i in 0 until note1Samples) {
                val t = i.toDouble() / sampleRate
                val env = exp(-t * 14.0) * (1.0 - exp(-t * 240.0))
                val sample = (sin(2.0 * PI * 783.99 * t) + 0.35 * sin(2.0 * PI * 1567.98 * t)) * env
                buffer[i] = (sample * 0.55 * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            for (i in 0 until note2Samples) {
                val t = i.toDouble() / sampleRate
                val env = exp(-t * 9.5) * (1.0 - exp(-t * 240.0))
                val sample = (sin(2.0 * PI * 1046.50 * t) + 0.3 * sin(2.0 * PI * 2093.0 * t)) * env
                buffer[note1Samples + i] = (sample * 0.6 * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, buffer.size)
            track.play()
            Thread.sleep(390)
            track.stop()
            track.release()
        } catch (e: Exception) {
            Log.w(TAG, "Chime playback skipped: ${e.message}")
        }
    }

    fun refreshLanguages() {
        if (isInitialized) {
            val enginePkg = tts?.defaultEngine.orEmpty().ifBlank { "Android TTS" }
            stateHolder.setTtsReady(true, detectAvailableLanguages(), enginePkg, extractAvailableVoices())
        }
    }

    fun shutdown() {
        try {
            activeUtteranceDeferred?.complete(Unit)
            activeMediaDeferred?.complete(Unit)
            activeMediaPlayer?.release()
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "PaymentSpeakerEngine"
    }
}
