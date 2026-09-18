package com.iris.assistant.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager

object LocationHelper {
    @SuppressLint("MissingPermission")
    fun getLastLocation(context: Context): String {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc: Location? = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        return loc?.let { "${it.latitude}, ${it.longitude}" } ?: "نامشخص"
    }
}
