package com.example.data.remote

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?,
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = Firebase.auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

fun friendlyFirestoreMessage(error: Throwable): String {
    val causes = generateSequence(error) { it.cause }
    val firestoreError = causes.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    val rawMessage = firestoreError?.message ?: error.message.orEmpty()
    return when {
        rawMessage.contains("Quota exceeded", ignoreCase = true) ->
            "Cloud sync quota exceeded for today. Your local ledger still works and cloud quota resets tomorrow."
        firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Cloud access denied for this account. Please sign out and sign back in with Google."
        firestoreError?.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Offline or network unavailable. Changes will sync automatically when reconnected."
        else -> firestoreError?.localizedMessage ?: error.localizedMessage ?: "Cloud sync temporarily unavailable."
    }
}
