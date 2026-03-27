package com.example.geostaff

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationService(private val context: Context) {
    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission") // We will handle permission check in UI
    fun getLocationUpdates(): Flow<android.location.Location> = callbackFlow {
        // 1. Define settings (How accurate? How fast?)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build()

        // 2. Define callback (What to do when GPS updates)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) } // Send new location to UI
            }
        }

        // 3. Start tracking
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())

        // 4. Stop tracking when screen closes
        awaitClose { client.removeLocationUpdates(callback) }
    }
}