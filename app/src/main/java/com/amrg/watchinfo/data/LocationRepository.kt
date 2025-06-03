package com.amrg.watchinfo.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationRepository(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val TAG = "LocationRepository"
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    suspend fun getCurrentLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Location permission not granted.")
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            val locationRequest = LocationRequest.create().apply {
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                numUpdates = 1
                maxWaitTime = 10000
            }

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    fusedLocationClient.removeLocationUpdates(this)
                    val location = locationResult.lastLocation
                    if (location != null) {
                        Log.d(TAG, "Location received: $location")
                        if (continuation.isActive) continuation.resume(location)
                    } else {
                        Log.w(TAG, "Location result was null.")
                        if (continuation.isActive) continuation.resume(null)
                    }
                }

                override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                    if (!locationAvailability.isLocationAvailable) {
                        Log.w(TAG, "Location not available.")
                        fusedLocationClient.removeLocationUpdates(this)
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            continuation.invokeOnCancellation {
                Log.d(TAG, "Location request cancelled.")
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        }
    }
}