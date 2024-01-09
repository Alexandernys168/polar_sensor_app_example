package mobappdev.example.sensorapplication.domain

/**
 * File: InternalSensorController.kt
 * Purpose: Defines the blueprint for the Internal Sensor Controller.
 * Author: Jitse van Esch
 * Created: 2023-09-21
 * Last modified: 2023-09-21
 */

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import mobappdev.example.sensorapplication.data.ElevationData

interface InternalSensorController {
    val currentLinAccUI: StateFlow<Triple<Double, Double, Double>?>
    val currentGyroUI: StateFlow<Triple<Double, Double, Double>?>

    val currentElevation: StateFlow<ElevationData?>
    val elevationList: StateFlow<List<ElevationData>>

    val streamingElevation: StateFlow<Boolean>
    val streamingGyro: StateFlow<Boolean>
    val streamingLinAcc: StateFlow<Boolean>

    fun startImuStream()
    fun stopImuStream()

    fun writeElevationListToFile(fileName: String)

    fun startGyroStream()
    fun stopGyroStream()
}