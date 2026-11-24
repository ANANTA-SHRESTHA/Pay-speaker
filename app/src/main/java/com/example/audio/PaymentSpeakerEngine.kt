package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import kotlinx.coroutines.Job
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
    private var speakingTimeoutJob: Job? = null

    @Volatile
    private var activeMediaPlayer: MediaPlayer? = null

    @Volatile
    private var activeMediaDeferred: CompletableDeferred<Unit>? = null

    @Volatile
    private var cachedSupportedLanguages: Set<VoiceLanguage> = setOf(VoiceLanguage.ENGLISH)

    @Volatile
    private var cachedBestOfflineVoiceByLang: Map<VoiceLanguage, Voice?> = emptyMap()

    @Volatile
    private var cachedBestAnyVoiceByLang: Map<VoiceLanguage, Voice?> = emptyMap()

    @Volatile
    private var cachedVoicesByName: Map<String, Voice> = emptyMap()

    @Volatile
    private var lastKnownSettings: SoundboxSettings = SoundboxSettings()

    @Volatile
    private var appliedVoiceKey: String? = null

    @Volatile
    private var appliedRate: Float = Float.NaN

    @Volatile
    private var appliedPitch: Float = Float.NaN

    private var savedPreviousVolume: Int? = null
    private var focusRequest: AudioFocusRequest? = null

    private val chimePcmBuffer: ShortArray = buildPrecomputedChimePcm()
    private val instantTtsParams = Bundle().apply {
        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
    }

    init {
        initTts()
    }

    private fun initTts() {
        try {
            ttsInitSignal = CompletableDeferred()
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
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
                                onTtsUtteranceCompleted()
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (utteranceId == activeFinalUtteranceId || utteranceId != null) {
                                activeUtteranceDeferred?.complete(Unit)
                                onTtsUtteranceCompleted()
                            }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            Log.w(TAG, "TTS utterance error code=$errorCode id=$utteranceId")
                            activeUtteranceDeferred?.complete(Unit)
                            onTtsUtteranceCompleted()
                        }

                        override fun onStop(utteranceId: String?, interrupted: Boolean) {
                            activeUtteranceDeferred?.complete(Unit)
                            if (!interrupted) {
                                onTtsUtteranceCompleted()
                            }
                        }
                    })
                    rebuildVoiceCacheAndPublish()
                    prewarmForSettings(lastKnownSettings, sendSilentWarmup = true)
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

    private fun rebuildVoiceCacheAndPublish() {
        val engine = tts ?: return
        val available = detectAvailableLanguages(engine)
        val rawVoices = runCatching { engine.voices.orEmpty() }.getOrElse { emptySet() }
        val installedVoices = rawVoices.filter(::isVoiceInstalled)

        val byName = HashMap<String, Voice>(installedVoices.size)
        for (v in installedVoices) {
            byName[v.name] = v
        }

        val offlineMap = HashMap<VoiceLanguage, Voice?>()
        val anyMap = HashMap<VoiceLanguage, Voice?>()
        for (lang in VoiceLanguage.entries) {
            val locale = Locale(lang.languageTag, lang.countryTag)
            val langVoices = installedVoices.filter {
                it.locale.language.equals(lang.languageTag, ignoreCase = true)
            }
            offlineMap[lang] = langVoices
                .filter { !it.isNetworkConnectionRequired }
                .maxByOrNull { naturalVoiceScore(it, locale) }
            anyMap[lang] = langVoices.maxByOrNull { naturalVoiceScore(it, locale) }
        }

        cachedSupportedLanguages = available
        cachedVoicesByName = byName
        cachedBestOfflineVoiceByLang = offlineMap
        cachedBestAnyVoiceByLang = anyMap
        appliedVoiceKey = null

        val voiceOptions = buildVoiceOptions(installedVoices)
        val enginePkg = engine.defaultEngine.orEmpty().ifBlank { "Android TTS" }
        stateHolder.setTtsReady(true, available, enginePkg, voiceOptions)
    }

    private fun detectAvailableLanguages(engine: TextToSpeech): Set<VoiceLanguage> {
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

    private fun isVoiceInstalled(voice: Voice): Boolean {
        val notInstalled = voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
        return !notInstalled
    }

    private fun isOnlineConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            false
        }
    }

    private fun naturalVoiceScore(voice: Voice, preferredLocale: Locale): Int {
        var score = 0
        val nameLower = voice.name.lowercase(Locale.ROOT)
        if (voice.locale.country.equals(preferredLocale.country, ignoreCase = true)) {
            score += 400
        }
        // Strongly prioritize offline voices for zero-latency (<50ms) local synthesis
        if (!voice.isNetworkConnectionRequired) {
            score += 300
        }
        score += when {
            voice.quality >= Voice.QUALITY_VERY_HIGH -> 320
            voice.quality >= Voice.QUALITY_HIGH -> 220
            voice.quality >= Voice.QUALITY_NORMAL -> 100
            else -> 0
        }
        if (
            nameLower.contains("neural") ||
            nameLower.contains("wavenet") ||
            nameLower.contains("studio") ||
            nameLower.contains("journey") ||
            nameLower.contains("natural") ||
            nameLower.contains("-smr") ||
            nameLower.contains("-sfg") ||
            nameLower.contains("-x-")
        ) {
            score += 240
        }
        if (nameLower.contains("espeak") || nameLower.contains("compact")) {
            score -= 220
        }
        if (voice.latency <= Voice.LATENCY_NORMAL) {
            score += 60
        }
        return score
    }

    private fun buildVoiceOptions(installedVoices: List<Voice>): List<TtsVoiceOption> {
        val supportedLangs = setOf("en", "ne", "hi")
        return installedVoices
            .filter { voice ->
                voice.locale.language.lowercase(Locale.ROOT) in supportedLangs
            }
            .sortedWith(
                compareBy<Voice>(
                    { it.locale.language },
                    { it.isNetworkConnectionRequired },
                    { -it.quality },
                    { it.name },
                )
            )
            .distinctBy { it.name }
            .take(24)
            .mapIndexed { idx, voice ->
                val nameLower = voice.name.lowercase(Locale.ROOT)
                val isNatural = nameLower.contains("neural") ||
                    nameLower.contains("wavenet") ||
                    nameLower.contains("studio") ||
                    nameLower.contains("-x-") ||
                    voice.quality >= Voice.QUALITY_HIGH
                val qualityLabel = when {
                    voice.quality >= Voice.QUALITY_VERY_HIGH -> "Natural HD"
                    voice.quality >= Voice.QUALITY_HIGH -> if (isNatural) "Natural HD" else "HD"
                    voice.quality >= Voice.QUALITY_NORMAL -> "Standard"
                    else -> "Compact"
                }
                val networkTag = if (voice.isNetworkConnectionRequired) "Online" else "Instant Offline"
                val localeTag = voice.locale.toLanguageTag()
                TtsVoiceOption(
                    name = voice.name,
                    displayLabel = "${voice.locale.displayLanguage} (${voice.locale.country.ifBlank { localeTag }}) • $qualityLabel $networkTag #${idx + 1}",
                    localeTag = localeTag,
                    requiresNetwork = voice.isNetworkConnectionRequired,
                )
            }
    }

    fun prewarmForSettings(settings: SoundboxSettings, sendSilentWarmup: Boolean = false) {
        lastKnownSettings = settings
        val engine = tts
        if (engine == null || !isInitialized) return
        scope.launch {
            runCatching {
                applyVoiceAndTuningIfNeeded(engine, settings)
                if (sendSilentWarmup && !stateHolder.isSpeaking.value) {
                    engine.playSilentUtterance(1L, TextToSpeech.QUEUE_ADD, "WARMUP")
                }
            }
        }
    }

    private fun resolveTargetLanguageAndVoice(settings: SoundboxSettings): Pair<VoiceLanguage, Voice?> {
        val targetLang = if (settings.language in cachedSupportedLanguages) {
            settings.language
        } else {
            VoiceLanguage.ENGLISH
        }

        val explicitVoice = settings.selectedVoiceName?.let { cachedVoicesByName[it] }
        if (explicitVoice != null && targetLang == settings.language) {
            if (!explicitVoice.isNetworkConnectionRequired || isOnlineConnected()) {
                return targetLang to explicitVoice
            }
        }

        val bestOffline = cachedBestOfflineVoiceByLang[targetLang]
        if (bestOffline != null) {
            return targetLang to bestOffline
        }

        val bestAny = cachedBestAnyVoiceByLang[targetLang]
        if (bestAny != null && (!bestAny.isNetworkConnectionRequired || isOnlineConnected())) {
            return targetLang to bestAny
        }

        return targetLang to null
    }

    private fun applyVoiceAndTuningIfNeeded(
        engine: TextToSpeech,
        settings: SoundboxSettings,
    ): VoiceLanguage {
        val (targetLang, voice) = resolveTargetLanguageAndVoice(settings)
        val desiredVoiceKey = "${targetLang.name}|${voice?.name.orEmpty()}"
        if (appliedVoiceKey != desiredVoiceKey) {
            if (voice != null) {
                engine.voice = voice
            } else {
                engine.language = Locale(targetLang.languageTag, targetLang.countryTag)
            }
            appliedVoiceKey = desiredVoiceKey
        }
        if (appliedRate != settings.speechRate) {
            engine.setSpeechRate(settings.speechRate)
            appliedRate = settings.speechRate
        }
        if (appliedPitch != settings.speechPitch) {
            engine.setPitch(settings.speechPitch)
            appliedPitch = settings.speechPitch
        }
        return targetLang
    }

    fun announceText(
        spokenText: String,
        fallbackEnglishText: String,
        settings: SoundboxSettings,
    ) {
        val engine = tts
        val clipPath = settings.ownVoiceFilePath?.takeIf {
            settings.voiceSourceMode != VoiceSourceMode.TTS_VOICE && File(it).exists()
        }

        // ZERO-LATENCY INSTANT FAST PATH:
        // Directly fires audio & tts.speak(QUEUE_FLUSH) synchronously without coroutine/mutex/sleep wait!
        if (engine != null && isInitialized && clipPath == null) {
            fireTtsInstant(engine, spokenText, fallbackEnglishText, settings)
            return
        }

        // Fallback / custom audio clip path
        scope.launch {
            speakMutex.withLock {
                if (!isInitialized) {
                    withTimeoutOrNull(1800L) { ttsInitSignal.await() }
                }

                stateHolder.setSpeaking(true, spokenText)
                requestAudioFocusAndBoost(settings.boostVolume)

                if (clipPath != null) {
                    playAudioClip(clipPath)
                    if (settings.voiceSourceMode == VoiceSourceMode.OWN_VOICE_ONLY) {
                        if (settings.repeatTwice) {
                            delay(240)
                            playAudioClip(clipPath)
                        }
                        onPlaybackFinished()
                        return@withLock
                    } else {
                        speakViaTtsSuspended(spokenText, fallbackEnglishText, settings)
                        onPlaybackFinished()
                        return@withLock
                    }
                }

                if (settings.playChime) {
                    playChimeNonBlocking()
                }
                speakViaTtsSuspended(spokenText, fallbackEnglishText, settings)
                onPlaybackFinished()
            }
        }
    }

    private fun fireTtsInstant(
        engine: TextToSpeech,
        spokenText: String,
        fallbackEnglishText: String,
        settings: SoundboxSettings,
    ) {
        runCatching { activeMediaPlayer?.stop() }
        activeMediaDeferred?.complete(Unit)
        activeUtteranceDeferred?.complete(Unit)

        stateHolder.setSpeaking(true, spokenText)
        requestAudioFocusAndBoost(settings.boostVolume)

        if (settings.playChime) {
            playChimeNonBlocking()
        }

        val resolvedLang = applyVoiceAndTuningIfNeeded(engine, settings)
        val textToSpeak = if (resolvedLang == settings.language) spokenText else fallbackEnglishText

        val baseId = UUID.randomUUID().toString()
        val finalId = "${baseId}_FINAL"
        activeFinalUtteranceId = finalId

        speakingTimeoutJob?.cancel()
        val timeoutMs = if (settings.repeatTwice) 14_000L else 8_000L
        speakingTimeoutJob = scope.launch {
            delay(timeoutMs)
            if (activeFinalUtteranceId == finalId) {
                onTtsUtteranceCompleted()
            }
        }

        val speakResult = if (settings.repeatTwice && settings.voiceSourceMode == VoiceSourceMode.TTS_VOICE) {
            engine.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, instantTtsParams, "${baseId}_1")
            engine.playSilentUtterance(280L, TextToSpeech.QUEUE_ADD, "${baseId}_GAP")
            engine.speak(textToSpeak, TextToSpeech.QUEUE_ADD, instantTtsParams, finalId)
        } else {
            engine.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, instantTtsParams, finalId)
        }

        if (speakResult != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TextToSpeech.speak returned error=$speakResult")
            onTtsUtteranceCompleted()
        }
    }

    private fun onTtsUtteranceCompleted() {
        speakingTimeoutJob?.cancel()
        speakingTimeoutJob = null
        activeFinalUtteranceId = null
        activeUtteranceDeferred = null
        onPlaybackFinished()
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

    private suspend fun speakViaTtsSuspended(
        spokenText: String,
        fallbackEnglishText: String,
        settings: SoundboxSettings,
    ) {
        val engine = tts
        if (engine != null && isInitialized) {
            val resolvedLang = applyVoiceAndTuningIfNeeded(engine, settings)
            val textToSpeak = if (resolvedLang == settings.language) spokenText else fallbackEnglishText

            val baseId = UUID.randomUUID().toString()
            val finalId = "${baseId}_FINAL"
            val completion = CompletableDeferred<Unit>()
            activeFinalUtteranceId = finalId
            activeUtteranceDeferred = completion

            val speakResult = if (settings.repeatTwice && settings.voiceSourceMode == VoiceSourceMode.TTS_VOICE) {
                engine.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, instantTtsParams, "${baseId}_1")
                engine.playSilentUtterance(280L, TextToSpeech.QUEUE_ADD, "${baseId}_GAP")
                engine.speak(textToSpeak, TextToSpeech.QUEUE_ADD, instantTtsParams, finalId)
            } else {
                engine.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, instantTtsParams, finalId)
            }

            if (speakResult == TextToSpeech.SUCCESS) {
                withTimeoutOrNull(if (settings.repeatTwice) 14_000L else 8_000L) {
                    completion.await()
                }
            } else {
                Log.w(TAG, "TextToSpeech.speak returned error=$speakResult")
                delay(400L)
            }

            activeFinalUtteranceId = null
            activeUtteranceDeferred = null
        } else {
            delay(800L)
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
        val englishFallback = if (settings.language == VoiceLanguage.ENGLISH) {
            localized
        } else {
            SpeechFormatter.formatAnnouncement(
                amount = amount,
                currency = currency,
                senderName = senderName,
                sourceLabel = sourceLabel,
                settings = settings.copy(language = VoiceLanguage.ENGLISH),
            )
        }
        announceText(localized, englishFallback, settings)
        return localized
    }

    fun stopSpeaking() {
        speakingTimeoutJob?.cancel()
        speakingTimeoutJob = null
        runCatching { tts?.stop() }
        runCatching { activeMediaPlayer?.stop() }
        activeUtteranceDeferred?.complete(Unit)
        activeMediaDeferred?.complete(Unit)
        onPlaybackFinished()
    }

    private fun requestAudioFocusAndBoost(boostVolume: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                    .also { focusRequest = it }
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
                    if (savedPreviousVolume == null) {
                        savedPreviousVolume = current
                    }
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
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed restoring audio state: ${e.message}")
        }
    }

    private fun playChimeNonBlocking() {
        try {
            val sampleRate = 44100
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
                .setBufferSizeInBytes(chimePcmBuffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(chimePcmBuffer, 0, chimePcmBuffer.size)
            track.play()
            scope.launch {
                delay(200)
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Chime playback skipped: ${e.message}")
        }
    }

    private fun buildPrecomputedChimePcm(): ShortArray {
        val sampleRate = 44100
        val note1Samples = (sampleRate * 0.065).toInt()
        val note2Samples = (sampleRate * 0.115).toInt()
        val buffer = ShortArray(note1Samples + note2Samples)

        // Crisp 180ms studio ding-dong (E5 -> A5) precomputed once at startup for zero runtime latency
        for (i in 0 until note1Samples) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 22.0) * (1.0 - exp(-t * 360.0))
            val sample = (sin(2.0 * PI * 659.25 * t) + 0.25 * sin(2.0 * PI * 1318.51 * t)) * env
            buffer[i] = (sample * 0.50 * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        for (i in 0 until note2Samples) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 14.0) * (1.0 - exp(-t * 360.0))
            val sample = (sin(2.0 * PI * 880.00 * t) + 0.22 * sin(2.0 * PI * 1760.00 * t)) * env
            buffer[note1Samples + i] = (sample * 0.55 * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    fun refreshLanguages() {
        if (isInitialized) {
            rebuildVoiceCacheAndPublish()
            prewarmForSettings(lastKnownSettings)
        }
    }

    fun shutdown() {
        try {
            speakingTimeoutJob?.cancel()
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
