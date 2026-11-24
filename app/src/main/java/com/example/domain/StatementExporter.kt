package com.example.domain

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.local.PaymentAlertEntity
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChannelBreakdownItem(
    val sourceLabel: String,
    val totalAmount: Double,
    val count: Int,
)

data class StatementSummary(
    val merchantName: String,
    val accountEmail: String?,
    val periodLabel: String,
    val channelLabel: String,
    val statementTotal: Double,
    val overallBalance: Double,
    val transactionCount: Int,
    val channelBreakdown: List<ChannelBreakdownItem>,
    val generatedAtMillis: Long,
)

data class StatementExportResult(
    val fileName: String,
    val mimeType: String,
    val contentUri: Uri,
    val savedLocationLabel: String,
)

object StatementExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_H = 36f
    private const val MARGIN_BOTTOM = 44f

    fun buildStatementSummary(
        alerts: List<PaymentAlertEntity>,
        overallBalance: Double,
        merchantName: String,
        accountEmail: String?,
        periodLabel: String,
        channelLabel: String,
        generatedAtMillis: Long = System.currentTimeMillis(),
    ): StatementSummary {
        val breakdown = alerts
            .groupBy { it.sourceLabel.ifBlank { "Payment" } }
            .map { (source, items) ->
                ChannelBreakdownItem(
                    sourceLabel = source,
                    totalAmount = items.sumOf { it.amount },
                    count = items.size,
                )
            }
            .sortedByDescending { it.totalAmount }

        return StatementSummary(
            merchantName = merchantName.trim().ifBlank { "PaySpeaker Merchant" },
            accountEmail = accountEmail?.trim()?.takeIf { it.isNotEmpty() },
            periodLabel = periodLabel,
            channelLabel = channelLabel,
            statementTotal = alerts.sumOf { it.amount },
            overallBalance = overallBalance,
            transactionCount = alerts.size,
            channelBreakdown = breakdown,
            generatedAtMillis = generatedAtMillis,
        )
    }

    fun buildFileName(extension: String, timestampMillis: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestampMillis))
        return "PaySpeaker_Statement_$stamp.$extension"
    }

    fun buildCsvContent(
        alerts: List<PaymentAlertEntity>,
        summary: StatementSummary,
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.US)
        val generatedFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        return buildString {
            append('\uFEFF') // UTF-8 BOM for spreadsheet apps
            appendLine("PaySpeaker Merchant Account Statement")
            appendLine("Merchant,${csvCell(summary.merchantName)}")
            if (summary.accountEmail != null) {
                appendLine("Account Email,${csvCell(summary.accountEmail)}")
            }
            appendLine("Statement Period,${csvCell(summary.periodLabel)}")
            appendLine("Channel Filter,${csvCell(summary.channelLabel)}")
            appendLine("Generated On,${csvCell(generatedFormat.format(Date(summary.generatedAtMillis)))}")
            appendLine("Statement Total,NPR ${PaymentNotificationParser.formatAmount(summary.statementTotal)}")
            appendLine("Total Account Balance,NPR ${PaymentNotificationParser.formatAmount(summary.overallBalance)}")
            appendLine()
            appendLine("No,Date,Time,Channel,Sender / Particulars,Currency,Credit Amount,Mode,Details")
            alerts.forEachIndexed { index, alert ->
                val dateStr = dateFormat.format(Date(alert.timestamp))
                val timeStr = timeFormat.format(Date(alert.timestamp))
                val particulars = alert.senderName?.takeIf { it.isNotBlank() } ?: "Direct Credit"
                val mode = if (alert.isSimulated) "Test" else "Live"
                val details = alert.rawMessage.ifBlank { alert.rawTitle }
                appendLine(
                    listOf(
                        (index + 1).toString(),
                        csvCell(dateStr),
                        csvCell(timeStr),
                        csvCell(alert.sourceLabel),
                        csvCell(particulars),
                        csvCell(alert.currency),
                        csvCell(String.format(Locale.US, "%.2f", alert.amount)),
                        csvCell(mode),
                        csvCell(details),
                    ).joinToString(",")
                )
            }
            appendLine()
            appendLine(",,,,,Total Credits,${String.format(Locale.US, "%.2f", summary.statementTotal)},,")
        }
    }

    private fun csvCell(value: String): String {
        val clean = value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        return if (clean.contains(',') || clean.contains('"')) {
            "\"${clean.replace("\"", "\"\"")}\""
        } else {
            clean
        }
    }

    fun writePdfStatement(
        outputStream: OutputStream,
        alerts: List<PaymentAlertEntity>,
        summary: StatementSummary,
    ) {
        val document = PdfDocument()
        try {
            val contentWidth = PAGE_WIDTH - (MARGIN_H * 2f)
            val dateTimeFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
            val rowDateFmt = SimpleDateFormat("dd MMM yy", Locale.US)
            val rowTimeFmt = SimpleDateFormat("hh:mm a", Locale.US)

            val headerBgPaint = Paint().apply { color = Color.rgb(6, 59, 37) }
            val accentPillPaint = Paint().apply { color = Color.rgb(209, 250, 229) }
            val summaryCardPaint = Paint().apply { color = Color.rgb(240, 249, 244) }
            val summaryBorderPaint = Paint().apply {
                color = Color.rgb(187, 230, 207)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val tableHeaderPaint = Paint().apply { color = Color.rgb(228, 244, 235) }
            val altRowPaint = Paint().apply { color = Color.rgb(248, 252, 250) }
            val dividerPaint = Paint().apply {
                color = Color.rgb(218, 232, 224)
                strokeWidth = 0.7f
            }

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(209, 250, 229)
                textSize = 10f
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(66, 94, 80)
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val valueBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(13, 31, 22)
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(24, 42, 33)
                textSize = 9.5f
            }
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(92, 118, 105)
                textSize = 8.5f
            }
            val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0, 128, 64)
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val tableHeadTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(6, 59, 37)
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val colNoX = MARGIN_H + 6f
            val colDateX = MARGIN_H + 34f
            val colSenderX = MARGIN_H + 148f
            val colChannelX = MARGIN_H + 340f
            val colAmountX = PAGE_WIDTH - MARGIN_H - 8f

            fun drawTableHeader(canvas: Canvas, topY: Float): Float {
                val rect = RectF(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + 24f)
                canvas.drawRoundRect(rect, 4f, 4f, tableHeaderPaint)
                val textY = topY + 15.5f
                canvas.drawText("#", colNoX, textY, tableHeadTextPaint)
                canvas.drawText("DATE & TIME", colDateX, textY, tableHeadTextPaint)
                canvas.drawText("PARTICULARS / SENDER", colSenderX, textY, tableHeadTextPaint)
                canvas.drawText("CHANNEL", colChannelX, textY, tableHeadTextPaint)
                val amtHead = Paint(tableHeadTextPaint).apply { textAlign = Paint.Align.RIGHT }
                canvas.drawText("CREDIT (NPR)", colAmountX, textY, amtHead)
                return topY + 26f
            }

            fun drawFooter(canvas: Canvas, pageNum: Int) {
                val y = PAGE_HEIGHT - 22f
                canvas.drawLine(MARGIN_H, y - 10f, PAGE_WIDTH - MARGIN_H, y - 10f, dividerPaint)
                canvas.drawText(
                    "PaySpeaker Digital Soundbox • Merchant Account Statement",
                    MARGIN_H,
                    y,
                    mutedPaint,
                )
                val pagePaint = Paint(mutedPaint).apply { textAlign = Paint.Align.RIGHT }
                canvas.drawText("Page $pageNum", PAGE_WIDTH - MARGIN_H, y, pagePaint)
            }

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas

            // Page 1 Header Banner
            val bannerRect = RectF(0f, 0f, PAGE_WIDTH.toFloat(), 96f)
            canvas.drawRect(bannerRect, headerBgPaint)
            canvas.drawText("PaySpeaker Account Statement", MARGIN_H, 36f, titlePaint)
            canvas.drawText(
                "${summary.merchantName}${summary.accountEmail?.let { " • $it" } ?: ""}",
                MARGIN_H,
                56f,
                subTitlePaint,
            )
            canvas.drawText(
                "Generated: ${dateTimeFmt.format(Date(summary.generatedAtMillis))}  |  Period: ${summary.periodLabel} (${summary.channelLabel})",
                MARGIN_H,
                74f,
                subTitlePaint,
            )

            // Summary Box
            var y = 112f
            val summaryRect = RectF(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + 74f)
            canvas.drawRoundRect(summaryRect, 10f, 10f, summaryCardPaint)
            canvas.drawRoundRect(summaryRect, 10f, 10f, summaryBorderPaint)

            val col2X = MARGIN_H + contentWidth * 0.36f
            val col3X = MARGIN_H + contentWidth * 0.72f
            canvas.drawText("TOTAL BALANCE", MARGIN_H + 14f, y + 22f, labelPaint)
            canvas.drawText(
                "Rs. ${PaymentNotificationParser.formatAmount(summary.overallBalance)}",
                MARGIN_H + 14f,
                y + 44f,
                valueBoldPaint,
            )

            canvas.drawText("STATEMENT CREDITS", col2X, y + 22f, labelPaint)
            canvas.drawText(
                "Rs. ${PaymentNotificationParser.formatAmount(summary.statementTotal)}",
                col2X,
                y + 44f,
                valueBoldPaint,
            )

            canvas.drawText("TRANSACTIONS", col3X, y + 22f, labelPaint)
            canvas.drawText("${summary.transactionCount} Credits", col3X, y + 44f, valueBoldPaint)

            if (summary.channelBreakdown.isNotEmpty()) {
                val breakdownLine = summary.channelBreakdown.take(4).joinToString("  •  ") {
                    "${it.sourceLabel}: Rs. ${PaymentNotificationParser.formatAmount(it.totalAmount)} (${it.count})"
                }
                canvas.drawText(breakdownLine, MARGIN_H + 14f, y + 63f, mutedPaint)
            }

            y += 90f
            y = drawTableHeader(canvas, y)

            val rowHeight = 28f
            alerts.forEachIndexed { index, alert ->
                if (y + rowHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                    drawFooter(canvas, pageNumber)
                    document.finishPage(page)

                    pageNumber += 1
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas

                    // Compact header on continuation pages
                    canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 44f, headerBgPaint)
                    val contTitle = Paint(titlePaint).apply { textSize = 12f }
                    canvas.drawText(
                        "PaySpeaker Statement — ${summary.merchantName} (cont.)",
                        MARGIN_H,
                        27f,
                        contTitle,
                    )
                    y = drawTableHeader(canvas, 58f)
                }

                if (index % 2 == 1) {
                    canvas.drawRect(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + rowHeight, altRowPaint)
                }

                val date = Date(alert.timestamp)
                val primaryTextY = y + 12f
                val subTextY = y + 22.5f

                canvas.drawText("${index + 1}", colNoX, primaryTextY + 3f, bodyPaint)
                canvas.drawText(rowDateFmt.format(date), colDateX, primaryTextY, bodyPaint)
                canvas.drawText(rowTimeFmt.format(date), colDateX, subTextY, mutedPaint)

                val senderDisplay = alert.senderName?.takeIf { it.isNotBlank() } ?: "Direct Credit"
                val truncatedSender = if (senderDisplay.length > 30) {
                    senderDisplay.take(29) + "…"
                } else {
                    senderDisplay
                }
                canvas.drawText(truncatedSender, colSenderX, primaryTextY, bodyPaint)
                val tagText = if (alert.isSimulated) "Test Alert" else "Verified Alert"
                canvas.drawText(tagText, colSenderX, subTextY, mutedPaint)

                val channelText = alert.sourceLabel.take(16)
                canvas.drawText(channelText, colChannelX, primaryTextY + 3f, bodyPaint)

                val amountText = "+ ${PaymentNotificationParser.formatAmount(alert.amount)}"
                canvas.drawText(amountText, colAmountX, primaryTextY + 3f, amountPaint)

                y += rowHeight
                canvas.drawLine(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y, dividerPaint)
            }

            drawFooter(canvas, pageNumber)
            document.finishPage(page)
            document.writeTo(outputStream)
        } finally {
            document.close()
        }
    }

    fun exportToDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit,
    ): StatementExportResult {
        // Always write a shareable/openable cache copy for FileProvider
        val cacheDir = File(context.cacheDir, "statements").apply { mkdirs() }
        val cacheFile = File(cacheDir, fileName)
        cacheFile.outputStream().use { out ->
            writer(out)
        }
        val providerUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/PaySpeaker")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val insertedUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (insertedUri != null) {
                try {
                    resolver.openOutputStream(insertedUri)?.use { out ->
                        cacheFile.inputStream().use { input -> input.copyTo(out) }
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(insertedUri, values, null, null)
                    return StatementExportResult(
                        fileName = fileName,
                        mimeType = mimeType,
                        contentUri = providerUri,
                        savedLocationLabel = "Saved to Downloads/PaySpeaker/$fileName",
                    )
                } catch (_: Exception) {
                    runCatching { resolver.delete(insertedUri, null, null) }
                }
            }
        }

        return StatementExportResult(
            fileName = fileName,
            mimeType = mimeType,
            contentUri = providerUri,
            savedLocationLabel = "Statement ready: $fileName",
        )
    }

    fun exportToPickedUri(
        context: Context,
        targetUri: Uri,
        fileName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit,
    ): StatementExportResult {
        context.contentResolver.openOutputStream(targetUri)?.use { out ->
            writer(out)
        } ?: error("Unable to open destination file.")

        return StatementExportResult(
            fileName = fileName,
            mimeType = mimeType,
            contentUri = targetUri,
            savedLocationLabel = "Saved: $fileName",
        )
    }
}
