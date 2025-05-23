package com.example.activityrecognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
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
import android.content.SharedPreferences
import androidx.annotation.RequiresPermission

class MainActivity : ComponentActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var _currentActivity by mutableStateOf("Unknown")
    private var _activityEvents by mutableStateOf<List<String>>(emptyList())

    @SuppressLint("MissingPermission")
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        FileLogger.d("Permission result: $isGranted")
        if (isGranted) {
            startActivityRecognition()
        } else {
            FileLogger.w("Activity recognition permission denied")
        }
    }

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FileLogger.d("onCreate")

        sharedPreferences = getSharedPreferences("activity_events", MODE_PRIVATE)

        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED -> {
                FileLogger.d("Permission already granted")
                startActivityRecognition()
            }
            else -> {
                FileLogger.d("Requesting permission")
                requestPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }

        loadSavedEvents()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ActivityTrackerScreen(
                        currentActivity = _currentActivity,
                        activityEvents = _activityEvents
                    )
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    private fun startActivityRecognition() {
        FileLogger.d("Starting activity recognition service")
        ActivityRecognitionProvider().startActivityRecognition(applicationContext)

    }

    private fun loadSavedEvents() {
        FileLogger.d("Loading saved events")
        val events = sharedPreferences.getString("events", "")?.split("\n") ?: emptyList()
        _activityEvents = events
        _currentActivity = sharedPreferences.getString("current_activity", "Unknown") ?: "Unknown"
        FileLogger.d("Loaded ${events.size} events")
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        loadSavedEvents()
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "events" || key == "current_activity") {
            FileLogger.d("SharedPreferences changed, reloading events for key: $key")
            loadSavedEvents()
        }
    }
}

@Composable
fun ActivityTrackerScreen(
    currentActivity: String,
    activityEvents: List<String>
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
                    text = "Current Activity",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentActivity,
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }

        // Activity History
        Text(
            text = "Activity History",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
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
        }
    }
}