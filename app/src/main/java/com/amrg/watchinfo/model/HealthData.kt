package com.amrg.watchinfo.model

data class HealthData(
    val deviceId: String,
    val deviceName: String,
    var heartRate: Int?,
    var steps: Int?,
    var batteryLevel: Int?,
    val timestamp: String, // ISO 8601 format
    val recentIncident: IncidentData? = null
)