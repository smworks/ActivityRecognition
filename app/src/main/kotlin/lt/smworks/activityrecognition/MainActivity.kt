package lt.smworks.activityrecognition

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
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
            val activityPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Manifest.permission.ACTIVITY_RECOGNITION
            } else {
                "com.google.android.gms.permission.ACTIVITY_RECOGNITION"
            }
            if (ContextCompat.checkSelfPermission(
                    applicationContext, activityPermission
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (ContextCompat.checkSelfPermission(
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
                    applicationContext, Manifest.permission.ACTIVITY_RECOGNITION
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

        setContent {
            MaterialTheme {
                CompositionLocalProvider(Storage provides persistingStorage) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val activityEvents by persistingStorage.eventObservable.collectAsState(
                            initial = emptyList()
                        )
                        val currentActivity by persistingStorage.activityObservable.collectAsState(initial = null)
                        ActivityTrackerScreen(
                            currentActivity = currentActivity,
                            activityEvents = activityEvents.map { it.value },
                            isIgnoringBatteryOptimizations = _isIgnoringBatteryOptimizations
                        )
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val activityPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Manifest.permission.ACTIVITY_RECOGNITION
        } else {
            "com.google.android.gms.permission.ACTIVITY_RECOGNITION"
        }

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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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


    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    private fun startActivityRecognition() {
        if (!InVehicleForegroundService.isRunning()) {
            startService(Intent(applicationContext, InVehicleForegroundService::class.java).apply {
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

@SuppressLint("CompositionLocalNaming")
private val Storage = compositionLocalOf<PersistingStorage?> { null }

@Composable
fun ActivityTrackerScreen(
    currentActivity: String?,
    activityEvents: List<String>,
    isIgnoringBatteryOptimizations: Boolean
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
            Row(
                modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Current Activity", style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentActivity ?: "No activity detected", style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isIgnoringBatteryOptimizations) "(Ignoring Battery Optimizations)" else "(Not Ignoring Battery Optimizations)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                val context = LocalContext.current
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RefreshButton(context)
                    BatteryOptimisationButton(context)
                }
            }

        }

        var index by remember { mutableIntStateOf(0) }
        val tabNames = listOf("Activity History", "Routes", "Logs")
        TabRow(selectedTabIndex = index, modifier = Modifier.fillMaxWidth()) {
            tabNames.forEachIndexed { i, name ->
                Tab(text = { Text(name) }, selected = index == i, onClick = { index = i })
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            when (index) {
                0 -> Events(activityEvents)
                1 -> Routes()
                2 -> Logs()
            }
        }
    }
}

@Composable
private fun BatteryOptimisationButton(context: Context) {
    IconButton(onClick = {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = "package:${context.packageName}".toUri()
        context.startActivity(intent)
    }) {
        Icon(
            Icons.Filled.Settings,
            contentDescription = "Ignore Battery Optimizations"
        )
    }
}

@Composable
private fun RefreshButton(context: Context) {
    IconButton(onClick = {
        val applicationContext = context.applicationContext as Application
        ActivityRecognitionProvider(applicationContext).apply {
            val activityPermission =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Manifest.permission.ACTIVITY_RECOGNITION
                } else {
                    "com.google.android.gms.permission.ACTIVITY_RECOGNITION"
                }
            if (ActivityCompat.checkSelfPermission(
                    context, activityPermission
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startActivityTransitionRecognitionWithBroadcast()
                startActivityUpdatesWithBroadcast()
            } else {
                FileLogger.e("Permission for activity recognition not granted")
            }
        }
    }) {
        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
    }
}

@Composable
private fun Events(activityEvents: List<String>) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .lazyListScrollBar(listState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(activityEvents) { event ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = event, modifier = Modifier.padding(16.dp)
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
}

@Composable
private fun Logs() {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .scrollBar(scrollState, color = MaterialTheme.colorScheme.primary)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = FileLogger.getLog() ?: "No log",
            modifier = Modifier.padding(vertical = 16.dp),
            fontSize = 8.sp,
            lineHeight = 8.sp
        )
    }
}

@Composable
fun ColumnScope.Routes() {
    val routes = Storage.current?.getRoutePaths() ?: emptyList()
    if (routes.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp), contentAlignment = Alignment.Center
        ) {
            Text("No route data recorded.")
        }
        return
    }

    var routePath by remember { mutableStateOf(routes.first()) }
    val latLngList = Storage.current?.getRoute(routePath)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(latLngList?.firstOrNull() ?: LatLng(0.0, 0.0), 15f)
    }

    LaunchedEffect(latLngList) {
        if (latLngList?.isNotEmpty() == true) {
            val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.builder()
            for (latLng in latLngList) {
                boundsBuilder.include(latLng)
            }
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(
                    boundsBuilder.build(), 50
                )
            )
        }
    }

    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(routes) {
            Card(
                modifier = Modifier
                    .clickable(onClick = { routePath = it })
                    .fillMaxWidth()
            ) {
                Text(
                    it.substringAfterLast("/"),
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    GoogleMap(
        modifier = Modifier.weight(2f),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = true),
        uiSettings = MapUiSettings(myLocationButtonEnabled = true)
    ) {
        if (latLngList?.isNotEmpty() == true) {
            Polyline(points = latLngList)
        }
    }
}