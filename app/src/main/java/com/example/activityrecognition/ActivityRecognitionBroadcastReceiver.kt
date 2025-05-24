package com.example.activityrecognition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityRecognitionBroadcastReceiver : BroadcastReceiver() {
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())


    override fun onReceive(context: Context, intent: Intent) {
        FileLogger.d("Received broadcast: ${intent.getInfo()}")
        
        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent)
            result?.let {
                for (event in it.transitionEvents) {
                    val activityType = event.activityType
                    val transitionType = event.transitionType
                    val sharedPreferences = context.getSharedPreferences("activity_events", MODE_PRIVATE)
                    val currentTime = dateFormat.format(Date())
                    val activityName = when (activityType) {
                        DetectedActivity.STILL -> "Still"
                        DetectedActivity.WALKING -> "Walking"
                        DetectedActivity.RUNNING -> "Running"
                        DetectedActivity.ON_BICYCLE -> "Cycling"
                        DetectedActivity.IN_VEHICLE -> "In Vehicle"
                        else -> "Unknown"
                    }
                    val transitionName = when (transitionType) {
                        ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "Enter"
                        ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "Exit"
                        else -> "Unknown"
                    }
                    FileLogger.d("Activity transition: type=$activityName, transition=$transitionName")
                    val event = "$currentTime - $activityName - $transitionName"
                    val events = sharedPreferences.getString("events", "") ?: ""
                    val newEvents = if (events.isEmpty()) event else "$event\n$events"
                    FileLogger.d("Saving activity event: $event")
                    sharedPreferences.edit().apply {
                        putString("events", newEvents)
                        putString("current_activity", activityName)
                        commit()
                    }
                }
            }
        }
    }
} 