package com.example

import com.example.data.local.PaymentAlertEntity
import com.example.data.preferences.PaymentChannel
import com.example.domain.StatementExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementExporterTest {

    private fun sampleAlerts(): List<PaymentAlertEntity> = listOf(
        PaymentAlertEntity(
            id = 1L,
            amount = 3500.0,
            currency = "NPR",
            formattedAmount = "3,500",
            senderName = "Ananta Shrest",
            channelName = PaymentChannel.ESEWA.name,
            sourceLabel = "eSewa",
            packageName = "com.android.mms",
            rawTitle = "CTZN-ALERT",
            rawMessage = "Credited by NPR 3,500.00, Remarks: Ananta Shrest",
            spokenText = "तीन हजार पाँच सय रुपैयाँ प्राप्त भयो",
            wasSpoken = true,
            isSimulated = false,
            timestamp = 1_700_000_000_000L,
        ),
        PaymentAlertEntity(
            id = 2L,
            amount = 4000.0,
            currency = "NPR",
            formattedAmount = "4,000",
            senderName = null,
            channelName = PaymentChannel.BANK_ALERT.name,
            sourceLabel = "Bank & SMS",
            packageName = "com.android.mms",
            rawTitle = "Laxmi Sunrise",
            rawMessage = "Credited by NPR 4,000.00",
            spokenText = "चार हजार रुपैयाँ प्राप्त भयो",
            wasSpoken = true,
            isSimulated = false,
            timestamp = 1_700_000_100_000L,
        ),
        PaymentAlertEntity(
            id = 3L,
            amount = 1.0,
            currency = "NPR",
            formattedAmount = "1",
            senderName = "9864843502",
            channelName = PaymentChannel.FONEPAY.name,
            sourceLabel = "Fonepay",
            packageName = "com.android.mms",
            rawTitle = "Fonepay",
            rawMessage = "Rs. 1.00 received from 9864843502",
            spokenText = "एक रुपैयाँ प्राप्त भयो",
            wasSpoken = true,
            isSimulated = true,
            timestamp = 1_700_000_200_000L,
        ),
    )

    @Test
    fun buildsAccurateStatementSummaryAndChannelBreakdown() {
        val alerts = sampleAlerts()
        val summary = StatementExporter.buildStatementSummary(
            alerts = alerts.take(2),
            overallBalance = 7501.0,
            merchantName = "Himalayan Cafe",
            accountEmail = "merchant@example.com",
            periodLabel = "This Month",
            channelLabel = "All Apps",
        )

        assertEquals("Himalayan Cafe", summary.merchantName)
        assertEquals(7500.0, summary.statementTotal, 0.001)
        assertEquals(7501.0, summary.overallBalance, 0.001)
        assertEquals(2, summary.transactionCount)
        assertEquals("Bank & SMS", summary.channelBreakdown.first().sourceLabel)
        assertEquals(4000.0, summary.channelBreakdown.first().totalAmount, 0.001)
    }

    @Test
    fun buildsEscapedCsvStatementWithTotals() {
        val alerts = sampleAlerts()
        val summary = StatementExporter.buildStatementSummary(
            alerts = alerts,
            overallBalance = 7501.0,
            merchantName = "Cafe, \"Nepal\"",
            accountEmail = "merchant@example.com",
            periodLabel = "All Time",
            channelLabel = "All Apps",
        )

        val csv = StatementExporter.buildCsvContent(alerts, summary)

        assertTrue(csv.contains("\"Cafe, \"\"Nepal\"\"\""))
        assertTrue(csv.contains("Total Account Balance,NPR 7,501"))
        assertTrue(csv.contains("3500.00"))
        assertTrue(csv.contains("9864843502"))
        assertTrue(csv.contains("Total Credits,7501.00"))
    }
}
