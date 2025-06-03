package com.amrg.watchinfo.service

import android.content.Context
import android.content.Intent
import com.amrg.watchinfo.model.WearIntegration
import com.amrg.watchinfo.utils.Constants

class WearIntegrationImpl(private val context: Context) : WearIntegration {

    override fun startContinuousMonitoring() {
        val intent = Intent(context, WearableMonitoringService::class.java).apply {
            action = Constants.ACTION_START_CONTINUOUS_MONITORING
        }
        context.startService(intent)
    }

    override fun stopContinuousMonitoring() {
        val intent = Intent(context, WearableMonitoringService::class.java).apply {
            action = Constants.ACTION_STOP_CONTINUOUS_MONITORING
        }
        context.startService(intent)
    }

    override fun startIncidentRecording() {
        // This might be triggered by the phone app via DataLayer,
        // or by a local UI element on the watch that uses this library.
        val intent = Intent(context, WearableMonitoringService::class.java).apply {
            action = Constants.ACTION_START_INCIDENT_RECORDING
        }
        context.startService(intent)
    }

    override fun stopIncidentRecording() {
        val intent = Intent(context, WearableMonitoringService::class.java).apply {
            action = Constants.ACTION_STOP_INCIDENT_RECORDING
        }
        context.startService(intent)
    }

    companion object {
        @Volatile
        private var INSTANCE: WearIntegration? = null

        fun getInstance(context: Context): WearIntegration {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WearIntegrationImpl(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}