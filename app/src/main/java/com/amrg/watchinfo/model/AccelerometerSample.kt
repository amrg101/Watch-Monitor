package com.amrg.watchinfo.model

data class AccelerometerSample(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestamp: Long // Milliseconds epoch
)