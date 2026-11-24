package com.example.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.preferences.SoundboxSettings
import com.example.domain.DailySummaryCalculator
import java.util.concurrent.TimeUnit

object DailySummaryScheduler {

    const val UNIQUE_WORK_NAME = "payspeaker_daily_summary_work"
    private const val WORK_TAG = "daily_summary_tag"

    fun syncSchedule(
        context: Context,
        settings: SoundboxSettings,
        replaceExisting: Boolean = false,
    ) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!settings.dailySummaryEnabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val delayMillis = DailySummaryCalculator.computeInitialDelayMillis(
            nowMillis = System.currentTimeMillis(),
            targetHour = settings.dailySummaryHour,
            targetMinute = settings.dailySummaryMinute,
        )

        val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .build()

        val policy = if (replaceExisting) {
            ExistingPeriodicWorkPolicy.UPDATE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
        }

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            policy,
            request,
        )
    }

    fun triggerImmediateSummary(context: Context) {
        val request = OneTimeWorkRequestBuilder<DailySummaryWorker>()
            .setInputData(workDataOf(DailySummaryWorker.KEY_FORCE_RUN to true))
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }
}
