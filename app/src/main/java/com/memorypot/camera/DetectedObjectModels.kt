package com.memorypot.camera

import android.graphics.RectF

/** Bounding box in PreviewView (view) coordinates. */
data class DetectedObjectBox(
    val trackingId: Int,
    val rect: RectF,
    val label: String? = null,
    val confidence: Float? = null
)

/** Normalized (0..1) bounding box relative to the *analysis image* in upright orientation. */
data class NormalizedBox(
    val trackingId: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val label: String? = null,
    val confidence: Float? = null
)
