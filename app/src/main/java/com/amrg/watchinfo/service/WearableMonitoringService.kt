package com.amrg.watchinfo.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.amrg.watchinfo.R
import com.amrg.watchinfo.communication.DataLayerManager
import com.amrg.watchinfo.data.BatteryRepository
import com.amrg.watchinfo.data.LocationRepository
import com.amrg.watchinfo.data.SensorRepository
import com.amrg.watchinfo.model.GpsPoint
import com.amrg.watchinfo.model.HealthData
import com.amrg.watchinfo.model.IncidentData
import com.amrg.watchinfo.utils.Constants
import com.amrg.watchinfo.utils.DateTimeUtils
import com.amrg.watchinfo.utils.PermissionUtils
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WearableMonitoringService : Service() {

    private val TAG = "WearableMonitoringSvc"
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var sensorRepository: SensorRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var batteryRepository: BatteryRepository
    private lateinit var dataLayerManager: DataLayerManager

    private var isContinuousMonitoringActive = false
    private var isIncidentRecordingActive = false
    private var incidentRecordingJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastSentIncidentTimestamp: String? = null // To avoid re-sending the same incident

    private val gson = Gson()

    private val _isContinuousMonitoringActiveStateFlow = MutableStateFlow(false)
    val isContinuousMonitoringActiveStateFlow: StateFlow<Boolean> =
        _isContinuousMonitoringActiveStateFlow.asStateFlow()

    private val _isIncidentRecordingActiveStateFlow = MutableStateFlow(false)
    val isIncidentRecordingActiveStateFlow: StateFlow<Boolean> =
        _isIncidentRecordingActiveStateFlow.asStateFlow()

    private val _lastIncidentDataStateFlow = MutableStateFlow<IncidentData?>(null)
    val lastIncidentDataStateFlow: StateFlow<IncidentData?> =
        _lastIncidentDataStateFlow.asStateFlow()

    private val _heartRateStateFlow = MutableStateFlow<Int?>(null)
    val heartRateStateFlow: StateFlow<Int?> = _heartRateStateFlow.asStateFlow()

    private val _stepsStateFlow = MutableStateFlow<Int?>(null)
    val stepsStateFlow: StateFlow<Int?> = _stepsStateFlow.asStateFlow()

    private val _batteryLevelStateFlow = MutableStateFlow<Int?>(null)
    val batteryLevelStateFlow: StateFlow<Int?> = _batteryLevelStateFlow.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): WearableMonitoringService = this@WearableMonitoringService
    }

    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG::SvcWakeLock")

        dataLayerManager = DataLayerManager(
            applicationContext,
            Wearable.getMessageClient(applicationContext),
            Wearable.getDataClient(applicationContext),
            serviceScope,
            ::handleCommandFromPhone
        )
        sensorRepository = SensorRepository(applicationContext, serviceScope, ::onNewSensorData)
        locationRepository = LocationRepository(applicationContext, serviceScope)
        batteryRepository = BatteryRepository(applicationContext, serviceScope, ::onNewBatteryData)

        startForegroundServiceWithNotification(getString(R.string.default_notification_title))
        dataLayerManager.startListening()
        batteryRepository.startBatteryMonitoring() // Battery is always monitored
    }

    private fun startForegroundServiceWithNotification(contentText: String) {
        createNotificationChannel()
        val notification = buildNotification(contentText)
        try {
            startForeground(Constants.NOTIFICATION_ID, notification)
            Log.d(TAG, "Service started in foreground: $contentText")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val notificationIntent =
            packageManager.getLaunchIntentForPackage(packageName) // Or specific activity
        val pendingIntentFlags =
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.default_notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @SuppressLint("NotificationPermission")
    private fun updateNotification(contentText: String) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Constants.NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW // Low importance for background services
        ).apply {
            description = getString(R.string.notification_channel_description)
            setSound(null, null) // No sound for this channel
            enableVibration(false) // No vibration
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received action: ${intent?.action}, startId: $startId")
        intent?.action?.let { action ->
            when (action) {
                Constants.ACTION_START_CONTINUOUS_MONITORING -> startContinuousMonitoringInternal()
                Constants.ACTION_STOP_CONTINUOUS_MONITORING -> stopContinuousMonitoringInternal()
                Constants.ACTION_START_INCIDENT_RECORDING -> startIncidentRecordingInternal()
                Constants.ACTION_STOP_INCIDENT_RECORDING -> stopIncidentRecordingInternal(manualStop = true)
            }
        }
        return START_STICKY
    }

    private fun handleCommandFromPhone(command: String, payload: ByteArray?) {
        Log.d(TAG, "Command from phone: $command")
        when (command) {
            Constants.COMMAND_START_INCIDENT -> startIncidentRecordingInternal()
            Constants.COMMAND_STOP_INCIDENT -> stopIncidentRecordingInternal(manualStop = true)
        }
    }

    private fun startContinuousMonitoringInternal() {
        if (!PermissionUtils.hasRequiredContinuousPermissions(this)) {
            Log.w(TAG, "Missing permissions for continuous monitoring. Cannot start.")
            // Notify phone app or local UI if possible. For a library, this might be an error state.
            return
        }
        if (isContinuousMonitoringActive) {
            Log.d(TAG, "Continuous monitoring already active.")
            return
        }
        Log.i(TAG, "Starting continuous monitoring...")
        isContinuousMonitoringActive = true
        _isContinuousMonitoringActiveStateFlow.value = true

        sensorRepository.startHeartRateMonitoring()
        sensorRepository.startStepCountMonitoring()
        updateNotification(getString(R.string.monitoring_active_notification_text))
    }

    private fun stopContinuousMonitoringInternal() {
        if (!isContinuousMonitoringActive) {
            Log.d(TAG, "Continuous monitoring not active.")
            return
        }
        Log.i(TAG, "Stopping continuous monitoring.")
        isContinuousMonitoringActive = false
        _isContinuousMonitoringActiveStateFlow.value = false

        sensorRepository.stopHeartRateMonitoring()
        sensorRepository.stopStepCountMonitoring()

        _heartRateStateFlow.value = null
        _stepsStateFlow.value = null

        updateNotification(getString(R.string.default_notification_title))
    }

    private fun startIncidentRecordingInternal() {
        if (!PermissionUtils.hasRequiredIncidentPermissions(this)) { // Check location perm
            Log.w(TAG, "Missing permissions for incident recording. Cannot start.")
            _isIncidentRecordingActiveStateFlow.value = false
            return
        }
        if (isIncidentRecordingActive) {
            Log.d(TAG, "Incident recording already active.")
            return
        }
        Log.i(TAG, "Starting incident recording...")
        isIncidentRecordingActive = true
        _isIncidentRecordingActiveStateFlow.value = true
        _lastIncidentDataStateFlow.value = null

        updateNotification(getString(R.string.monitoring_incident_notification_text))
        wakeLock?.acquire(Constants.INCIDENT_RECORDING_DURATION_MS + 5000L)

        val incidentStartTime = System.currentTimeMillis()
        var startGpsPoint: GpsPoint? = null

        incidentRecordingJob = serviceScope.launch {
            try {
                try {
                    val loc = locationRepository.getCurrentLocation() // Suspending function
                    loc?.let { startGpsPoint = GpsPoint(it.latitude, it.longitude) }
                    Log.d(TAG, "Incident start location: $startGpsPoint")
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting start location for incident", e)
                }

                sensorRepository.startAccelerometerMonitoringForIncident()

                delay(Constants.INCIDENT_RECORDING_DURATION_MS)

                sensorRepository.stopAccelerometerMonitoringForIncident()
                val collectedAccelData =
                    sensorRepository.getCollectedAccelerometerSamplesForIncident()
                Log.d(TAG, "Collected ${collectedAccelData.size} accel samples for incident.")


                var endGpsPoint: GpsPoint? = null
                try {
                    val loc = locationRepository.getCurrentLocation() // Suspending function
                    loc?.let { endGpsPoint = GpsPoint(it.latitude, it.longitude) }
                    Log.d(TAG, "Incident end location: $endGpsPoint")
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting end location for incident", e)
                }

                val incident = IncidentData(
                    deviceId = getDeviceIdentifier(),
                    deviceName = Build.MODEL,
                    startPoint = startGpsPoint,
                    endPoint = endGpsPoint,
                    accelerometerData = ArrayList(sensorRepository.getCollectedAccelerometerSamplesForIncident()),
                    timestamp = DateTimeUtils.getISO8601Timestamp(incidentStartTime),
                    source = "wear_os"
                )
                Log.d(TAG, "Incident recorded (for UI & Phone): ${gson.toJson(incident)}")
                _lastIncidentDataStateFlow.value = incident // Update UI StateFlow
                dataLayerManager.sendIncidentData(incident) // Send to phone

            } catch (e: CancellationException) {
                Log.i(TAG, "Incident recording job cancelled.", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error during incident recording", e)
                _lastIncidentDataStateFlow.value =
                    IncidentData( // Populate with error state if needed
                        deviceId = getDeviceIdentifier(),
                        deviceName = Build.MODEL,
                        startPoint = null,
                        endPoint = null,
                        accelerometerData = emptyList(),
                        timestamp = DateTimeUtils.getISO8601Timestamp(incidentStartTime),
                        source = "wear_os_error" // Indicate error
                    )
            } finally {
                if (isActive) {
                    stopIncidentRecordingInternal(manualStop = false)
                }
            }
        }
    }

    private fun stopIncidentRecordingInternal(manualStop: Boolean) {
        if (!isIncidentRecordingActive && incidentRecordingJob == null) {
            Log.d(TAG, "Incident recording not active or already stopping.")
            return
        }
        Log.i(TAG, "Stopping incident recording. Manual stop: $manualStop")

        incidentRecordingJob?.cancel()
        incidentRecordingJob = null

        sensorRepository.stopAccelerometerMonitoringForIncident()

        isIncidentRecordingActive = false
        _isIncidentRecordingActiveStateFlow.value = false

        updateNotification(
            if (isContinuousMonitoringActive) getString(R.string.monitoring_active_notification_text)
            else getString(R.string.default_notification_title)
        )
        wakeLock?.let { if (it.isHeld) it.release() }
        Log.d(TAG, "Incident recording stopped and WakeLock released.")
    }


    private fun onNewSensorData(type: DataType, data: Any?) {
        var dataChangedForPhone = false
        when (type) {
            DataType.HEART_RATE -> {
                val hr = data as? Int
                if (_heartRateStateFlow.value != hr) {
                    _heartRateStateFlow.value = hr
                    if (isContinuousMonitoringActive && hr != null) dataChangedForPhone = true
                }
            }

            DataType.STEPS -> {
                val steps = data as? Int
                if (_stepsStateFlow.value != steps) {
                    _stepsStateFlow.value = steps
                    if (isContinuousMonitoringActive && steps != null) dataChangedForPhone = true
                }
            }

            else -> { /* Should not happen from SensorRepository */
            }
        }

        if (dataChangedForPhone) {
            sendAggregatedHealthData("Sensor Update")
        }
    }

    private fun onNewBatteryData(type: DataType, data: Any) {
        if (type == DataType.BATTERY) {
            val battery = data as? Int
            if (_batteryLevelStateFlow.value != battery) {
                _batteryLevelStateFlow.value = battery
                // Always send battery updates if changed, regardless of continuous monitoring status
                if (battery != null) sendAggregatedHealthData("Battery Update")
            }
        }
    }

    private fun sendAggregatedHealthData(reason: String) {
        val currentHeartRate = if (isContinuousMonitoringActive) _heartRateStateFlow.value else null
        val currentSteps = if (isContinuousMonitoringActive) _stepsStateFlow.value else null
        val currentBattery = _batteryLevelStateFlow.value


        var incidentToSend: IncidentData? = null
        val currentIncident = _lastIncidentDataStateFlow.value
        if (currentIncident != null && currentIncident.timestamp != lastSentIncidentTimestamp) {
            incidentToSend = currentIncident
            lastSentIncidentTimestamp = currentIncident.timestamp // Mark as "to be sent"
        }

        if (currentBattery != null || (isContinuousMonitoringActive && (currentHeartRate != null || currentSteps != null)) || incidentToSend != null) {
            val healthData = HealthData(
                deviceId = getDeviceIdentifier(),
                deviceName = Build.MODEL,
                heartRate = currentHeartRate,
                steps = currentSteps,
                batteryLevel = currentBattery,
                timestamp = DateTimeUtils.getISO8601Timestamp(System.currentTimeMillis()),
                recentIncident = incidentToSend
            )
            Log.v(
                TAG,
                "$reason - Sending Health Data (Incident attached: ${incidentToSend != null}): ${
                    gson.toJson(healthData)
                }"
            )
            dataLayerManager.sendHealthData(healthData)
        }
    }


    @SuppressLint("HardwareIds")
    private fun getDeviceIdentifier(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_wear_device_id"
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service onBind from: ${intent?.component?.className}")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Service onUnbind from: ${intent?.component?.className}")
        // The service is a foreground service and should continue running even if all clients unbind.
        return true // Allow rebind
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "Service onDestroy. Cleaning up...")
        stopContinuousMonitoringInternal()
        stopIncidentRecordingInternal(manualStop = false)

        batteryRepository.stopBatteryMonitoring()
        dataLayerManager.stopListening()
        serviceJob.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        Log.i(TAG, "WearableMonitoringService destroyed and cleaned up.")
    }

    // Enum for differentiating data types from repositories
    enum class DataType { HEART_RATE, STEPS, BATTERY, ACCELEROMETER_INCIDENT_BATCH }
}
