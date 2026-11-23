package com.example

import android.app.Application
import com.example.audio.OwnVoiceRecorder
import com.example.audio.PaymentSpeakerEngine
import com.example.data.local.AppDatabase
import com.example.data.preferences.SoundboxPreferencesRepository
import com.example.data.repository.PaymentRepository
import com.example.service.SoundboxStateHolder

class AppContainer(application: Application) {
    private val database: AppDatabase by lazy { AppDatabase.build(application) }

    val paymentRepository: PaymentRepository by lazy {
        PaymentRepository(database.paymentAlertDao())
    }

    val preferencesRepository: SoundboxPreferencesRepository by lazy {
        SoundboxPreferencesRepository(application)
    }

    val stateHolder: SoundboxStateHolder by lazy { SoundboxStateHolder() }

    val speakerEngine: PaymentSpeakerEngine by lazy {
        PaymentSpeakerEngine(application, stateHolder)
    }

    val ownVoiceRecorder: OwnVoiceRecorder by lazy {
        OwnVoiceRecorder(application)
    }
}

class PaySpeakerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Warm up TTS engine on app launch
        container.speakerEngine
    }
}
