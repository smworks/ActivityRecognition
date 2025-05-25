package com.example.activityrecognition

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.core.content.edit

class PersistingStorage(context: Context) {

    companion object {
        private const val STORAGE_NAME = "PersistingStorage"
        const val KEY_EVENTS = "key_events"
        const val KEY_CURRENT_ACTIVITY = "key_current_activity"
        const val KEY_ROUTE = "route"
    }

    private val sharedPreferences = context.getSharedPreferences(STORAGE_NAME, MODE_PRIVATE)

    fun storeEvent(
        event: String,
        activityName: String = ""
    ) {
        val events = getEvents()
        val accumulatedEvents = if (events.isEmpty()) event else "$event\n$events"
        sharedPreferences.edit().apply {
            putString(KEY_EVENTS, accumulatedEvents)
            if (activityName.isNotEmpty()) {
                putString(KEY_CURRENT_ACTIVITY, activityName)
            }
            apply()
        }
    }

    fun getEvents() = sharedPreferences.getString(KEY_EVENTS, "") ?: ""
    fun addRoute(route: String) {
        sharedPreferences.edit { putString(KEY_ROUTE, route) }
    }

    fun getCurrentActivity(): String {
        return sharedPreferences.getString(KEY_CURRENT_ACTIVITY, "Unknown") ?: "Unknown"
    }

    fun getRoute(): String? {
        return sharedPreferences.getString(KEY_ROUTE, null)
    }

    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }
}