package com.example

import com.example.data.local.PaymentAlertEntity
import com.example.data.preferences.PaymentChannel
import com.example.domain.DailySummaryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DailySummaryCalculatorTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun utcMillis(year: Int, monthZeroBased: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance(utc).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun sampleAlert(
        amount: Double,
        channel: PaymentChannel,
        timestamp: Long,
    ) = PaymentAlertEntity(
        amount = amount,
        currency = "NPR",
        formattedAmount = amount.toString(),
        senderName = "Test Sender",
        channelName = channel.name,
        sourceLabel = channel.displayName,
        packageName = "com.test",
        rawTitle = "Payment",
        rawMessage = "Received",
        spokenText = "Received",
        wasSpoken = true,
        isSimulated = false,
        timestamp = timestamp,
    )

    @Test
    fun `calculates day total and overall cumulative balance separately`() {
        val now = utcMillis(2026, Calendar.NOVEMBER, 23, 20, 30)
        val alerts = listOf(
            sampleAlert(1500.0, PaymentChannel.ESEWA, utcMillis(2026, Calendar.NOVEMBER, 23, 9, 15)),
            sampleAlert(2500.0, PaymentChannel.FONEPAY, utcMillis(2026, Calendar.NOVEMBER, 23, 18, 45)),
            sampleAlert(500.0, PaymentChannel.ESEWA, utcMillis(2026, Calendar.NOVEMBER, 23, 19, 10)),
            sampleAlert(10000.0, PaymentChannel.BANK_ALERT, utcMillis(2026, Calendar.NOVEMBER, 20, 14, 0)),
        )

        val report = DailySummaryCalculator.calculate(alerts, nowMillis = now, timeZone = utc)

        assertEquals(4500.0, report.dayTotal, 0.01)
        assertEquals(3, report.dayTransactionCount)
        assertEquals(14500.0, report.totalBalance, 0.01)
        assertEquals(4, report.totalTransactionCount)
        assertEquals("Fonepay", report.topChannelLabel)
        assertEquals("Nov 23, 2026", report.dayDateLabel)
    }

    @Test
    fun `handles zero transactions today while preserving total balance`() {
        val now = utcMillis(2026, Calendar.NOVEMBER, 23, 21, 0)
        val alerts = listOf(
            sampleAlert(4000.0, PaymentChannel.BANK_ALERT, utcMillis(2026, Calendar.NOVEMBER, 21, 12, 0)),
        )

        val report = DailySummaryCalculator.calculate(alerts, nowMillis = now, timeZone = utc)

        assertEquals(0.0, report.dayTotal, 0.01)
        assertEquals(0, report.dayTransactionCount)
        assertEquals(4000.0, report.totalBalance, 0.01)
        assertEquals(1, report.totalTransactionCount)
        assertNull(report.topChannelLabel)
    }

    @Test
    fun `initial delay targets same day when scheduled time is in future`() {
        val now = utcMillis(2026, Calendar.NOVEMBER, 23, 19, 0)
        val delay = DailySummaryCalculator.computeInitialDelayMillis(
            nowMillis = now,
            targetHour = 21,
            targetMinute = 0,
            timeZone = utc,
        )
        assertEquals(2 * 60 * 60 * 1000L, delay)
    }

    @Test
    fun `initial delay rolls to next day when scheduled time has passed`() {
        val now = utcMillis(2026, Calendar.NOVEMBER, 23, 22, 0)
        val delay = DailySummaryCalculator.computeInitialDelayMillis(
            nowMillis = now,
            targetHour = 21,
            targetMinute = 0,
            timeZone = utc,
        )
        assertEquals(23 * 60 * 60 * 1000L, delay)
    }

    @Test
    fun `formats 12 hour schedule time cleanly`() {
        assertEquals("9:00 PM", DailySummaryCalculator.formatScheduleTime(21, 0))
        assertEquals("12:00 AM", DailySummaryCalculator.formatScheduleTime(0, 0))
        assertEquals("12:30 PM", DailySummaryCalculator.formatScheduleTime(12, 30))
        assertEquals("8:05 AM", DailySummaryCalculator.formatScheduleTime(8, 5))
    }
}
