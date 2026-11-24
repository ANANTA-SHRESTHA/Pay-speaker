package com.example.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.PaySpeakerApp
import com.example.domain.DailySummaryCalculator
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.first

class DailySummaryWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? PaySpeakerApp ?: return Result.failure()
        val container = app.container

        val settings = container.preferencesRepository.settingsFlow.first()
        val forceRun = inputData.getBoolean(KEY_FORCE_RUN, false)
        if (!forceRun && !settings.dailySummaryEnabled) {
            return Result.success()
        }

        val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        val alerts = if (!uid.isNullOrBlank()) {
            val remoteResult = container.firestorePaymentRepository.fetchAllAlertsOnce(uid)
            val remoteAlerts = remoteResult.getOrNull()?.map { it.toEntity() }
            if (remoteAlerts != null) {
                remoteAlerts
            } else {
                Log.w(TAG, "Cloud fetch unavailable for daily summary; falling back to local ledger.")
                container.paymentRepository.getAllAlertsSnapshot()
            }
        } else {
            container.paymentRepository.getAllAlertsSnapshot()
        }

        val report = DailySummaryCalculator.calculate(alerts)
        DailySummaryNotifier.showDailySummary(
            context = applicationContext,
            report = report,
            merchantName = settings.merchantName,
        )

        return Result.success()
    }

    companion object {
        private const val TAG = "DailySummaryWorker"
        const val KEY_FORCE_RUN = "key_force_run"
    }
}
