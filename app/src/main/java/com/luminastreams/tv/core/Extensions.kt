package com.luminastreams.tv.core

import android.content.Context
import android.widget.Toast

/**
 * Extension functions לשימוש מהיר בכל הפרויקט.
 *
 * Path: app/src/main/java/com/luminastreams/tv/core/Extensions.kt
 */
fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}