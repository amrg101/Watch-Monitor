package com.amrg.watchinfo.utils

object Constants {
    const val ACTION_START_CONTINUOUS_MONITORING = "com.amrg.watchinfo.action.START_CONTINUOUS"
    const val ACTION_STOP_CONTINUOUS_MONITORING = "com.amrg.watchinfo.action.STOP_CONTINUOUS"
    const val ACTION_START_INCIDENT_RECORDING = "com.amrg.watchinfo.action.START_INCIDENT"
    const val ACTION_STOP_INCIDENT_RECORDING = "com.amrg.watchinfo.action.STOP_INCIDENT"

    const val NOTIFICATION_CHANNEL_ID = "WatchMonitor ServiceChannel"
    const val NOTIFICATION_ID = 101

    const val WEAR_MESSAGE_PATH_COMMAND = "/watchinfo/command"
    const val WEAR_MESSAGE_PATH_HEALTH_DATA = "/watchinfo/health_data"
    const val WEAR_MESSAGE_PATH_INCIDENT_DATA = "/watchinfo/incident_data"

    // Command keys for messages
    const val COMMAND_KEY = "command"
    const val COMMAND_START_INCIDENT = "start_incident"
    const val COMMAND_STOP_INCIDENT = "stop_incident" // Manual stop from phone

    const val INCIDENT_RECORDING_DURATION_MS = 30_000L // 30 seconds
    const val ACCELEROMETER_SAMPLING_INTERVAL_MS = 1000L // ~1 second
}