package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class OwnVoiceRecorder(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recorder: MediaRecorder? = null
    private var timerJob: Job? = null
    private var currentOutputFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

    fun startRecording(
        onAutoStopped: (String?) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (_isRecording.value) return
        val outputFile = File(context.filesDir, "own_voice_alert.m4a")
        if (outputFile.exists()) {
            outputFile.delete()
        }

        try {
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setMaxDuration(MAX_RECORD_DURATION_MS)
                setOutputFile(outputFile.absolutePath)
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        val saved = stopRecording()
                        onAutoStopped(saved)
                    }
                }
                prepare()
                start()
            }

            recorder = mediaRecorder
            currentOutputFile = outputFile
            _recordingSeconds.value = 0
            _isRecording.value = true

            timerJob?.cancel()
            timerJob = scope.launch {
                while (_isRecording.value) {
                    delay(1000L)
                    if (_isRecording.value) {
                        _recordingSeconds.value += 1
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting voice recording", e)
            cleanupRecorder()
            onError(e.message ?: "Unable to access microphone")
        }
    }

    fun stopRecording(): String? {
        timerJob?.cancel()
        timerJob = null
        val activeRecorder = recorder ?: return null
        val file = currentOutputFile
        recorder = null
        _isRecording.value = false

        return try {
            activeRecorder.stop()
            activeRecorder.release()
            if (file != null && file.exists() && file.length() > 1024L) {
                file.absolutePath
            } else {
                file?.delete()
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Recording stopped too early or failed: ${e.message}")
            runCatching { activeRecorder.release() }
            file?.delete()
            null
        }
    }

    fun importAudioFromUri(uri: Uri): String? {
        return try {
            val destFile = File(context.filesDir, "custom_imported_voice")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            if (destFile.exists() && destFile.length() > 256L) {
                destFile.absolutePath
            } else {
                destFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed importing custom audio clip", e)
            null
        }
    }

    fun deleteClip(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val file = File(path)
            if (file.exists()) file.delete()
        }
    }

    private fun cleanupRecorder() {
        timerJob?.cancel()
        timerJob = null
        runCatching { recorder?.release() }
        recorder = null
        _isRecording.value = false
    }

    companion object {
        private const val TAG = "OwnVoiceRecorder"
        private const val MAX_RECORD_DURATION_MS = 15_000
    }
}
