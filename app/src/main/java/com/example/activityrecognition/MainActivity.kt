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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp
import com.example.activityrecognition.InVehicleForegroundService.Companion.ACTION_INITIALIZE_SERVICE
import com.example.activityrecognition.PersistingStorage.Companion.KEY_CURRENT_ACTIVITY
import com.example.activityrecognition.PersistingStorage.Companion.KEY_EVENTS
import com.example.activityrecognition.PersistingStorage.Companion.KEY_ROUTE

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
        val notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }

        if (fineLocationGranted && coarseLocationGranted && notificationPermissionGranted) {
            FileLogger.d("Location and Notification permissions granted")
             if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED) {
                startActivityRecognition()
            } else {
                 FileLogger.w("Activity recognition permission was not granted before location permissions.")
            }
        } else {
            FileLogger.w("Location or Notification permissions denied. Fine: $fineLocationGranted, Coarse: $coarseLocationGranted, Notification: $notificationPermissionGranted")
        }
    }

    @SuppressLint("MissingPermission")
    private val activityRecognitionPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        FileLogger.d("Activity Recognition Permission result: $isGranted")
        if (isGranted) {
            requestLocationAndNotificationPermissions()
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

        val locationPermissionsGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (activityPermissionGranted) {
            if (locationPermissionsGranted && notificationPermissionGranted) {
                startActivityRecognition()
            } else {
                FileLogger.d("Requesting location and/or notification permissions.")
                requestLocationAndNotificationPermissions()
            }
        } else {
            FileLogger.d("Requesting activity recognition permission.")
            activityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    private fun requestLocationAndNotificationPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestLocationAndNotificationPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }


    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    private fun startActivityRecognition() {
        startService(Intent(this, InVehicleForegroundService::class.java).apply {
            action = ACTION_INITIALIZE_SERVICE
        })
    }

    private fun loadSavedEvents() {
        val events = persistingStorage.getEvents().split("\n")
        _activityEvents = events.filter { it.isNotBlank() }
        _currentActivity = persistingStorage.getCurrentActivity()
    }

    private fun loadLastRoute() {
        val routeString = persistingStorage.getRoute()
        if (routeString != null) {
            _lastRouteCoordinates = routeString.split(";")
                .mapNotNull {
                    val parts = it.split(",")
                    if (parts.size == 2) {
                        parts[0].toDoubleOrNull()?.let { lat ->
                            parts[1].toDoubleOrNull()?.let { lon ->
                                Pair(lat, lon)
                            }
                        }
                    } else null
                }
        } else {
            _lastRouteCoordinates = emptyList()
        }
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
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("No route data recorded.")
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(routeCoordinates) { coordinate ->
            Text("Lat: ${"%.6f".format(coordinate.first)}, Lon: ${"%.6f".format(coordinate.second)}",
                 modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        }
    }
}