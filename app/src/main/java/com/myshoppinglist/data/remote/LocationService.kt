package com.myshoppinglist.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class LocationInfo(
    val postalCode: String,
    val cityName: String
)

@Singleton
class LocationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private var cachedLocation: LocationInfo? = null

    @SuppressLint("MissingPermission")
    suspend fun getLocation(): LocationInfo? {
        cachedLocation?.let { return it }

        val location = suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    cont.resume(loc)
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
            cont.invokeOnCancellation { cts.cancel() }
        } ?: return null

        return try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context, Locale.CANADA)
                .getFromLocation(location.latitude, location.longitude, 1)

            val address = addresses?.firstOrNull() ?: return null
            val postal = address.postalCode ?: return null
            val city = address.locality ?: address.subAdminArea ?: ""

            LocationInfo(postalCode = postal, cityName = city).also {
                cachedLocation = it
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clearCache() {
        cachedLocation = null
    }
}
