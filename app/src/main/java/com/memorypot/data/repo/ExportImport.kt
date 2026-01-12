package com.memorypot.data.repo

import android.content.Context
import android.net.Uri
import com.memorypot.data.db.MemoryDao
import com.memorypot.data.db.MemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * v0 export: JSON metadata only (privacy-first, simple).
 * Photos not embedded in v0 to keep export light.
 */
class ExportImport(
    private val context: Context,
    private val dao: MemoryDao
) {
    suspend fun exportJson(toUri: Uri) = withContext(Dispatchers.IO) {
        val all = dao.getAll()
        val root = JSONObject()
        root.put("schemaVersion", 0)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("memories", JSONArray().apply {
            all.forEach { put(it.toJson()) }
        })

        context.contentResolver.openOutputStream(toUri)?.use { out ->
            out.write(root.toString(2).toByteArray(Charsets.UTF_8))
        }
    }

    private fun MemoryEntity.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("note", note)
        put("placeText", placeText)
        put("photoPath", photoPath) // internal path on this device; v1 should export photos too
        put("createdAt", createdAt)
        put("isArchived", isArchived)
        put("latitude", latitude)
        put("longitude", longitude)
        put("geoKey", geoKey)
    }
}
