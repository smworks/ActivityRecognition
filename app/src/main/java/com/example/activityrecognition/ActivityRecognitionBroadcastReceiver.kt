package com.example.activityrecognition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransitionResult

class ActivityRecognitionBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        FileLogger.d("Received broadcast: ${intent.getInfo()}")

        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent)
            result?.let {
                for (event in it.transitionEvents) {
                    val serviceIntent =
                        Intent(context, InVehicleForegroundService::class.java).apply {
                            action = InVehicleForegroundService.ACTION_ACTIVITY_RECOGNISED
                            putExtra(
                                InVehicleForegroundService.EXTRA_ACTIVITY_TYPE,
                                event.activityType
                            )
                            putExtra(
                                InVehicleForegroundService.EXTRA_TRANSITION_TYPE,
                                event.transitionType
                            )
                        }
                    context.startForegroundService(serviceIntent)
                }
            }
        }
    }
} 