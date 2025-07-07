package lt.smworks.activityrecognition

import lt.smworks.activityrecognition.ActivityRecognitionEvent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransitionResult
import kotlinx.coroutines.DelicateCoroutinesApi
import java.io.Serializable

class ActivityRecognitionBroadcastReceiver : BroadcastReceiver() {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityTransitionResult.hasResult(intent)) {
            handleActivityTransition(intent, context)
        } else if (ActivityRecognitionResult.hasResult(intent)) {
            handleActivityRecognition(intent)
        } else {
            FileLogger.w("Received an unrecognized intent action: ${intent.getInfo()}")
        }
    }

    private fun handleActivityRecognition(intent: Intent) {
        val result = ActivityRecognitionResult.extractResult(intent)
        println("Received Activity Recognition broadcast: ${result?.probableActivities.toString()}")
        if (result == null) {
            return
        }
        val detectedActivities = result.probableActivities
        for (activity in detectedActivities) {
            handleActivity(activity.type, activity.confidence)
        }
    }

    private fun handleActivityTransition(intent: Intent, context: Context) {
        val result = ActivityTransitionResult.extractResult(intent)
        FileLogger.d("handleActivityTransition(${result?.toTransitionEvents()})")
        if (result == null) {
            return
        }
        val serviceIntent = Intent(context, InVehicleForegroundService::class.java).apply {
            action = InVehicleForegroundService.ACTION_ACTIVITY_TRANSITION_RECOGNISED
            putExtra(
                InVehicleForegroundService.EXTRA_TRANSITION_EVENTS,
                result.transitionEvents.map { transition ->
                    ActivityRecognitionEvent(
                        transition.activityType,
                        transition.transitionType
                    )
                } as Serializable)
        }
        try {
            context.startForegroundService(serviceIntent)
        } catch (t: Throwable) {
            FileLogger.e("Failed to start service for activity transition: ${t.message}")
        }
    }

    private fun handleActivity(activityType: Int, confidence: Int) {
        val activityName = activityType.getActivityName()
        println("Service handleActivity($activityName, confidence: $confidence)")
    }

    private fun ActivityTransitionResult.toTransitionEvents(): String {
        return transitionEvents.joinToString(separator = ",") { event -> "${event.activityType.getActivityName()}->${event.transitionType.getTransitionName()}"  }
    }
} 