package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.local.PaymentAlertEntity
import com.example.data.preferences.SoundboxSettings
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestorePaymentRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireSignedInUid(expectedUid: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        if (expectedUid != null) {
            check(uid == expectedUid) { "Signed-in user changed; refusing cross-user Firestore operation." }
        }
        return uid
    }

    fun observeAlerts(userId: String): Flow<List<FirestorePaymentAlert>> {
        require(userId.isNotBlank()) { "userId must not be blank when observing payment alerts." }
        val colPath = "users/$userId/payment_alerts"
        return db.collection("users")
            .document(userId)
            .collection("payment_alerts")
            .orderBy("clientTimestamp", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestorePaymentAlert::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, colPath)
                }
                throw error
            }
    }

    suspend fun fetchAllAlertsOnce(userId: String): Result<List<FirestorePaymentAlert>> {
        val colPath = "users/$userId/payment_alerts"
        return try {
            val uid = requireSignedInUid(userId)
            val snapshot = db.collection("users")
                .document(uid)
                .collection("payment_alerts")
                .orderBy("clientTimestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            val list = snapshot.toObjects(
                FirestorePaymentAlert::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colPath)
            Result.failure(e)
        }
    }

    suspend fun addAlert(userId: String, alert: PaymentAlertEntity): Result<String> {
        var docPath = "users/$userId/payment_alerts"
        return try {
            val uid = requireSignedInUid(userId)
            require(alert.amount > 0.0 && alert.amount <= 1_000_000_000.0) {
                "Payment amount must be positive and within bounds."
            }
            val docRef = db.collection("users")
                .document(uid)
                .collection("payment_alerts")
                .document()
            docPath = docRef.path
            val payload = FirestorePaymentAlert.toCreateMap(uid, alert)
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addAlertIfSignedIn(alert: PaymentAlertEntity): Result<String>? {
        val uid = auth.currentUser?.uid ?: return null
        return addAlert(uid, alert)
    }

    suspend fun deleteAlert(userId: String, alertDocId: String): Result<Unit> {
        var docPath = "users/$userId/payment_alerts/$alertDocId"
        return try {
            val uid = requireSignedInUid(userId)
            require(alertDocId.isNotBlank()) { "Document ID must not be blank for delete." }
            val docRef = db.collection("users")
                .document(uid)
                .collection("payment_alerts")
                .document(alertDocId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearAllAlerts(userId: String): Result<Unit> {
        val colPath = "users/$userId/payment_alerts"
        return try {
            val uid = requireSignedInUid(userId)
            val snapshot = db.collection("users")
                .document(uid)
                .collection("payment_alerts")
                .get()
                .await()

            val docs = snapshot.documents
            for (chunk in docs.chunked(400)) {
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colPath)
            Result.failure(e)
        }
    }

    suspend fun fetchMerchantSettings(userId: String): Result<FirestoreMerchantSettings?> {
        var docPath = "users/$userId/merchant_settings/$DEFAULT_SETTINGS_DOC_ID"
        return try {
            val uid = requireSignedInUid(userId)
            val docRef = db.collection("users")
                .document(uid)
                .collection("merchant_settings")
                .document(DEFAULT_SETTINGS_DOC_ID)
            docPath = docRef.path
            val snapshot = docRef.get().await()
            val parsed = snapshot.toObject(
                FirestoreMerchantSettings::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            Result.success(parsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveMerchantSettings(userId: String, settings: SoundboxSettings): Result<Unit> {
        var docPath = "users/$userId/merchant_settings/$DEFAULT_SETTINGS_DOC_ID"
        return try {
            val uid = requireSignedInUid(userId)
            val docRef = db.collection("users")
                .document(uid)
                .collection("merchant_settings")
                .document(DEFAULT_SETTINGS_DOC_ID)
            docPath = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(FirestoreMerchantSettings.toMutableFieldsMap(settings)).await()
            } else {
                docRef.set(FirestoreMerchantSettings.toCreateMap(uid, settings)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        private const val DEFAULT_SETTINGS_DOC_ID = "default"
    }
}
