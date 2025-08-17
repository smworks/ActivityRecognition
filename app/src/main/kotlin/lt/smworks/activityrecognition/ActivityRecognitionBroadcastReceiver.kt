package lt.smworks.activityrecognition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionResult
import kotlinx.coroutines.DelicateCoroutinesApi
import java.io.Serializable

class ActivityRecognitionBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val ACTIVITY_TRANSITION_DELAY = 10 * 60 * 1000L
    }

    private var lastEvent: ActivityTransitionEvent? = null
    private var lastEventTimestamp: Long = 0

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
            FileLogger.w("Activity transition result is null")
            return
        }
        val transitionEvents = result.transitionEvents
        if (transitionEvents.isEmpty()) {
            FileLogger.w("Activity transition events are empty")
            return
        }
        if (transitionEvents.size == 1) {
            handleSingleEventActivityTransition(context, transitionEvents[0])
        } else if (result.transitionEvents.size == 2) {
            handleTwoEventActivityTransition(context, transitionEvents)
        } else {
            FileLogger.w("More than two activity transition events detected!")
        }
    }

    private fun handleSingleEventActivityTransition(context: Context, event: ActivityTransitionEvent) {
        if (lastEvent != null) {
            val isActivityTypeMatch = lastEvent?.activityType == event.activityType
            val isTransitionTypeMatch = lastEvent?.transitionType == event.transitionType
            val isDelayNotExceeded = System.currentTimeMillis() - lastEventTimestamp < ACTIVITY_TRANSITION_DELAY
            if (isActivityTypeMatch && isTransitionTypeMatch && isDelayNotExceeded) {
                FileLogger.i("Ignoring duplicate activity transition due to short delay")
                return
            }
        }
        lastEvent = event
        lastEventTimestamp = System.currentTimeMillis()
        startForegroundServiceWithNewActivityTransitions(context, listOf(event))
    }

    private fun handleTwoEventActivityTransition(
        context: Context,
        transitionEvents: List<ActivityTransitionEvent>
    ) {
        startForegroundServiceWithNewActivityTransitions(context, transitionEvents)
    }

    private fun startForegroundServiceWithNewActivityTransitions(
        context: Context,
        transitionEvents: List<ActivityTransitionEvent>
    ) {
        val serviceIntent = Intent(context, InVehicleForegroundService::class.java).apply {
            action = InVehicleForegroundService.ACTION_ACTIVITY_TRANSITION_RECOGNISED
            putExtra(
                InVehicleForegroundService.EXTRA_TRANSITION_EVENTS,
                transitionEvents.map { transition ->
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