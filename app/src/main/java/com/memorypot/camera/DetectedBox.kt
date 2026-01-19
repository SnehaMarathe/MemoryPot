package com.memorypot.camera

import android.graphics.RectF

data class DetectedBox(
    val trackingId: Int,
    val rect: RectF, // in PreviewView pixel coordinates
    val label: String?,
    val confidence: Float?
)
