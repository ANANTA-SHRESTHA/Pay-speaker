package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentAlertDao {

    @Query("SELECT * FROM payment_alerts ORDER BY timestamp DESC")
    fun observeAllAlerts(): Flow<List<PaymentAlertEntity>>

    @Query("SELECT * FROM payment_alerts ORDER BY timestamp DESC")
    suspend fun getAllAlertsSnapshot(): List<PaymentAlertEntity>

    @Query("SELECT * FROM payment_alerts ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentAlerts(limit: Int): Flow<List<PaymentAlertEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM payment_alerts WHERE timestamp >= :startOfDayMillis")
    fun observeTodayTotal(startOfDayMillis: Long): Flow<Double>

    @Query("SELECT COUNT(*) FROM payment_alerts WHERE timestamp >= :startOfDayMillis")
    fun observeTodayCount(startOfDayMillis: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: PaymentAlertEntity): Long

    @Query("DELETE FROM payment_alerts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM payment_alerts")
    suspend fun clearAll()
}
