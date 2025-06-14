package lt.smworks.activityrecognition

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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

@SuppressLint("CompositionLocalNaming")
private val Storage = compositionLocalOf<PersistingStorage?> { null }

@Composable
fun ActivityTrackerScreen(isIgnoringBatteryOptimizations: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        CurrentActivityCard(isIgnoringBatteryOptimizations)

        var index by remember { mutableIntStateOf(0) }
        val tabNames = listOf("Activity History", "Routes", "Logs")
        TabRow(selectedTabIndex = index, modifier = Modifier.fillMaxWidth()) {
            tabNames.forEachIndexed { i, name ->
                Tab(text = { Text(name) }, selected = index == i, onClick = { index = i })
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            when (index) {
                0 -> Events()
                1 -> Routes()
                2 -> Logs()
            }
        }
    }
}

@Composable
private fun CurrentActivityCard(isIgnoringBatteryOptimizations: Boolean) {
    val storage = Storage.current ?: return
    val currentActivity by storage.activityObservable.collectAsState(initial = null)
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
                    text = currentActivity ?: "No activity detected",
                    style = MaterialTheme.typography.headlineMedium
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
}

@SuppressLint("BatteryLife")
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

@OptIn(DelicateCoroutinesApi::class)
@Composable
private fun RefreshButton(context: Context) {
    IconButton(onClick = {
        val applicationContext = context.applicationContext as Application
        ActivityRecognitionProvider(applicationContext).apply {
            val activityPermission = getActivityRecognitionPermissionType()
            if (ActivityCompat.checkSelfPermission(
                    context, activityPermission
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                GlobalScope.launch {
                    startActivityTransitionRecognitionWithBroadcast()
                    startActivityUpdatesWithBroadcast()
                }
            } else {
                FileLogger.e("Permission for activity recognition not granted")
            }
        }
    }) {
        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
    }
}

@Composable
private fun Events() {
    val storage = Storage.current ?: return
    val activityEvents = storage.eventObservable.collectAsState(initial = null).value

    if (activityEvents == null) {
        CenteredContent {
            CircularProgressIndicator()
        }
        return
    }

    EventList(activityEvents)
}

@Composable
private fun EventList(activityEvents: List<Event>) {
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
                        text = event.timestamp.timestampToDate() + " - " + event.value,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            if (activityEvents.isEmpty()) {
                item {
                    CenteredContent {
                        Text("No activity events yet.", modifier = Modifier.padding(16.dp))
                    }
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
    val storage = Storage.current
    val routesWithPoints by storage?.getAllRoutesWithPoints()?.collectAsState(initial = null)
        ?: remember { mutableStateOf(emptyList()) }

    if (routesWithPoints == null) {
        CenteredContent {
            CircularProgressIndicator(modifier = Modifier.size(64.dp))
        }
        return
    }

    if (routesWithPoints?.isEmpty() == true) {
        CenteredContent {
            Text("No route data recorded.")
        }
        return
    }

    RouteContent(routesWithPoints)
}

@Composable
private fun ColumnScope.RouteContent(routesWithPoints: List<RouteWithPoints>?) {
    var selectedRouteWithPoints by remember { mutableStateOf(routesWithPoints?.firstOrNull()) }
    LaunchedEffect(routesWithPoints) {
        if (selectedRouteWithPoints == null || routesWithPoints?.contains(selectedRouteWithPoints) == false) {
            selectedRouteWithPoints = routesWithPoints?.firstOrNull()
        }
    }

    val latLngList = selectedRouteWithPoints?.points?.map { LatLng(it.latitude, it.longitude) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(latLngList?.firstOrNull() ?: LatLng(0.0, 0.0), 15f)
    }

    LaunchedEffect(latLngList) {
        if (latLngList?.isNotEmpty() == true) {
            val boundsBuilder = LatLngBounds.builder()
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

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(0.3f)
    ) {
        items(routesWithPoints ?: emptyList()) { routeItem ->
            Text(
                text = "${routeItem.route.activityName} - ${routeItem.route.timestamp.timestampToDate()}",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedRouteWithPoints = routeItem }
                    .padding(8.dp),
                color = if (selectedRouteWithPoints == routeItem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }

    Map(cameraPositionState, latLngList)
}

@Composable
private fun ColumnScope.Map(
    cameraPositionState: CameraPositionState,
    latLngList: List<LatLng>?
) {
    GoogleMap(
        modifier = Modifier
            .fillMaxWidth()
            .weight(0.7f),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = true),
        uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = true)
    ) {
        if (latLngList?.isNotEmpty() == true) {
            Polyline(points = latLngList)
        }
    }
}

@Composable
private fun CenteredContent(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), contentAlignment = Alignment.Center
    ) {
        content()
    }
}