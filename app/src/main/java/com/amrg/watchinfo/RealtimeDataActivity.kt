package com.amrg.watchinfo

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.amrg.watchinfo.model.IncidentData
import com.amrg.watchinfo.model.WearIntegration
import com.amrg.watchinfo.service.WearIntegrationImpl
import com.amrg.watchinfo.service.WearableMonitoringService
import com.amrg.watchinfo.ui.screen.RealtimeDataScreen
import com.amrg.watchinfo.ui.theme.WatchInfoTheme
import kotlinx.coroutines.launch

class RealtimeDataActivity : ComponentActivity() {

    private val TAG = "RealtimeDataActivity"
    private var wearService: WearableMonitoringService? = null
    private var isBound = false

    private val serviceActiveState = mutableStateOf(false)
    private val heartRateState = mutableStateOf<Int?>(null)
    private val stepsState = mutableStateOf<Int?>(null)
    private val batteryLevelState = mutableStateOf<Int?>(null)

    private val incidentRecordingActiveState = mutableStateOf(false)
    private val lastIncidentDataState = mutableStateOf<IncidentData?>(null)

    private lateinit var wearIntegration: WearIntegration

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as WearableMonitoringService.LocalBinder
            wearService = binder.getService()
            isBound = true
            observeServiceData()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            wearService = null
            Log.d(TAG, "Service unbound")
            serviceActiveState.value = false
            heartRateState.value = null
            stepsState.value = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wearIntegration = WearIntegrationImpl.getInstance(applicationContext)

        setContent {
            WatchInfoTheme {
                RealtimeDataScreen(
                    isContinuousServiceActive = serviceActiveState.value,
                    heartRate = heartRateState.value,
                    steps = stepsState.value,
                    battery = batteryLevelState.value,
                    onToggleContinuousMonitoring = {
                        if (serviceActiveState.value) {
                            wearIntegration.stopContinuousMonitoring()
                        } else {
                            checkAndRequestContinuousPermissions { granted ->
                                if (granted) wearIntegration.startContinuousMonitoring()
                            }
                        }
                    },
                    isIncidentRecordingActive = incidentRecordingActiveState.value,
                    lastIncidentData = lastIncidentDataState.value,
                    onToggleIncidentRecording = {
                        if (incidentRecordingActiveState.value) {
                            wearIntegration.stopIncidentRecording()
                        } else {
                            checkAndRequestIncidentPermissions { granted ->
                                if (granted) wearIntegration.startIncidentRecording()
                            }
                        }
                    }
                )
            }
        }

        checkAndRequestContinuousPermissions {
            if (it) {
                wearIntegration.startContinuousMonitoring()
            } else {
                Log.w(TAG, "Cannot start monitoring, permissions missing.")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, WearableMonitoringService::class.java).also { intent ->
            val bound = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "Attempting to bind service in onStart, result: $bound")
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
            wearService = null // Clear reference
            Log.d(TAG, "Service unbound in onStop")
        }
    }

    private fun checkAndRequestContinuousPermissions(onResult: (Boolean) -> Unit) {
        val requiredPermissions = mutableListOf<String>()
        requiredPermissions.add(Manifest.permission.BODY_SENSORS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requiredPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            Log.i(TAG, "All continuous permissions already granted.")
            onResult(true)
        } else {
            Log.i(TAG, "Requesting continuous permissions: ${permissionsToRequest.joinToString()}")
            requestContinuousPermissionsLauncher.launch(permissionsToRequest)
        }
    }

    private fun checkAndRequestIncidentPermissions(onResult: (Boolean) -> Unit) {
        val requiredPermissions = mutableListOf<String>()
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            Log.i(TAG, "All incident permissions already granted.")
            onResult(true)
        } else {
            Log.i(TAG, "Requesting incident permissions: ${permissionsToRequest.joinToString()}")
            requestIncidentPermissionsLauncher.launch(permissionsToRequest)
        }
    }

    private val requestContinuousPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach {
                if (!it.value) {
                    allGranted = false
                    Log.w(TAG, "Permission not granted: ${it.key}")
                }
            }
            if (allGranted) {
                Log.i(TAG, "All continuous permissions granted by user.")
                if (!serviceActiveState.value) {
                    wearIntegration.startContinuousMonitoring()
                }
            } else {
                Log.e(TAG, "Not all continuous permissions were granted by user.")
            }
        }

    private val requestIncidentPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach {
                if (!it.value) {
                    allGranted = false
                    Log.w(TAG, "Incident Permission not granted: ${it.key}")
                }
            }
            if (allGranted) {
                Log.i(TAG, "All incident permissions granted by user.")
                // If user's intent was to start incident recording, try again
                if (!incidentRecordingActiveState.value) {
                    wearIntegration.startIncidentRecording()
                }
            } else {
                Log.e(TAG, "Not all incident permissions were granted by user.")
            }
        }


    private fun observeServiceData() {
        val currentService = wearService
        if (currentService == null) {
            Log.w(TAG, "observeServiceData called but service is null.")
            serviceActiveState.value = false
            return
        }

        Log.d(TAG, "Observing service data...")
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    currentService.isContinuousMonitoringActiveStateFlow.collect { isActive ->
                        Log.d(TAG, "UI Update - Service active state: $isActive")
                        serviceActiveState.value = isActive
                    }
                }
                launch {
                    currentService.heartRateStateFlow.collect { hr ->
                        Log.d(TAG, "UI Update - HR from service: $hr")
                        heartRateState.value = hr
                    }
                }
                launch {
                    currentService.stepsStateFlow.collect { st ->
                        Log.d(TAG, "UI Update - Steps from service: $st")
                        stepsState.value = st
                    }
                }
                launch {
                    currentService.batteryLevelStateFlow.collect { batt ->
                        Log.d(TAG, "UI Update - Battery from service: $batt")
                        batteryLevelState.value = batt
                    }
                }
                launch {
                    currentService.isIncidentRecordingActiveStateFlow.collect { isActive ->
                        Log.d(TAG, "UI Update - Incident Recording Active: $isActive")
                        incidentRecordingActiveState.value = isActive
                    }
                }
                launch {
                    currentService.lastIncidentDataStateFlow.collect { data ->
                        Log.d(TAG, "UI Update - Last Incident Data: ${data?.timestamp}")
                        lastIncidentDataState.value = data
                    }
                }
            }
        }
    }
}
