package lt.smworks.activityrecognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import lt.smworks.activityrecognition.InVehicleForegroundService.Companion.ACTION_INITIALIZE_SERVICE

class MainActivity : ComponentActivity() {
    private var _isIgnoringBatteryOptimizations by mutableStateOf(false)

    private lateinit var persistingStorage: PersistingStorage


    @SuppressLint("MissingPermission")
    private val requestLocationAndNotificationPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val notificationPermissionGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
            } else {
                true
            }
        if (fineLocationGranted && coarseLocationGranted && notificationPermissionGranted) {
            FileLogger.d("Foreground Location and Notification permissions granted")
            val activityPermission = getActivityRecognitionPermissionType()
            if (ContextCompat.checkSelfPermission(
                    applicationContext, activityPermission
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(
                        applicationContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    FileLogger.d("Background Location permission already granted.")
                    startActivityRecognition()
                } else {
                    FileLogger.d("Requesting background location permission.")
                    backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            } else {
                FileLogger.w("Activity recognition permission was not granted before location permissions.")
            }
        } else {
            FileLogger.w("Foreground Location or Notification permissions denied. Fine: $fineLocationGranted, Coarse: $coarseLocationGranted, Notification: $notificationPermissionGranted")
        }
    }

    @SuppressLint("MissingPermission")
    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            FileLogger.d("Background Location permission granted.")
            if (ContextCompat.checkSelfPermission(
                    applicationContext, getActivityRecognitionPermissionType()
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startActivityRecognition()
            } else {
                FileLogger.w("Activity recognition permission was not granted when background location was granted.")
            }
        } else {
            FileLogger.w("Background Location permission denied.")
        }
    }

    @SuppressLint("MissingPermission")
    private val activityRecognitionPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        FileLogger.d("Activity Recognition Permission result: $isGranted")
        if (isGranted) {
            requestForegroundLocationAndNotificationPermissions()
        } else {
            FileLogger.w("Activity recognition permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        persistingStorage = PersistingStorage(applicationContext)
        checkAndRequestPermissions()
        lockScreenOrientation()

        setContent {
            MaterialTheme {
                CompositionLocalProvider(Storage provides persistingStorage) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding(),
                        color = MaterialTheme.colorScheme.background
                    ) {

                        ActivityTrackerScreen(
                            isIgnoringBatteryOptimizations = _isIgnoringBatteryOptimizations
                        )
                    }
                }
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun lockScreenOrientation() {
        val initialOrientation = resources.configuration.orientation
        if (initialOrientation == Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        } else if (initialOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        }
    }

    private fun checkAndRequestPermissions() {
        val activityPermission = getActivityRecognitionPermissionType()

        val activityPermissionGranted = ContextCompat.checkSelfPermission(
            applicationContext, activityPermission
        ) == PackageManager.PERMISSION_GRANTED

        val fineLocationGranted = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val backgroundLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val notificationPermissionGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        if (activityPermissionGranted) {
            if (fineLocationGranted && coarseLocationGranted && notificationPermissionGranted && backgroundLocationGranted) {
                startActivityRecognition()
            } else if (!fineLocationGranted || !coarseLocationGranted || !notificationPermissionGranted) {
                FileLogger.d("Requesting foreground location and/or notification permissions.")
                requestForegroundLocationAndNotificationPermissions()
            } else {
                FileLogger.d("Requesting background location permission.")
                // You should ideally show a rationale to the user here

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
        } else {
            FileLogger.d("Requesting activity recognition permission.")
            activityRecognitionPermissionLauncher.launch(activityPermission)
        }
    }

    private fun requestForegroundLocationAndNotificationPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestLocationAndNotificationPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }

    private fun startActivityRecognition() {
        if (!InVehicleForegroundService.isRunning()) {
            FileLogger.d("Creating InVehicleForegroundService instance from MainActivity")
            startForegroundService(
                Intent(
                    applicationContext,
                    InVehicleForegroundService::class.java
                ).apply {
                    action = ACTION_INITIALIZE_SERVICE
                })
        }
    }

    override fun onResume() {
        super.onResume()
        updateBatteryOptimizationStatus()
    }

    override fun onPause() {
        super.onPause()
    }

    private fun updateBatteryOptimizationStatus() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        _isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
    }
}