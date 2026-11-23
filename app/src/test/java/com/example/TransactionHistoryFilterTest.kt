package com.example

import com.example.data.local.PaymentAlertEntity
import com.example.data.preferences.PaymentChannel
import com.example.domain.DateFilterOption
import com.example.domain.TransactionHistoryFilter
import com.example.domain.TransactionSortOption
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class TransactionHistoryFilterTest {

    private val baseNow: Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 23, 15, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun makeAlert(
        id: Long,
        amount: Double,
        sender: String?,
        channel: PaymentChannel,
        daysAgo: Int,
        hoursAgo: Int = 0,
    ): PaymentAlertEntity {
        val ts = baseNow - daysAgo * 86_400_000L - hoursAgo * 3_600_000L
        return PaymentAlertEntity(
            id = id,
            amount = amount,
            currency = "NPR",
            formattedAmount = amount.toInt().toString(),
            senderName = sender,
            channelName = channel.name,
            sourceLabel = channel.displayName,
            packageName = "com.example.test",
            rawTitle = "Payment",
            rawMessage = "Received $amount",
            spokenText = "Received $amount",
            wasSpoken = true,
            isSimulated = false,
            timestamp = ts,
        )
    }

    private val sampleAlerts = listOf(
        makeAlert(1, 500.0, "Ramesh", PaymentChannel.ESEWA, daysAgo = 0, hoursAgo = 1),
        makeAlert(2, 2400.0, "Anita", PaymentChannel.KHALTI, daysAgo = 2),
        makeAlert(3, 120.0, "Bikash", PaymentChannel.FONEPAY, daysAgo = 10),
        makeAlert(4, 5000.0, "Sita", PaymentChannel.ESEWA, daysAgo = 40),
    )

    @Test
    fun sortsByNewestOldestAmountAndSender() {
        val newest = TransactionHistoryFilter.apply(
            alerts = sampleAlerts,
            query = "",
            channelFilter = null,
            dateFilter = DateFilterOption.ALL_TIME,
            sortOption = TransactionSortOption.NEWEST_FIRST,
            nowMillis = baseNow,
        )
        assertEquals(listOf(1L, 2L, 3L, 4L), newest.map { it.id })

        val oldest = TransactionHistoryFilter.apply(
            alerts = sampleAlerts,
            query = "",
            channelFilter = null,
            dateFilter = DateFilterOption.ALL_TIME,
            sortOption = TransactionSortOption.OLDEST_FIRST,
            nowMillis = baseNow,
        )
        assertEquals(listOf(4L, 3L, 2L, 1L), oldest.map { it.id })

        val highToLow = TransactionHistoryFilter.apply(
            alerts = sampleAlerts,
            query = "",
            channelFilter = null,
            dateFilter = DateFilterOption.ALL_TIME,
            sortOption = TransactionSortOption.AMOUNT_HIGH_TO_LOW,
            nowMillis = baseNow,
        )
        assertEquals(listOf(4L, 2L, 1L, 3L), highToLow.map { it.id })

        val lowToHigh = TransactionHistoryFilter.apply(
            alerts = sampleAlerts,
            query = "",
            channelFilter = null,
            dateFilter = DateFilterOption.ALL_TIME,
            sortOption = TransactionSortOption.AMOUNT_LOW_TO_HIGH,
            nowMillis = baseNow,
        )
        assertEquals(listOf(3L, 1L, 2L, 4L), lowToHigh.map { it.id })

        val bySender = TransactionHistoryFilter.apply(
            alerts = sampleAlerts,
            query = "",
            channelFilter = null,
            dateFilter = DateFilterOption.ALL_TIME,
            sortOption = TransactionSortOption.SENDER_A_TO_Z,
            nowMillis = baseNow,
        )
        assertEquals(listOf(2L, 3L, 1L, 4L), bySender.map { it.id })
    }

    @Test
    fun filtersByDateRangeChannelAndSearchQuery() {
        val today = TransactionHistoryFilter.apply(
            alerts = sampleAlerts,
            query = "",
            channelFilter = null,
            dateFilter = DateFilterOption.TODAY,
            sortOption = TransactionSortOption.NEWEST_FIRST,
            nowMillis = baseNow,
        )
        assertEquals(listOf(1L), today.map { it.id })

        val last7Days = TransactionHistoryFilter.apply(
            alerts = sampleAlerts,
            query = "",
            channelFilter = null,
            dateFilter = DateFilterOption.LAST_7_DAYS,
            sortOption = TransactionSortOption.NEWEST_FIRST,
            nowMillis = baseNow,
        )
        assertEquals(listOf(1L, 2L), last7Days.map { it.id })

        val thisMonth = TransactionHistoryFilter.apply(
            alerts = sampleAlerts,
            query = "",
            channelFilter = null,
            dateFilter = DateFilterOption.THIS_MONTH,
            sortOption = TransactionSortOption.NEWEST_FIRST,
            nowMillis = baseNow,
        )
        assertEquals(listOf(1L, 2L, 3L), thisMonth.map { it.id })

        val esewaSearch = TransactionHistoryFilter.apply(
            alerts = sampleAlerts,
            query = "sita",
            channelFilter = PaymentChannel.ESEWA,
            dateFilter = DateFilterOption.ALL_TIME,
            sortOption = TransactionSortOption.NEWEST_FIRST,
            nowMillis = baseNow,
        )
        assertEquals(listOf(4L), esewaSearch.map { it.id })
    }
}
