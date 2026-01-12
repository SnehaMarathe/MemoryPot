package com.memorypot.data.repo

import android.Manifest
import android.content.Context
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val placeText: String,
    val geoKey: String
)

class LocationHelper(private val context: Context) {

    private val fused: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            coarse == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    suspend fun getCurrentLocationOrNull(): Location? = runCatching {
        fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
    }.getOrNull()

    /**
     * Reverse geocode to a short human readable string. Falls back to "lat, lon".
     */
    suspend fun reverseGeocodeShort(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            val results = geocoder.getFromLocation(lat, lon, 1)
            val a = results?.firstOrNull()
            val parts = listOfNotNull(
                a?.subLocality,
                a?.locality,
                a?.subAdminArea
            ).distinct().take(2)
            if (parts.isNotEmpty()) parts.joinToString(", ") else "${"%.5f".format(lat)}, ${"%.5f".format(lon)}"
        }.getOrElse {
            "${"%.5f".format(lat)}, ${"%.5f".format(lon)}"
        }
    }

    /**
     * GeoKey: coarse rounding to group nearby spots (approx ~100m-1km depending on latitude).
     */
    fun geoKey(lat: Double, lon: Double): String {
        val rLat = (lat * 1000.0).toInt() / 1000.0
        val rLon = (lon * 1000.0).toInt() / 1000.0
        return "$rLat,$rLon"
    }

    fun distanceMeters(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Float {
        val res = FloatArray(1)
        Location.distanceBetween(aLat, aLon, bLat, bLon, res)
        return res[0]
    }
}
