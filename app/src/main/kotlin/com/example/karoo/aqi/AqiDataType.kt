package com.example.karoo.aqi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.widget.RemoteViews
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.*

class AqiDataType(extensionId: String) : DataTypeImpl(extensionId, "peanut_visual_type") {

    // Target coordinates (31°13'5.47"N, 121°28'0.06"E)
    private val targetLocation = Location("").apply {
        latitude = 31.218186
        longitude = 121.466683
    }

    private var currentState = State.SEARCHING
    private val distanceHistory = mutableListOf<Pair<Long, Float>>() // timestamp, distance
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var lastLocationTime: Long = 0L

    enum class State { SEARCHING, FOUND, ACTIVE, ARRIVED }

    @SuppressLint("MissingPermission")
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        updateRemoteView(context, emitter, null, null) // Initial render

        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                handleNewLocation(context, emitter, location)
            }
        }

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener)

        // Watchdog Timer Coroutine
        coroutineScope.launch {
            while (isActive) {
                delay(2000)
                val timeSinceLastUpdate = System.currentTimeMillis() - lastLocationTime

                // If in ACTIVE or ARRIVED state and signal is lost for more than 5 seconds
                if ((currentState == State.ACTIVE || currentState == State.ARRIVED) && timeSinceLastUpdate > 5000) {
                    currentState = State.SEARCHING
                    distanceHistory.clear()
                    updateRemoteView(context, emitter, null, null)
                }
            }
        }

        emitter.setCancellable {
            locationManager.removeUpdates(locationListener)
            coroutineScope.cancel()
        }
    }

    private fun handleNewLocation(context: Context, emitter: ViewEmitter, currentLocation: Location) {
        val now = System.currentTimeMillis()
        lastLocationTime = now

        val distance = currentLocation.distanceTo(targetLocation)

        val arrowRotation = if (currentLocation.hasBearing()) {
            currentLocation.bearingTo(targetLocation) - currentLocation.bearing
        } else {
            currentLocation.bearingTo(targetLocation)
        }

        distanceHistory.add(Pair(now, distance))
        distanceHistory.removeAll { it.first < now - 60_000 }

        when (currentState) {
            State.SEARCHING -> {
                currentState = State.FOUND
                updateRemoteView(context, emitter, null, null)

                coroutineScope.launch {
                    delay(3000)
                    if (System.currentTimeMillis() - lastLocationTime < 5000) {
                        // Check distance immediately after lock to determine next state
                        currentState = if (distance <= 50) State.ARRIVED else State.ACTIVE
                        updateRemoteView(context, emitter, distance, arrowRotation)
                    }
                }
            }
            State.FOUND -> {}
            State.ACTIVE -> {
                if (distance <= 50) {
                    currentState = State.ARRIVED
                    distanceHistory.clear()
                }
                updateRemoteView(context, emitter, distance, arrowRotation)
            }
            State.ARRIVED -> {
                if (distance > 50) {
                    currentState = State.ACTIVE
                    distanceHistory.clear()
                }
                updateRemoteView(context, emitter, distance, arrowRotation)
            }
        }
    }

    private fun updateRemoteView(context: Context, emitter: ViewEmitter, distance: Float?, rotation: Float?) {
        val remoteViews = when (currentState) {
            State.SEARCHING -> RemoteViews(context.packageName, R.layout.widget_searching)
            State.FOUND -> RemoteViews(context.packageName, R.layout.widget_found)
            State.ARRIVED -> RemoteViews(context.packageName, R.layout.widget_arrived)
            State.ACTIVE -> {
                val views = RemoteViews(context.packageName, R.layout.widget_active)

                if (distance != null && rotation != null) {
                    val distanceDisplay = if (distance >= 1000) {
                        String.format("%.1f km", distance / 1000f)
                    } else {
                        "${distance.toInt()} m"
                    }
                    views.setTextViewText(R.id.distance_text, distanceDisplay)

                    views.setFloat(R.id.direction_arrow, "setRotation", rotation)

                    if (distanceHistory.isNotEmpty()) {
                        val oldestDistance = distanceHistory.first().second
                        val delta = oldestDistance - distance

                        val color = when {
                            delta >= 200 -> Color.GREEN
                            delta <= -200 -> Color.RED
                            else -> Color.parseColor("#FFA500") // Orange
                        }
                        views.setInt(R.id.direction_arrow, "setColorFilter", color)
                    }
                }
                views
            }
        }
        emitter.updateView(remoteViews)
    }
}