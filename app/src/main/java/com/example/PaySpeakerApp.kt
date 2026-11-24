package com.example

import android.app.Application
import com.example.ads.AdMobManager
import com.example.audio.OwnVoiceRecorder
import com.example.audio.PaymentSpeakerEngine
import com.example.data.local.AppDatabase
import com.example.data.preferences.SoundboxPreferencesRepository
import com.example.data.remote.FirestorePaymentRepository
import com.example.data.repository.PaymentRepository
import com.example.service.DailySummaryNotifier
import com.example.service.DailySummaryScheduler
import com.example.service.SoundboxStateHolder
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(application: Application) {
    private val database: AppDatabase by lazy { AppDatabase.build(application) }

    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(
            application.getString(R.string.firestore_database_id)
        )
    }

    val paymentRepository: PaymentRepository by lazy {
        PaymentRepository(database.paymentAlertDao())
    }

    val firestorePaymentRepository: FirestorePaymentRepository by lazy {
        FirestorePaymentRepository(firestore)
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

    val adMobManager: AdMobManager by lazy {
        AdMobManager()
    }
}

class PaySpeakerApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DailySummaryNotifier.ensureChannel(this)
        container.adMobManager.initialize(this)
        // Warm up TTS engine and pre-cache settings/voice pipeline immediately on process start
        container.speakerEngine
        var scheduleSynced = false
        appScope.launch {
            container.preferencesRepository.settingsFlow.collect { settings ->
                container.speakerEngine.prewarmForSettings(settings)
                if (!scheduleSynced) {
                    scheduleSynced = true
                    DailySummaryScheduler.syncSchedule(
                        context = this@PaySpeakerApp,
                        settings = settings,
                        replaceExisting = false,
                    )
                }
            }
        }
    }
}
