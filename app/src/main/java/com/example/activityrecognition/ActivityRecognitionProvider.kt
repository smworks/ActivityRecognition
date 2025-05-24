package com.example.activityrecognition

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.ActivityTransitionRequest

class ActivityRecognitionProvider {
    private lateinit var activityRecognitionClient: ActivityRecognitionClient

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    fun startActivityRecognition(context: Context) {
        FileLogger.d("Starting activity recognition")
        activityRecognitionClient = ActivityRecognition.getClient(context)
        val intent = Intent(context, ActivityRecognitionBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_MUTABLE
        )

        val activityTransitionList = ArrayList<ActivityTransition>()
        addActivity(activityTransitionList, DetectedActivity.WALKING)
        addActivity(activityTransitionList, DetectedActivity.RUNNING)
        addActivity(activityTransitionList, DetectedActivity.ON_FOOT)
        addActivity(activityTransitionList, DetectedActivity.ON_BICYCLE)
        addActivity(activityTransitionList, DetectedActivity.WALKING)
        addActivity(activityTransitionList, DetectedActivity.STILL)
        addActivity(activityTransitionList, DetectedActivity.IN_VEHICLE)

        val request = ActivityTransitionRequest(activityTransitionList)
        FileLogger.d("Requesting activity transition updates")

        activityRecognitionClient.requestActivityTransitionUpdates(request, pendingIntent)
            .addOnSuccessListener {
                FileLogger.d("Successfully registered for activity transition updates")
            }
            .addOnFailureListener { e ->
                FileLogger.e("Failed to register for activity transition updates", e)
            }
    }

    private fun addActivity(activityTransitionList: ArrayList<ActivityTransition>, activity: Int) {
        activityTransitionList.add(
            ActivityTransition.Builder()
                .setActivityType(activity)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        )
        activityTransitionList.add(
            ActivityTransition.Builder()
                .setActivityType(activity)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build()
        )
    }
}