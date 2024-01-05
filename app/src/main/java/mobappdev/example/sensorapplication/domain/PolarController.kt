package mobappdev.example.sensorapplication.domain

/**
 * File: PolarController.kt
 * Purpose: Defines the blueprint for the polar controller model
 * Author: Jitse van Esch
 * Created: 2023-07-08
 * Last modified: 2023-07-11
 */


import kotlinx.coroutines.flow.StateFlow

interface PolarController {
    val currentHR: StateFlow<Int?>
    val hrList: StateFlow<List<Int>>

    val currentLinAcc: StateFlow<Triple<Int,Int,Int>?>
    val linAccList: StateFlow<List<Triple<Int,Int,Int>>>

    val connected: StateFlow<Boolean>
    val measuring: StateFlow<Boolean>

    fun connectToDevice(deviceId: String)
    fun disconnectFromDevice(deviceId: String)

    fun startLinAccStreaming(deviceId: String)
    fun stopLinAccStreaming()

    fun startHrStreaming(deviceId: String)
    fun stopHrStreaming()
}