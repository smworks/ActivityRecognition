package com.example.activityrecognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.activityrecognition.InVehicleForegroundService.Companion.ACTION_INITIALIZE_SERVICE
import com.example.activityrecognition.PersistingStorage.Companion.KEY_CURRENT_ACTIVITY
import com.example.activityrecognition.PersistingStorage.Companion.KEY_EVENTS
import com.example.activityrecognition.PersistingStorage.Companion.KEY_ROUTE
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

class MainActivity : ComponentActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var _currentActivity by mutableStateOf("Unknown")
    private var _activityEvents by mutableStateOf<List<String>>(emptyList())
    private var _lastRouteCoordinates by mutableStateOf<List<Pair<Double, Double>>>(emptyList())

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
        // Background location permission is requested separately on Android 10+ if foreground is granted
        val backgroundLocationGranted =
            permissions[Manifest.permission.ACCESS_BACKGROUND_LOCATION] ?: false  // This will likely be false here

        if (fineLocationGranted && coarseLocationGranted && notificationPermissionGranted) {
            FileLogger.d("Foreground Location and Notification permissions granted")
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // Now check for background location
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        FileLogger.d("Background Location permission already granted.")
                        startActivityRecognition()
                    } else {
                        FileLogger.d("Requesting background location permission.")
                        // You should ideally show a rationale to the user here before launching this
                        backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                } else {
                    // On older versions, background location is implied if foreground is granted
                    startActivityRecognition()
                }
            } else {
                FileLogger.w("Activity recognition permission was not granted before location permissions.")
            }
        } else {
            FileLogger.w("Foreground Location or Notification permissions denied. Fine: $fineLocationGranted, Coarse: $coarseLocationGranted, Notification: $notificationPermissionGranted")
            // Handle cases where essential permissions are denied. Maybe show a message to the user.
        }
    }

    @SuppressLint("MissingPermission")
    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            FileLogger.d("Background Location permission granted.")
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startActivityRecognition()
            } else {
                 FileLogger.w("Activity recognition permission was not granted when background location was granted.")
                 // This scenario should ideally not happen if flow is correct
            }
        } else {
            FileLogger.w("Background Location permission denied.")
            // Handle denial. Maybe inform the user that certain features will be limited.
            // You could still start activity recognition if only foreground location is needed for some parts.
            // For now, let's assume it's critical.
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
        persistingStorage = PersistingStorage(this)
        checkAndRequestPermissions()
        loadSavedEvents()
        loadLastRoute()


        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ActivityTrackerScreen(
                        currentActivity = _currentActivity,
                        activityEvents = _activityEvents,
                        lastRouteCoordinates = _lastRouteCoordinates
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val activityPermissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val backgroundLocationGranted =
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val notificationPermissionGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
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
            } else { // Foreground and notifications are granted, but background location is missing
                FileLogger.d("Requesting background location permission.")
                // You should ideally show a rationale to the user here
                backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        } else {
            FileLogger.d("Requesting activity recognition permission.")
            activityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    private fun requestForegroundLocationAndNotificationPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // DO NOT request ACCESS_BACKGROUND_LOCATION here directly with foreground permissions.
        // It must be requested separately after foreground location is granted.
        requestLocationAndNotificationPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }


    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    private fun startActivityRecognition() {
        if (!InVehicleForegroundService.isRunning()) {
            startService(Intent(this, InVehicleForegroundService::class.java).apply {
                action = ACTION_INITIALIZE_SERVICE
            })
        }
    }

    private fun loadSavedEvents() {
        val events = persistingStorage.getEvents().split("\n")
        _activityEvents = events.filter { it.isNotBlank() }
        _currentActivity = persistingStorage.getCurrentActivity()
    }

    private fun loadLastRoute() {
        val routeString = persistingStorage.getRoute()
        _lastRouteCoordinates = routeString?.split(";")?.mapNotNull {
            val parts = it.split(",")
            if (parts.size == 2) {
                parts[0].toDoubleOrNull()?.let { lat ->
                    parts[1].toDoubleOrNull()?.let { lon ->
                        Pair(lat, lon)
                    }
                }
            } else null
        } ?: emptyList()
    }


    override fun onResume() {
        super.onResume()
        persistingStorage.registerOnSharedPreferenceChangeListener(this)
        loadSavedEvents()
        loadLastRoute()
    }

    override fun onPause() {
        super.onPause()
        persistingStorage.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == KEY_EVENTS || key == KEY_CURRENT_ACTIVITY) {
            loadSavedEvents()
        }
        if (key == KEY_ROUTE) {
            loadLastRoute()
        }
    }
}

@Composable
fun ActivityTrackerScreen(
    currentActivity: String,
    activityEvents: List<String>,
    lastRouteCoordinates: List<Pair<Double, Double>>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Current Activity", style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentActivity, style = MaterialTheme.typography.headlineMedium
                )
            }
        }

        var index by remember { mutableIntStateOf(0) }
        val tabNames = listOf("Activity History", "Last Route", "Logs")
        TabRow(selectedTabIndex = index, modifier = Modifier.fillMaxWidth()) {
            tabNames.forEachIndexed { i, name ->
                Tab(text = { Text(name) }, selected = index == i, onClick = { index = i })
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            when (index) {
                0 -> Events(activityEvents)
                1 -> Route(routeCoordinates = lastRouteCoordinates)
                2 -> Logs()
            }
        }
    }
}

@Composable
private fun Events(activityEvents: List<String>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(activityEvents) { event ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = event,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        if (activityEvents.isEmpty()) {
            item {
                Text("No activity events yet.", modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun Logs() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = FileLogger.getLog() ?: "No log",
                modifier = Modifier.padding(16.dp),
                fontSize = 8.sp
            )
        }
    }
}

@Composable
fun Route(routeCoordinates: List<Pair<Double, Double>>) {
    if (routeCoordinates.isEmpty()) {
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), contentAlignment = Alignment.Center) {
            Text("No route data recorded.")
        }
        return
    }

    val latLngList = routeCoordinates.map { LatLng(it.first, it.second) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(latLngList.firstOrNull() ?: LatLng(0.0, 0.0), 15f)
    }

    LaunchedEffect(latLngList) {
        if (latLngList.isNotEmpty()) {
            val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.builder()
            for (latLng in latLngList) {
                boundsBuilder.include(latLng)
            }
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(
                    boundsBuilder.build(),
                    50
                )
            )
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = true),
        uiSettings = MapUiSettings(myLocationButtonEnabled = true)
    ) {
        if (latLngList.isNotEmpty()) {
            Polyline(points = latLngList)
        }
    }
}