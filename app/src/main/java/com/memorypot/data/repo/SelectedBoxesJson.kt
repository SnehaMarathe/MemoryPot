package com.memorypot.data.repo

import android.graphics.RectF
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tiny JSON helper for storing object-selection boxes in Room.
 *
 * We store normalized rects (0..1) so they remain valid across image sizes.
 */
object SelectedBoxesJson {

    /**
     * Serializes a list of normalized [RectF] into a compact JSON string.
     *
     * Format: [{"l":0.1,"t":0.2,"r":0.5,"b":0.6}, ...]
     */
    fun encode(rects: List<RectF>): String {
        val arr = JSONArray()
        rects.forEach { r ->
            val o = JSONObject()
            o.put("l", r.left.coerceIn(0f, 1f))
            o.put("t", r.top.coerceIn(0f, 1f))
            o.put("r", r.right.coerceIn(0f, 1f))
            o.put("b", r.bottom.coerceIn(0f, 1f))
            arr.put(o)
        }
        return arr.toString()
    }

    /**
     * Best-effort decode. Returns empty list on any parse error.
     */
    fun decode(json: String?): List<RectF> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        RectF(
                            o.optDouble("l", 0.0).toFloat(),
                            o.optDouble("t", 0.0).toFloat(),
                            o.optDouble("r", 0.0).toFloat(),
                            o.optDouble("b", 0.0).toFloat()
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }
}
