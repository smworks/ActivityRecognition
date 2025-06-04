package lt.smworks.activityrecognition

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.ActivityTransitionRequest
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume

class ActivityRecognitionProvider(private val context: Context) {
    companion object {
        private const val ACTIVITY_TRANSITION_INTENT_REQUEST = 1991
        private const val ACTIVITY_UPDATES_INTENT_REQUEST = 1992
    }

    private val activityRecognitionClient = ActivityRecognition.getClient(context)

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    suspend fun startActivityUpdatesWithBroadcast(): Boolean = suspendCoroutine { continuation ->
        val pendingIntent = createRecognitionPendingIntent()
        if (pendingIntent == null) {
            FileLogger.e("Activity update PendingIntent could not be created or is not available")
            continuation.resume(false)
            return@suspendCoroutine
        }
        activityRecognitionClient.requestActivityUpdates(1000L, pendingIntent)
            .addOnFailureListener { e ->
                FileLogger.e("Failed to register for activity updates", e)
                continuation.resume(false)
            }
            .addOnSuccessListener {
                FileLogger.i("Successfully registered for activity updates")
                continuation.resume(true)
            }
    }

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    suspend fun startActivityTransitionRecognitionWithBroadcast(): Boolean = suspendCoroutine { continuation ->
        val pendingIntent = createTransitionPendingIntent()
        if (pendingIntent == null) {
            FileLogger.e("Activity transition PendingIntent could not be created or is not available")
            continuation.resume(false)
            return@suspendCoroutine
        }

        val request = createActivityTransitionRequest()
        activityRecognitionClient.requestActivityTransitionUpdates(request, pendingIntent)
            .addOnFailureListener { e ->
                FileLogger.e("Failed to register for activity transition updates", e)
                continuation.resume(false)
            }
            .addOnSuccessListener {
                FileLogger.i("Successfully registered for activity transition updates")
                continuation.resume(true)
            }
    }

    fun createTransitionPendingIntent(): PendingIntent? {
        val intent = Intent(context, ActivityRecognitionBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ACTIVITY_TRANSITION_INTENT_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        return pendingIntent
    }

    fun createRecognitionPendingIntent(): PendingIntent? {
        val intent = Intent(context, ActivityRecognitionBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ACTIVITY_UPDATES_INTENT_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        return pendingIntent
    }

    fun createActivityTransitionRequest(): ActivityTransitionRequest {
        val activityTransitionList = mutableListOf<ActivityTransition>()
        addActivity(activityTransitionList, DetectedActivity.WALKING)
        addActivity(activityTransitionList, DetectedActivity.RUNNING)
        addActivity(activityTransitionList, DetectedActivity.ON_FOOT)
        addActivity(activityTransitionList, DetectedActivity.ON_BICYCLE)
        addActivity(activityTransitionList, DetectedActivity.STILL)
        addActivity(activityTransitionList, DetectedActivity.IN_VEHICLE)
        return ActivityTransitionRequest(activityTransitionList)
    }

    private fun addActivity(
        activityTransitionList: MutableList<ActivityTransition>,
        activity: Int
    ) {
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