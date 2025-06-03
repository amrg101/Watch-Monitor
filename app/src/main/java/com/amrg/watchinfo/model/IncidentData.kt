package com.amrg.watchinfo.model

data class IncidentData(
    val deviceId: String,
    val deviceName: String,
    val startPoint: GpsPoint?,
    val endPoint: GpsPoint?,
    val accelerometerData: List<AccelerometerSample>,
    val timestamp: String, // ISO 8601 format
    val source: String = "wear_os"
)