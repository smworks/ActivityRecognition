package lt.smworks.activityrecognition

import ActivityRecognitionEvent
import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import lt.smworks.activityrecognition.NotificationProvider.Companion.NOTIFICATION_ID

class InVehicleForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var persistingStorage: PersistingStorage
    private lateinit var notificationProvider: NotificationProvider
    private var routePoints = mutableListOf<LatLng>()

    companion object {
        const val ACTION_INITIALIZE_SERVICE = "ACTION_START_FOREGROUND_SERVICE"
        const val ACTION_ACTIVITY_TRANSITION_RECOGNISED = "ACTION_ACTIVITY_TRANSITION_RECOGNISED"
        const val EXTRA_TRANSITION_EVENTS = "extra_transition_events"


        fun isRunning() = isServiceInForeground

        @Volatile
        private var isServiceInForeground = false

        @Volatile
        private var isActivityRecognitionActive = false
    }

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    override fun onCreate() {
        super.onCreate()
        FileLogger.i("Service onCreate(UID=${applicationContext.applicationInfo.uid}, serviceId=${this.hashCode()})")
        persistingStorage = PersistingStorage(applicationContext)
//        persistingStorage.storeEvent("Service created (UID=${applicationContext.applicationInfo.uid})")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        notificationProvider = NotificationProvider(applicationContext)
        notificationProvider.createNotificationChannel()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    println("New location: ${location.latitude}, ${location.longitude}")
                    routePoints.add(LatLng(location.latitude, location.longitude))
                }
            }
        }

    }

    @OptIn(DelicateCoroutinesApi::class)
    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        GlobalScope.launch {
            startForeground()
            if (!isActivityRecognitionActive) {
                isActivityRecognitionActive = true
                ActivityRecognitionProvider(applicationContext).apply {
                    startActivityTransitionRecognitionWithBroadcast()
                    startActivityUpdatesWithBroadcast()
                }
            }
            when (intent?.action) {
                ACTION_INITIALIZE_SERVICE -> {
                    FileLogger.d("Service initialized (serviceId=${this.hashCode()})")
                    stopForeground()
                }

                ACTION_ACTIVITY_TRANSITION_RECOGNISED -> {
                    val transitionEvents = intent.getActivityRecognitionEvents()
                    handleActivityTransition(transitionEvents ?: emptyList())
                }
            }
        }
        return START_STICKY
    }


    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun handleActivityTransition(recognizedTransitions: List<ActivityRecognitionEvent>) {
        val activityBefore = persistingStorage.getCurrentActivity()
        val currentActivity = recognizedTransitions.lastOrNull()
        if (currentActivity == null) {
            FileLogger.w("No activity transition events found!!!")
            return
        }
        val currentActivityName = currentActivity.activityType.getActivityName()
        val transitionName = currentActivity.transitionType.getTransitionName()
        FileLogger.i("Service handleActivityTransition($currentActivityName, $transitionName, serviceId=${this.hashCode()}))")
        val event = "$currentActivityName - $transitionName"
        persistingStorage.storeEvent(event, currentActivityName)

        FileLogger.i("Service updateNotification(currentActivityName=$currentActivityName)")
        notificationProvider.updateNotification(currentActivityName)

        if (currentActivity.activityType != DetectedActivity.STILL) {
            if (currentActivity.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                if (currentActivityName != activityBefore) {
                    saveRoute(activityBefore)
                }
                startLocationUpdates()
            } else if (currentActivity.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                stopLocationUpdates(activityBefore)
            }
        } else if (currentActivity.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
            stopLocationUpdates(activityBefore)
            stopForeground()
        }
    }

    private fun stopForeground() {
        FileLogger.i("Service stopForeground(serviceId=${this.hashCode()}))")
        stopLocationUpdates()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        isServiceInForeground = false
    }

    private fun startLocationUpdates() {
        FileLogger.i("Service startLocationUpdates()")
        val locationRequest = LocationRequest.Builder(1000).setMinUpdateIntervalMillis(100)
            .setMaxUpdateDelayMillis(1000).setMinUpdateDistanceMeters(1f).build()

        if (ActivityCompat.checkSelfPermission(
                applicationContext, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            FileLogger.e(
                "Location permissions not granted. Service should be started after permissions are granted."
            )
            return
        }
        fusedLocationClient.requestLocationUpdates(
            locationRequest, locationCallback, Looper.getMainLooper()
        ).addOnFailureListener {
            FileLogger.e("Failed to request location updates: ${it.message}")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun startForeground() {
        FileLogger.i("Service startForeground(). Is already started: $isServiceInForeground")
        val notification =
            notificationProvider.createNotification(persistingStorage.getCurrentActivity())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!isServiceInForeground) {
            isServiceInForeground = true
        }
    }


    @OptIn(DelicateCoroutinesApi::class)
    private fun stopLocationUpdates(activityBefore: String = "") {
        fusedLocationClient.removeLocationUpdates(locationCallback).addOnFailureListener {
            FileLogger.e("Failed to remove location updates: ${it.message}")
        }
        saveRoute(activityBefore)
    }

    private fun saveRoute(routeActivity: String) {
        if (routePoints.isNotEmpty()) {
            persistingStorage.saveRouteToDatabase(ArrayList(routePoints), routeActivity)
            routePoints.clear()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationProvider.destroyNotification()
        FileLogger.i("Service onDestroy(UID=${applicationContext.applicationInfo.uid}, serviceId=${this.hashCode()}))")
//        persistingStorage.storeEvent("Service destroyed (UID=${applicationContext.applicationInfo.uid})")
    }
}