package com.amrg.watchinfo.model

interface WearIntegration {
    fun startContinuousMonitoring()
    fun stopContinuousMonitoring()
    fun startIncidentRecording()
    fun stopIncidentRecording()
}
