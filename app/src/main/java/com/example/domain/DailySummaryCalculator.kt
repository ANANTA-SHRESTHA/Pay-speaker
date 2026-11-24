package com.example.domain

import com.example.data.local.PaymentAlertEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DailySummaryReport(
    val totalBalance: Double,
    val totalTransactionCount: Int,
    val dayTotal: Double,
    val dayTransactionCount: Int,
    val topChannelLabel: String?,
    val dayDateLabel: String,
)

object DailySummaryCalculator {

    fun calculate(
        alerts: List<PaymentAlertEntity>,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): DailySummaryReport {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDayMillis = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDayMillis = cal.timeInMillis

        val todayAlerts = alerts.filter { it.timestamp in startOfDayMillis until endOfDayMillis }
        val dayTotal = todayAlerts.sumOf { it.amount }
        val totalBalance = alerts.sumOf { it.amount }

        val topChannelLabel = todayAlerts
            .groupBy { it.sourceLabel.ifBlank { it.channelName } }
            .maxByOrNull { (_, group) -> group.sumOf { it.amount } }
            ?.key
            ?.takeIf { it.isNotBlank() }

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US).apply {
            this.timeZone = timeZone
        }

        return DailySummaryReport(
            totalBalance = totalBalance,
            totalTransactionCount = alerts.size,
            dayTotal = dayTotal,
            dayTransactionCount = todayAlerts.size,
            topChannelLabel = topChannelLabel,
            dayDateLabel = dateFormat.format(Date(nowMillis)),
        )
    }

    fun computeInitialDelayMillis(
        nowMillis: Long,
        targetHour: Int,
        targetMinute: Int,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long {
        val safeHour = targetHour.coerceIn(0, 23)
        val safeMinute = targetMinute.coerceIn(0, 59)

        val target = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, safeHour)
            set(Calendar.MINUTE, safeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return (target.timeInMillis - nowMillis).coerceAtLeast(1_000L)
    }

    fun formatScheduleTime(hour: Int, minute: Int): String {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val amPm = if (safeHour >= 12) "PM" else "AM"
        val hour12 = when {
            safeHour == 0 -> 12
            safeHour > 12 -> safeHour - 12
            else -> safeHour
        }
        return String.format(Locale.US, "%d:%02d %s", hour12, safeMinute, amPm)
    }
}
