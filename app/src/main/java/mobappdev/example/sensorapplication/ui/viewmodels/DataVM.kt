package mobappdev.example.sensorapplication.ui.viewmodels

/**
 * File: DataVM.kt
 * Purpose: Defines the view model of the data screen.
 *          Uses Dagger-Hilt to inject a controller model
 * Author: Jitse van Esch
 * Created: 2023-07-08
 * Last modified: 2023-07-11
 */

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import mobappdev.example.sensorapplication.data.ElevationData
import mobappdev.example.sensorapplication.domain.InternalSensorController
import mobappdev.example.sensorapplication.domain.PolarController
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Context
import java.lang.ref.WeakReference

@HiltViewModel
class DataVM @Inject constructor(
    private val polarController: PolarController,
    private val internalSensorController: InternalSensorController,
    //private val context: Context
) : ViewModel() {

    //private val contextRef: WeakReference<Context> = WeakReference(context)

    private val gyroDataFlow = internalSensorController.currentGyroUI
    private val hrDataFlow = polarController.currentHR

    private val externalElevation = polarController.currentElevation
    private val internalElevation = internalSensorController.currentElevation

    private val linAccFlow = polarController.currentLinAcc
    private val internalLinAccFlow = internalSensorController.currentLinAccUI

    // Combine the two data flows
    val combinedDataFlow = combine(
        hrDataFlow,
        gyroDataFlow,
        //linAccFlow,
        //internalLinAccFlow,
        externalElevation,
        internalElevation
    ) { values ->
        val hr: Int? = values[0] as? Int
        val gyro: Triple<Float, Float, Float>? = values[1] as? Triple<Float, Float, Float>
        //val linAcc: Triple<Double, Double, Double>? = values[2] as? Triple<Double, Double, Double>
        //val internalLinAcc: Triple<Float, Float, Float>? = values[3] as? Triple<Float, Float, Float>
        val exElevation: ElevationData? = values[2] as? ElevationData
        val inElevation: ElevationData? = values[3] as? ElevationData

        when {
            hr != null -> CombinedSensorData.HrData(hr)
            gyro != null -> CombinedSensorData.GyroData(gyro)
            //linAcc != null -> CombinedSensorData.ExternalLinAcc(linAcc)
            //internalLinAcc != null -> CombinedSensorData.InternalLinAcc(internalLinAcc)
            exElevation != null -> CombinedSensorData.ExternalElevation(exElevation)
            inElevation != null -> CombinedSensorData.InternalElevation(inElevation)
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)


    private val _state = MutableStateFlow(DataUiState())
    val state = combine(
        polarController.hrList,
        //polarController.linAccList,
        polarController.elevationList,
        internalSensorController.elevationList,
        polarController.connected,
        _state
    ) { hrList, /*linAccList*/ externalCurrentElevation, internalCurrentElevation, connected, state ->
        state.copy(
            hrList = hrList,
            // accList = linAccList,
            internalElevationList = internalCurrentElevation,
            externalElevationList = externalCurrentElevation,
            connected = connected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)

    private var streamType: StreamType? = null

    private var timerJob: Job? = null

    private val _deviceId = MutableStateFlow("")
    val deviceId: StateFlow<String>
        get() = _deviceId.asStateFlow()


    private fun startTimer() {
        timerJob?.cancel() // Cancel any existing timer job
        timerJob = viewModelScope.launch {
            for (i in 15 downTo 0) {
                _state.update { currentState ->
                    currentState.copy(timer = i)
                }
                delay(1000) // Delay for 1 second
            }
            stopDataStream()
        }
    }

    fun chooseSensor(deviceId: String) {
        _deviceId.update { deviceId }
    }

    fun connectToSensor() {
        polarController.connectToDevice(_deviceId.value)
    }

    fun disconnectFromSensor() {
        stopDataStream()
        polarController.disconnectFromDevice(_deviceId.value)
    }

    fun startExternalAcc() {
        polarController.startLinAccStreaming(_deviceId.value)
        streamType = StreamType.FOREIGN_ACC
        _state.update { it.copy(measuring = true) }
        startTimer()
    }

    fun startInternalAcc() {
        internalSensorController.startImuStream()
        streamType = StreamType.LOCAL_ACC
        _state.update { it.copy(measuring = true) }
        startTimer()
    }

    fun startHr() {
        polarController.startHrStreaming(_deviceId.value)
        streamType = StreamType.FOREIGN_HR
        _state.update { it.copy(measuring = true) }
    }

    fun startGyro() {

        internalSensorController.startGyroStream()
        streamType = StreamType.LOCAL_GYRO

        _state.update { it.copy(measuring = true) }
        startTimer()

    }

    fun stopDataStream() {
        //val context = contextRef.get()
        timerJob?.cancel()
       // if (context != null) {
            when (streamType) {
                StreamType.LOCAL_GYRO -> internalSensorController.stopGyroStream()
                StreamType.FOREIGN_HR -> polarController.stopHrStreaming()
                StreamType.FOREIGN_ACC -> {
                    polarController.stopLinAccStreaming()
                }

                StreamType.LOCAL_ACC -> {
                    internalSensorController.stopImuStream()
                }

                else -> {} // Do nothing
            }
            _state.update { it.copy(measuring = false) }
        }
    //}
}

data class DataUiState(
    val hrList: List<Int> = emptyList(),
    //val accList: List<Triple<Double, Double, Double>> = emptyList(),
    val externalElevationList: List<ElevationData> = emptyList(),
    val internalElevationList: List<ElevationData> = emptyList(),
    val connected: Boolean = false,
    val measuring: Boolean = false,
    val timer: Int = 10
)

enum class StreamType {
    LOCAL_GYRO, LOCAL_ACC, FOREIGN_HR, FOREIGN_ACC
}

sealed class CombinedSensorData {
    data class GyroData(val gyro: Triple<Float, Float, Float>?) : CombinedSensorData()
    data class HrData(val hr: Int?) : CombinedSensorData()

    //data class InternalLinAcc(val internalLinAcc: Triple<Float, Float, Float>?) : CombinedSensorData()

    data class ExternalElevation(val externalElevation: ElevationData?) : CombinedSensorData()

    data class InternalElevation(val internalElevation: ElevationData?) : CombinedSensorData()

    //data class ExternalLinAcc(val linAcc: Triple<Double, Double, Double>?) : CombinedSensorData()
}