package lt.smworks.activityrecognition

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import lt.smworks.activityrecognition.NotificationProvider.Companion.NOTIFICATION_ID
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices

class InVehicleForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var persistingStorage: PersistingStorage
    private lateinit var notificationProvider: NotificationProvider
    private var routePoints = mutableListOf<Pair<Double, Double>>()
    private lateinit var activityRecognitionProvider: ActivityRecognitionProvider


    companion object {
        const val ACTION_INITIALIZE_SERVICE = "ACTION_START_FOREGROUND_SERVICE"
        const val ACTION_ACTIVITY_TRANSITION_RECOGNISED = "ACTION_ACTIVITY_TRANSITION_RECOGNISED"
        const val EXTRA_ACTIVITY_TYPE = "extra_activity_type"
        const val EXTRA_TRANSITION_TYPE = "extra_transition_type"


        fun isRunning() = isServiceInForeground

        @Volatile
        private var isServiceInForeground = false
    }

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    override fun onCreate() {
        super.onCreate()
        FileLogger.i("Service onCreate(UID=${applicationContext.applicationInfo.uid})")
        persistingStorage = PersistingStorage(this)
        persistingStorage.storeEvent("Service created (UID=${applicationContext.applicationInfo.uid})")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        activityRecognitionProvider = ActivityRecognitionProvider(this)
        notificationProvider = NotificationProvider(this)
        notificationProvider.createNotificationChannel()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    println("New location: ${location.latitude}, ${location.longitude}")
                    routePoints.add(Pair(location.latitude, location.longitude))
                }
            }
        }

    }

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground()
        when (intent?.action) {
            ACTION_INITIALIZE_SERVICE -> {
                activityRecognitionProvider.startActivityTransitionRecognitionWithBroadcast()
                activityRecognitionProvider.startActivityUpdatesWithBroadcast()
            }

            ACTION_ACTIVITY_TRANSITION_RECOGNISED -> {
                handleActivityTransition(
                    activityType = intent.extras?.getInt(EXTRA_ACTIVITY_TYPE) ?: 0,
                    transitionType = intent.extras?.getInt(EXTRA_TRANSITION_TYPE) ?: 0
                )
            }
        }
        return START_STICKY
    }

    private fun handleActivityTransition(activityType: Int, transitionType: Int) {
        val activityName = activityType.getActivityName()
        val transitionName = getTransitionName(transitionType)

        FileLogger.i("Service handleActivityTransition($activityName, $transitionName)")
        val event = "$activityName - $transitionName"
        val previousActivity = persistingStorage.getCurrentActivity()
        persistingStorage.storeEvent(event, activityName)

        updateNotification(activityName)

        if (activityType != DetectedActivity.STILL) {
            if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                if (routePoints.isNotEmpty()) {
                    FileLogger.d("New activity started, clearing previous route points for: $previousActivity")
                    persistingStorage.saveRouteToFile(ArrayList(routePoints), previousActivity)
                    routePoints.clear()
                }
                startLocationUpdates()
            } else if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                stopLocationUpdates()
            }
        } else if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
            stopLocationUpdates()
            stopForeground()
        }
    }

    private fun getTransitionName(transitionType: Int): String {
        return when (transitionType) {
            ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "Enter"
            ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "Exit"
            else -> "Unknown Transition Type: $transitionType"
        }
    }

    private fun stopForeground() {
        FileLogger.i("Service stopForeground()")
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
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
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

    fun updateNotification(activityName: String) {
        val notification = notificationProvider.createNotification(activityName)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun startForeground() {
        FileLogger.i("Service startForeground(). Is already started: $isServiceInForeground")
        val notification = notificationProvider.createNotification(persistingStorage.getCurrentActivity())

        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        
        if (!isServiceInForeground) {
            isServiceInForeground = true
        }
    }


    private fun stopLocationUpdates() {
        saveRoute()
        fusedLocationClient.removeLocationUpdates(locationCallback).addOnFailureListener {
            FileLogger.e("Failed to remove location updates: ${it.message}")
        }
        routePoints.clear()
    }

    private fun saveRoute() {
        if (routePoints.isNotEmpty()) {
            val currentActivity = persistingStorage.getCurrentActivity()
            persistingStorage.saveRouteToFile(ArrayList(routePoints), currentActivity)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        FileLogger.i("Service onDestroy(UID=${applicationContext.applicationInfo.uid})")
        persistingStorage.storeEvent("Service destroyed (UID=${applicationContext.applicationInfo.uid})")
    }
}