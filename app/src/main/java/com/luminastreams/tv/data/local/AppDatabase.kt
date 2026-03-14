package com.luminastreams.tv.data.local

// תשתית מוכנה לאינטגרציה עם Room Database (דורש הוספת Room ל-Gradle)
// המטרה: לשמור את היסטוריית הצפייה וההתקדמות לוקאלית.
interface AppDatabase {
    suspend fun saveWatchProgress(movieId: String, progressMillis: Long)
    suspend fun getWatchProgress(movieId: String): Long
}