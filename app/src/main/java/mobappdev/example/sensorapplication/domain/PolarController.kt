package mobappdev.example.sensorapplication.domain

/**
 * File: PolarController.kt
 * Purpose: Defines the blueprint for the polar controller model
 * Author: Jitse van Esch
 * Created: 2023-07-08
 * Last modified: 2023-07-11
 */


import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import mobappdev.example.sensorapplication.data.ElevationData

interface PolarController {
    val currentHR: StateFlow<Int?>
    val hrList: StateFlow<List<Int>>

    val currentLinAcc: StateFlow<Triple<Double,Double,Double>?>
    val linAccList: StateFlow<List<Triple<Double,Double,Double>>>

    val currentElevation: StateFlow<ElevationData?>
    val elevationList: StateFlow<List<ElevationData>>

    val connected: StateFlow<Boolean>
    val measuring: StateFlow<Boolean>

    fun connectToDevice(deviceId: String)
    fun disconnectFromDevice(deviceId: String)

    fun startLinAccStreaming(deviceId: String)
    fun stopLinAccStreaming()

    fun writeElevationListToFile( fileName: String)


    fun startHrStreaming(deviceId: String)
    fun stopHrStreaming()
}