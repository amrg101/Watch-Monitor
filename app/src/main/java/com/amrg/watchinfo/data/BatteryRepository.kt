package com.amrg.watchinfo.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.amrg.watchinfo.service.WearableMonitoringService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class BatteryRepository(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onNewData: (WearableMonitoringService.DataType, Any) -> Unit
) {
    private val TAG = "BatteryRepository"
    private val _lastBatteryLevel = MutableStateFlow<Int?>(null)
    fun getLastBatteryLevel(): Int? = _lastBatteryLevel.value

    private val batteryLevelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val level: Int = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale: Int = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = if (level != -1 && scale != -1) {
                    (level * 100 / scale.toFloat()).toInt()
                } else {
                    null
                }
                batteryPct?.let { pct ->
                    if (_lastBatteryLevel.value != pct) {
                        _lastBatteryLevel.value = pct
                        Log.v(TAG, "Battery Level: $pct%")
                        coroutineScope.launch {
                            onNewData(WearableMonitoringService.DataType.BATTERY, pct)
                        }
                    }
                }
            }
        }
    }

    fun startBatteryMonitoring() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryLevelReceiver, filter)
        Log.d(TAG, "Battery monitoring started.")
        triggerInitialBatteryRead()
    }

    private fun triggerInitialBatteryRead() {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { iFilter ->
            context.registerReceiver(null, iFilter) // Sticky broadcast
        }
        batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = if (level != -1 && scale != -1) {
                (level * 100 / scale.toFloat()).toInt()
            } else {
                null
            }
            batteryPct?.let {
                _lastBatteryLevel.value = it
                coroutineScope.launch {
                    onNewData(WearableMonitoringService.DataType.BATTERY, it)
                }
            }
        }
    }

    fun stopBatteryMonitoring() {
        try {
            context.unregisterReceiver(batteryLevelReceiver)
            Log.d(TAG, "Battery monitoring stopped.")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Battery receiver not registered or already unregistered.")
        }
    }
}