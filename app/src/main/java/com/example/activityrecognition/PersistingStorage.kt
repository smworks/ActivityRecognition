package com.example.activityrecognition

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import com.google.android.gms.maps.model.LatLng
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PersistingStorage(private val context: Context) {

    companion object {
        private const val STORAGE_NAME = "PersistingStorage"
        const val KEY_EVENTS = "key_events"
        const val KEY_CURRENT_ACTIVITY = "key_current_activity"
        private const val ROUTES_DIRECTORY_NAME = "routes"
    }

    private val sharedPreferences = context.getSharedPreferences(STORAGE_NAME, MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val routeFileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())

    fun storeEvent(
        event: String,
        activityName: String = ""
    ) {
        val events = getEvents()
        val currentTime = dateFormat.format(Date())
        val newEvent = "$currentTime: $event"
        val accumulatedEvents = if (events.isEmpty()) newEvent else "$newEvent\n$events"
        sharedPreferences.edit().apply {
            putString(KEY_EVENTS, accumulatedEvents)
            if (activityName.isNotEmpty()) {
                putString(KEY_CURRENT_ACTIVITY, activityName)
            }
            apply()
        }
    }

    fun getEvents() = sharedPreferences.getString(KEY_EVENTS, "") ?: ""

    fun saveRouteToFile(routePoints: List<Pair<Double, Double>>, activityName: String) {
        if (routePoints.isEmpty()) {
            FileLogger.w("Attempted to save an empty route.")
            return
        }
        val routesDir = File(context.filesDir, ROUTES_DIRECTORY_NAME)
        if (!routesDir.exists()) {
            routesDir.mkdirs()
        }
        val timestamp = routeFileDateFormat.format(Date())
        val fileName = "${activityName}_${timestamp}.route"
        val routeFile = File(routesDir, fileName)
        val routeString = routePoints.joinToString(";") { "${it.first},${it.second}" }
        try {
            routeFile.writeText(routeString)
            FileLogger.i("Route saved to: ${routeFile.absolutePath}")
        } catch (e: Exception) {
            FileLogger.e("Error saving route to file: $fileName", e)
        }
    }

    fun getRoutePaths(): List<String> {
        val routesDir = File(context.filesDir, ROUTES_DIRECTORY_NAME)
        if (!routesDir.exists() || !routesDir.isDirectory) {
            return emptyList()
        }
        return routesDir.listFiles { file -> file.isFile && file.name.endsWith(".route") }
            ?.map { it.absolutePath } ?: emptyList()
    }

    fun getRoute(path: String): List<LatLng> {
        val file = File(path)
        return if (file.exists() && file.isFile) {
            try {
                val routeString = file.readText()
                return routeString.split(";").mapNotNull {
                    val parts = it.split(",")
                    if (parts.size == 2) {
                        parts[0].toDoubleOrNull()?.let { lat ->
                            parts[1].toDoubleOrNull()?.let { lon ->
                                LatLng(lat, lon)
                            }
                        }
                    } else null
                }
            } catch (e: Exception) {
                FileLogger.e("Error reading route file: $path", e)
                emptyList()
            }
        } else {
            FileLogger.w("Route file not found or is not a file: $path")
            emptyList()
        }
    }

    fun getCurrentActivity(): String {
        return sharedPreferences.getString(KEY_CURRENT_ACTIVITY, "Unknown") ?: "Unknown"
    }

    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }
}