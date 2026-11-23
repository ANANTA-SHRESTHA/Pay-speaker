package com.example.domain

import com.example.data.local.PaymentAlertEntity
import com.example.data.preferences.PaymentChannel
import java.util.Calendar
import java.util.Locale

enum class TransactionSortOption(val label: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    AMOUNT_HIGH_TO_LOW("Highest Amount"),
    AMOUNT_LOW_TO_HIGH("Lowest Amount"),
    SENDER_A_TO_Z("Sender (A–Z)"),
}

enum class DateFilterOption(val label: String) {
    ALL_TIME("All Time"),
    TODAY("Today"),
    LAST_7_DAYS("Last 7 Days"),
    THIS_MONTH("This Month"),
}

object TransactionHistoryFilter {

    fun apply(
        alerts: List<PaymentAlertEntity>,
        query: String,
        channelFilter: PaymentChannel?,
        dateFilter: DateFilterOption,
        sortOption: TransactionSortOption,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<PaymentAlertEntity> {
        val trimmed = query.trim().lowercase(Locale.ROOT)
        val minTimestamp = startTimestampFor(dateFilter, nowMillis)

        val filtered = alerts.filter { alert ->
            val matchesChannel = channelFilter == null || alert.channelName == channelFilter.name
            val matchesDate = minTimestamp == null || alert.timestamp >= minTimestamp
            val matchesQuery = trimmed.isEmpty() ||
                alert.senderName.orEmpty().lowercase(Locale.ROOT).contains(trimmed) ||
                alert.sourceLabel.lowercase(Locale.ROOT).contains(trimmed) ||
                alert.formattedAmount.lowercase(Locale.ROOT).contains(trimmed) ||
                alert.amount.toString().contains(trimmed) ||
                alert.rawMessage.lowercase(Locale.ROOT).contains(trimmed)
            matchesChannel && matchesDate && matchesQuery
        }

        return when (sortOption) {
            TransactionSortOption.NEWEST_FIRST -> filtered.sortedByDescending { it.timestamp }
            TransactionSortOption.OLDEST_FIRST -> filtered.sortedBy { it.timestamp }
            TransactionSortOption.AMOUNT_HIGH_TO_LOW -> filtered.sortedWith(
                compareByDescending<PaymentAlertEntity> { it.amount }.thenByDescending { it.timestamp }
            )
            TransactionSortOption.AMOUNT_LOW_TO_HIGH -> filtered.sortedWith(
                compareBy<PaymentAlertEntity> { it.amount }.thenByDescending { it.timestamp }
            )
            TransactionSortOption.SENDER_A_TO_Z -> filtered.sortedWith(
                compareBy<PaymentAlertEntity>(
                    { (it.senderName ?: it.sourceLabel).lowercase(Locale.ROOT) },
                    { -it.timestamp },
                )
            )
        }
    }

    fun startTimestampFor(dateFilter: DateFilterOption, nowMillis: Long): Long? {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return when (dateFilter) {
            DateFilterOption.ALL_TIME -> null
            DateFilterOption.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            DateFilterOption.LAST_7_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -6)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            DateFilterOption.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
        }
    }
}
