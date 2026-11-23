package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payment_alerts")
data class PaymentAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val currency: String,
    val formattedAmount: String,
    val senderName: String?,
    val channelName: String,
    val sourceLabel: String,
    val packageName: String,
    val rawTitle: String,
    val rawMessage: String,
    val spokenText: String,
    val wasSpoken: Boolean,
    val isSimulated: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
)
