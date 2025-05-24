package com.example.activityrecognition

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InVehicleForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var persistingStorage: PersistingStorage
    private var routePoints = mutableListOf<Pair<Double, Double>>()
    private val activityRecognitionProvider = ActivityRecognitionProvider()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    companion object {
        const val ACTION_INITIALIZE_SERVICE = "ACTION_START_FOREGROUND_SERVICE"
        const val ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE"
        const val ACTION_ACTIVITY_RECOGNISED = "ACTION_ACTIVITY_RECOGNISED"
        const val EXTRA_ACTIVITY_TYPE = "extra_activity_type"
        const val EXTRA_TRANSITION_TYPE = "extra_transition_type"
        private const val NOTIFICATION_CHANNEL_ID = "in_vehicle_service_channel"
        private const val NOTIFICATION_ID = 1
        private const val SERVICE_REQUEST_CODE = 1990

        @Volatile
        private var isServiceInForeground = false
    }

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    override fun onCreate() {
        super.onCreate()
        FileLogger.i("Service onCreate()")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        persistingStorage = PersistingStorage(this)
        createNotificationChannel()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    FileLogger.d("New location: ${location.latitude}, ${location.longitude}")
                    routePoints.add(Pair(location.latitude, location.longitude))
                    saveRoute()
                }
            }
        }

    }

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INITIALIZE_SERVICE -> {
                startForeground()
                activityRecognitionProvider.startActivityRecognition(this) {
//            stopForeground()

                }
            }

            ACTION_STOP_FOREGROUND_SERVICE -> stopForeground()
            ACTION_ACTIVITY_RECOGNISED -> handleRecognisedActivity(
                intent.extras?.getInt(
                    EXTRA_ACTIVITY_TYPE
                ) ?: 0, intent.extras?.getInt(EXTRA_TRANSITION_TYPE) ?: 0
            )
        }
        return START_STICKY
    }

    private fun handleRecognisedActivity(activityType: Int, transitionType: Int) {
        val currentTime = dateFormat.format(Date())
        val activityName = getActivityName(activityType)
        val transitionName = getTransitionName(transitionType)

        FileLogger.i("Service handleRecognisedActivity($activityName, $transitionName)")
        val event = "$currentTime - $activityName - $transitionName"
        persistingStorage.storeEvent(event, activityName)

        updateNotification(activityName)

        if (activityType != DetectedActivity.STILL) {
            if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                startForeground()
                startLocationUpdates()
            } else if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                stopLocationUpdates()
                stopForeground()
            }
        }
    }

    private fun getActivityName(activityType: Int): String {
        return when (activityType) {
            DetectedActivity.STILL -> "Still"
            DetectedActivity.WALKING -> "Walking"
            DetectedActivity.RUNNING -> "Running"
            DetectedActivity.ON_BICYCLE -> "Cycling"
            DetectedActivity.IN_VEHICLE -> "In Vehicle"
            else -> "Unknown Activity Type: $activityType"
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

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "In Movement Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }

    private fun startLocationUpdates() {
        FileLogger.i("Service startLocationUpdates()")
        val locationRequest = LocationRequest.Builder(1000)
            .setMinUpdateIntervalMillis(100)
            .setMaxUpdateDelayMillis(1000)
            .setMinUpdateDistanceMeters(1f)
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            FileLogger.e(
                "Location permissions not granted. Service should be started after permissions are granted."
            )
            return
        }
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        FileLogger.d("Location updates started")
    }

    fun updateNotification(activityName: String) {
        val notification = createNotification(activityName)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun startForeground() {
        if (isServiceInForeground) {
            return
        }
        FileLogger.i("Service startForeground()")
        val notification = createNotification(persistingStorage.getCurrentActivity())

        startForeground(NOTIFICATION_ID, notification)
        isServiceInForeground = true
    }

    private fun createNotification(activityName: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags =
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                SERVICE_REQUEST_CODE,
                notificationIntent,
                pendingIntentFlags
            )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("In Movement")
            .setContentText("Detected activity: $activityName. Tracking location.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
        return notification
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback).addOnFailureListener {
            FileLogger.e("Failed to remove location updates")
        }
    }

    private fun saveRoute() {
        val routeString = routePoints.joinToString(";") { "${it.first},${it.second}" }
        persistingStorage.addRoute(routeString)
        FileLogger.d("Route saved: $routeString")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        FileLogger.i("Service onDestroy()")
    }
}