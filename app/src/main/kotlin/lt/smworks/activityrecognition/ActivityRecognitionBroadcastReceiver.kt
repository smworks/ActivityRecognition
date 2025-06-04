package lt.smworks.activityrecognition

import ActivityRecognitionEvent
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
            val result = ActivityTransitionResult.extractResult(intent)
            FileLogger.d("Received Activity Transition broadcast: ${result?.transitionEvents.toString()}}")
            result?.let {
                val serviceIntent =
                    Intent(context, InVehicleForegroundService::class.java).apply {
                        action = InVehicleForegroundService.ACTION_ACTIVITY_TRANSITION_RECOGNISED
                        putExtra(
                            InVehicleForegroundService.EXTRA_TRANSITION_EVENTS,
                            it.transitionEvents.map { transition ->
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
        } else if (ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent)
            println("Received Activity Recognition broadcast: ${result?.probableActivities.toString()}")
            result?.let {
                val detectedActivities = it.probableActivities
                for (activity in detectedActivities) {
                    handleActivity(activity.type, activity.confidence)
                }
            }
        } else {
            FileLogger.w("Received an unrecognized intent action: ${intent.getInfo()}")
        }
    }

    private fun handleActivity(activityType: Int, confidence: Int) {
        val activityName = activityType.getActivityName()
        println("Service handleActivity($activityName, confidence: $confidence)")
    }
} 