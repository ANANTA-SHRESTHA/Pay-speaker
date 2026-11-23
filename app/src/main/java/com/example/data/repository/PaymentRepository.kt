package com.example.data.repository

import com.example.data.local.PaymentAlertDao
import com.example.data.local.PaymentAlertEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class PaymentRepository(private val dao: PaymentAlertDao) {

    val allAlerts: Flow<List<PaymentAlertEntity>> = dao.observeAllAlerts()

    fun recentAlerts(limit: Int = 5): Flow<List<PaymentAlertEntity>> =
        dao.observeRecentAlerts(limit)

    fun observeTodayTotal(): Flow<Double> =
        dao.observeTodayTotal(startOfTodayMillis())

    fun observeTodayCount(): Flow<Int> =
        dao.observeTodayCount(startOfTodayMillis())

    suspend fun recordAlert(alert: PaymentAlertEntity): Long = dao.insertAlert(alert)

    suspend fun deleteAlert(id: Long) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAll()

    private fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
