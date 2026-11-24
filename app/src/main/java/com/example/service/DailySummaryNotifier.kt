package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.DailySummaryReport
import com.example.domain.PaymentNotificationParser

object DailySummaryNotifier {

    const val CHANNEL_ID = "payspeaker_daily_summary"
    const val NOTIFICATION_ID = 2001
    const val EXTRA_OPEN_HISTORY = "extra_open_history"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.daily_summary_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.daily_summary_channel_desc)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun showDailySummary(
        context: Context,
        report: DailySummaryReport,
        merchantName: String,
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        ensureChannel(context)

        val formattedDayTotal = PaymentNotificationParser.formatAmount(report.dayTotal)
        val formattedBalance = PaymentNotificationParser.formatAmount(report.totalBalance)
        val headerPrefix = merchantName.trim().takeIf { it.isNotEmpty() } ?: "PaySpeaker"

        val title = if (report.dayTransactionCount > 0) {
            "$headerPrefix Daily Summary • Rs. $formattedDayTotal"
        } else {
            "$headerPrefix Daily Summary • No payments today"
        }

        val txWord = if (report.dayTransactionCount == 1) "transaction" else "transactions"
        val shortSummary = "${report.dayTransactionCount} $txWord today • Total Balance: NPR $formattedBalance"

        val expandedText = buildString {
            append("Date: ${report.dayDateLabel}\n")
            append("Today's Received: Rs. $formattedDayTotal (${report.dayTransactionCount} $txWord)\n")
            append("Total Balance: NPR $formattedBalance (${report.totalTransactionCount} total credits)")
            if (!report.topChannelLabel.isNullOrBlank()) {
                append("\nTop Channel Today: ${report.topChannelLabel}")
            }
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_HISTORY, true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(shortSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
