package com.memorypot.data.repo

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stores photos in app-internal storage: /files/photos/
 * Scoped-storage compliant and privacy-first.
 */
class PhotoStore(private val context: Context) {
    private val photosDir: File by lazy {
        File(context.filesDir, "photos").apply { mkdirs() }
    }

    fun newPhotoFile(): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(photosDir, "MP_${ts}.jpg")
    }

    fun deleteIfExists(path: String) {
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}
