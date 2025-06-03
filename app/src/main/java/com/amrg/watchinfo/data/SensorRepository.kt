package com.amrg.watchinfo.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.amrg.watchinfo.model.AccelerometerSample
import com.amrg.watchinfo.service.WearableMonitoringService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

class SensorRepository(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onNewSensorData: (WearableMonitoringService.DataType, Any?) -> Unit
) : SensorEventListener {

    private val TAG = "SensorRepository"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var heartRateSensor: Sensor? = null
    private var stepCounterSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private var accelerometerSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastReportedHrTimeMs: Long = 0L
    private var lastReportedHrValue: Int? = null
    private val hrDebounceIntervalMs: Long = 3000L // Only report HR at most every 3 seconds
    private val hrSignificantChangeDebounce: Int = 2 // Or if it changes by more than 2 bpm

    private var isHeartRateListenerActive = false
    private var isStepCounterListenerActive = false
    private var isAccelerometerListenerActive = false


    private val collectedAccelSamplesForIncident = mutableListOf<AccelerometerSample>()

    init {
        initializeSensors()
    }

    private fun initializeSensors() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BODY_SENSORS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
            if (heartRateSensor == null) Log.w(
                TAG,
                "Heart rate sensor (TYPE_HEART_RATE) not available."
            )
            else Log.i(TAG, "Heart rate sensor initialized: ${heartRateSensor?.name}")
        } else {
            Log.w(TAG, "BODY_SENSORS permission not granted. HR sensor not initialized.")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "ACTIVITY_RECOGNITION permission not granted for step counter.")
        } // Step counter sensor init remains

        if (stepCounterSensor == null) Log.w(TAG, "Step counter sensor not available")
        if (accelerometerSensor == null) Log.w(TAG, "Accelerometer sensor not available")
    }


    fun startHeartRateMonitoring() {
        if (isHeartRateListenerActive) {
            Log.d(TAG, "HR monitoring already active.")
            return
        }
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BODY_SENSORS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Cannot start HR: BODY_SENSORS permission denied.")
            return
        }
        heartRateSensor?.let { sensor ->
            val registered =
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            if (registered) {
                isHeartRateListenerActive = true
                lastReportedHrTimeMs = 0L // Reset debounce timer on start
                lastReportedHrValue = null // Reset last value
                Log.i(TAG, "Heart rate monitoring started for ${sensor.name}")
            } else {
                Log.e(TAG, "Failed to register HR listener.")
            }
        } ?: Log.e(TAG, "Cannot start HR monitoring: sensor is null.")
    }

    fun stopHeartRateMonitoring() {
        if (!isHeartRateListenerActive) {
            Log.d(TAG, "HR monitoring not active.")
            return
        }
        heartRateSensor?.let {
            sensorManager.unregisterListener(this, it)
            isHeartRateListenerActive = false
            Log.i(TAG, "Heart rate monitoring stopped.")
        }
    }

    fun startStepCountMonitoring() {
        if (isStepCounterListenerActive) {
            Log.d(TAG, "Step count already active."); return
        }
        stepCounterSensor?.let {
            val registered =
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            if (registered) isStepCounterListenerActive = true
            Log.d(TAG, "Step count monitoring started: $registered")
        } ?: Log.e(TAG, "Cannot start Step monitoring: sensor unavailable.")
    }

    fun stopStepCountMonitoring() {
        if (!isStepCounterListenerActive) {
            Log.d(TAG, "Step count not active."); return
        }
        stepCounterSensor?.let {
            sensorManager.unregisterListener(this, it)
            isStepCounterListenerActive = false
            Log.d(TAG, "Step count monitoring stopped.")
        }
    }

    fun startAccelerometerMonitoringForIncident() { // Removed onSample callback, not used
        if (isAccelerometerListenerActive) {
            Log.d(TAG, "Accel for incident already active."); return
        }
        collectedAccelSamplesForIncident.clear()
        accelerometerSensor?.let {
            val registered =
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            if (registered) isAccelerometerListenerActive = true
            Log.d(TAG, "Accelerometer for incident started: $registered")
        } ?: Log.e(TAG, "Cannot start Accelerometer for incident: sensor unavailable.")
    }

    fun stopAccelerometerMonitoringForIncident() {
        if (!isAccelerometerListenerActive) {
            Log.d(TAG, "Accel for incident not active."); return
        }
        accelerometerSensor?.let {
            sensorManager.unregisterListener(this, it)
            isAccelerometerListenerActive = false
            Log.d(TAG, "Accelerometer for incident stopped.")
        }
    }

    fun getCollectedAccelerometerSamplesForIncident(): List<AccelerometerSample> {
        return ArrayList(collectedAccelSamplesForIncident)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        coroutineScope.launch {
            when (event.sensor.type) {
                Sensor.TYPE_HEART_RATE -> {
                    if (!isHeartRateListenerActive) return@launch

                    val rawHrValue = event.values[0]
                    val accuracy = event.accuracy
                    val currentTimeMs = System.currentTimeMillis()

                    Log.v(TAG, "Raw HR Event: Value=${rawHrValue}, Accuracy=${accuracy}")

                    if (rawHrValue > 0.0f && accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                        val currentHr = rawHrValue.toInt()
                        val significantChange = lastReportedHrValue == null || abs(
                            (lastReportedHrValue
                                ?: (currentHr + hrSignificantChangeDebounce + 1)) - currentHr
                        ) >= hrSignificantChangeDebounce
                        val timePassed =
                            (currentTimeMs - lastReportedHrTimeMs) >= hrDebounceIntervalMs

                        if (significantChange || timePassed) {
                            Log.v(
                                TAG,
                                "Debounced HR: $currentHr (Prev: $lastReportedHrValue, SigChange: $significantChange, TimePassed: $timePassed)"
                            )
                            lastReportedHrValue = currentHr
                            lastReportedHrTimeMs = currentTimeMs
                            onNewSensorData(
                                WearableMonitoringService.DataType.HEART_RATE,
                                currentHr
                            )
                        } else {
                            Log.v(TAG, "HR event skipped by debounce: $currentHr")
                        }
                    } else {
                        // If HR is 0, unreliable, or off-body, and the last reported value was not null,
                        // report null to indicate loss of valid reading.
                        if (lastReportedHrValue != null) {
                            Log.w(
                                TAG,
                                "HR sensor reported 0, unreliable, or off-body. Reporting null. Raw: $rawHrValue, Acc: $accuracy"
                            )
                            lastReportedHrValue = null // Reset so next valid reading is reported
                            lastReportedHrTimeMs =
                                currentTimeMs // Allow next valid to report sooner
                            onNewSensorData(WearableMonitoringService.DataType.HEART_RATE, null)
                        } else {
                            Log.v(
                                TAG,
                                "HR sensor still reporting 0/unreliable, already reported null."
                            )
                        }
                    }
                }

                Sensor.TYPE_STEP_COUNTER -> {
                    if (!isStepCounterListenerActive) return@launch
                    val steps = event.values[0].toInt()
                    // No debouncing for step counter as it's usually cumulative and event-driven
                    onNewSensorData(WearableMonitoringService.DataType.STEPS, steps)
                }

                Sensor.TYPE_ACCELEROMETER -> {
                    if (!isAccelerometerListenerActive) return@launch
                    val sample = AccelerometerSample(
                        x = event.values[0],
                        y = event.values[1],
                        z = event.values[2],
                        timestamp = System.currentTimeMillis()
                    )
                    if (collectedAccelSamplesForIncident.size < 40) { // Slightly more than 30-35 for buffer
                        collectedAccelSamplesForIncident.add(sample)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        sensor ?: return
        Log.d(TAG, "Sensor accuracy changed: ${sensor.name} to $accuracy")
        if (sensor.type == Sensor.TYPE_HEART_RATE) {
            if (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_LOW && isHeartRateListenerActive) {
                // If accuracy becomes unreliable, and we were previously reporting valid HR,
                // send a null to signal this change.
                coroutineScope.launch {
                    if (lastReportedHrValue != null) { // Only if we were reporting valid HR
                        Log.w(TAG, "HR accuracy became unreliable. Reporting null.")
                        lastReportedHrValue = null
                        lastReportedHrTimeMs = System.currentTimeMillis() // Reset time
                        onNewSensorData(WearableMonitoringService.DataType.HEART_RATE, null)
                    }
                }
            }
        }
    }
}