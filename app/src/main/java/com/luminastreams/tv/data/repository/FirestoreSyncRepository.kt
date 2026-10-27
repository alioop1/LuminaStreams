package com.luminastreams.tv.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.luminastreams.tv.domain.repository.UserSyncRepository
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose

class FirestoreSyncRepository : UserSyncRepository {
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "FirestoreSync"

    override suspend fun syncFavorites(userId: String, movieIds: List<String>): Result<Unit> {
        return try {
            val data = mapOf("favorites" to movieIds)
            db.collection("users").document(userId).set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "syncFavorites failed", e)
            Result.failure(e)
        }
    }

    override suspend fun getFavorites(userId: String): Result<List<String>> {
        return try {
            val doc = db.collection("users").document(userId).get().await()
            val list = doc.get("favorites") as? List<String> ?: emptyList()
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "getFavorites failed", e)
            Result.failure(e)
        }
    }

    override suspend fun syncProgress(userId: String, progressMap: Map<String, String>): Result<Unit> {
        if (progressMap.isEmpty()) return Result.success(Unit)
        return try {
            val docRef = db.collection("users").document(userId)
            // Ensure the document exists first
            docRef.set(mapOf("_exists" to true), SetOptions.merge()).await()

            // Use FieldPath.of() to safely handle keys with special chars (~*/[])
            // Batch in chunks of 500 (Firestore limit per update)
            progressMap.entries.chunked(500).forEach { chunk ->
                val updates = mutableListOf<Any>()
                for ((k, v) in chunk) {
                    updates.add(com.google.firebase.firestore.FieldPath.of("progress", k))
                    updates.add(v)
                }
                if (updates.size >= 2) {
                    // update(FieldPath, value, vararg moreFieldsAndValues)
                    val first = updates[0] as com.google.firebase.firestore.FieldPath
                    val firstVal = updates[1]
                    val rest = updates.drop(2).toTypedArray()
                    docRef.update(first, firstVal, *rest).await()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "syncProgress failed", e)
            Result.failure(e)
        }
    }

    override suspend fun getProgress(userId: String): Result<Map<String, String>> {
        return try {
            val doc = db.collection("users").document(userId).get().await()
            val rawMap = doc.get("progress") as? Map<*, *> ?: emptyMap<String, String>()
            val map = rawMap.entries
                .filter { it.key is String && it.value is String }
                .associate { (it.key as String) to (it.value as String) }
            Result.success(map)
        } catch (e: Exception) {
            Log.e(TAG, "getProgress failed", e)
            Result.failure(e)
        }
    }

    override fun observeUserData(userId: String): kotlinx.coroutines.flow.Flow<Map<String, Any>> = kotlinx.coroutines.flow.callbackFlow {
        val listener = db.collection("users").document(userId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                // DON'T close on transient errors — just log and keep listening
                Log.w(TAG, "Snapshot listener error (will retry): ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data ?: emptyMap()
                trySend(data)
            } else {
                trySend(emptyMap())
            }
        }
        awaitClose { listener.remove() }
    }
}
