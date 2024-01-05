package mobappdev.example.sensorapplication.data

/**
 * File: AndroidPolarController.kt
 * Purpose: Implementation of the PolarController Interface.
 *          Communicates with the polar API
 * Author: Jitse van Esch
 * Created: 2023-07-08
 * Last modified: 2023-07-11
 */

import android.content.Context
import android.util.Log
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarSensorSetting
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import mobappdev.example.sensorapplication.domain.PolarController
import java.util.UUID
import com.polar.sdk.api.model.*

import kotlin.math.atan2

class AndroidPolarController (
    private val context: Context,
): PolarController {

    private val api: PolarBleApi by lazy {
        // Notice all features are enabled
        PolarBleApiDefaultImpl.defaultImplementation(
            context = context,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO
            )
        )
    }





    private var hrDisposable: Disposable? = null

    private var linAccDisposable: Disposable? = null

    private val TAG = "AndroidPolarController"

    private val _currentLinAccUI = MutableStateFlow<Triple<Int, Int, Int>?>(null)
    private val currentLinAccUI: StateFlow<Triple<Int, Int, Int>?>
        get() = _currentLinAccUI.asStateFlow()

    private var _currentLinAcc= MutableStateFlow<Triple<Int,Int,Int>?>(null)


    override val currentLinAcc: StateFlow<Triple<Int, Int, Int>?>
        get() = _currentLinAcc.asStateFlow()

    private val _elevations = MutableStateFlow<List<ElevationData>?>(emptyList())

    val elevations: StateFlow<List<ElevationData>?>
        get() = _elevations.asStateFlow()

    // Function to update elevations
    fun updateElevations(newElevations: List<ElevationData>) {
        _elevations.value = newElevations
    }

    private val _linAccList = MutableStateFlow<List<Triple<Int, Int, Int>>>(emptyList())
    override val linAccList: StateFlow<List<Triple<Int,Int,Int>>>
        get() = _linAccList.asStateFlow()



    private val _currentHR = MutableStateFlow<Int?>(null)
    override val currentHR: StateFlow<Int?>
        get() = _currentHR.asStateFlow()




    private val _hrList = MutableStateFlow<List<Int>>(emptyList())
    override val hrList: StateFlow<List<Int>>
        get() = _hrList.asStateFlow()

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean>
        get() = _connected.asStateFlow()

    private val _measuring = MutableStateFlow(false)
    override val measuring: StateFlow<Boolean>
        get() = _measuring.asStateFlow()

    init {
        api.setPolarFilter(false)

        val enableSdkLogs = false
        if(enableSdkLogs) {
            api.setApiLogger { s: String -> Log.d("Polar API Logger", s) }
        }

        api.setApiCallback(object: PolarBleApiCallback() {
            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.d(TAG, "BATTERY LEVEL: $level")
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "CONNECTED: ${polarDeviceInfo.deviceId}")
                _connected.update { true }
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "CONNECTING: ${polarDeviceInfo.deviceId}")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "DISCONNECTED: ${polarDeviceInfo.deviceId}")
                _connected.update { false }
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                Log.d(TAG, "DIS INFO uuid: $uuid value: $value")
            }
        })
    }

    override fun connectToDevice(deviceId: String) {
        try {
            api.connectToDevice(deviceId)
        } catch (polarInvalidArgument: PolarInvalidArgument) {
            Log.e(TAG, "Failed to connect to $deviceId.\n Reason $polarInvalidArgument")
        }
    }

    override fun disconnectFromDevice(deviceId: String) {
        try {
            api.disconnectFromDevice(deviceId)
        } catch (polarInvalidArgument: PolarInvalidArgument) {
            Log.e(TAG, "Failed to disconnect from $deviceId.\n Reason $polarInvalidArgument")
        }
    }


    override fun startLinAccStreaming(deviceId: String) {
        val settings: MutableMap<PolarSensorSetting.SettingType, Int> = mutableMapOf()
        settings[PolarSensorSetting.SettingType.SAMPLE_RATE] = 52
        settings[PolarSensorSetting.SettingType.RESOLUTION] = 16
        settings[PolarSensorSetting.SettingType.RANGE] = 8
        settings[PolarSensorSetting.SettingType.CHANNELS] = 3

        val polarSensorSetting = PolarSensorSetting(settings)

        var zTanBefore : Double = 0.0

        var offset = 0L

        val isDisposed = linAccDisposable?.isDisposed ?: true
        if(isDisposed) {
            _measuring.update { true }
            linAccDisposable =

                api.startAccStreaming(deviceId,polarSensorSetting )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {linAccData: PolarAccelerometerData ->
                        for (sample in linAccData.samples) {
                            val angleZTan = Math.toDegrees(atan2(sample.y.toDouble(), sample.x.toDouble()))

                            val filteredAngleZ = filterEWMA(angleZTan, zTanBefore)

                            // Use filteredAngleZ for further processing or UI updates
                            Log.d(TAG, "Filtered Angle Z: $filteredAngleZ + TimeStamp: ${sample.timeStamp}")

                            // Update zTanBefore for the next iteration
                            zTanBefore = filteredAngleZ

                            _elevations.update {
                                it?.plus(ElevationData(angleZTan.toInt(), sample.timeStamp))
                            }


                        }

                    },
                    {error: Throwable ->
                        Log.e(TAG, "Acc stream failed, reaseon $error")
                    },
                    {
                        Log.d(TAG, "Acc stream complete")
                    }
                )
        } else {
            Log.d(TAG, "Already streaming")
        }



    }

    override fun stopLinAccStreaming() {

        _measuring.update { false }
        linAccDisposable?.dispose()
        _elevations.update {null}

    }
    override fun startHrStreaming(deviceId: String) {
        val isDisposed = hrDisposable?.isDisposed ?: true
        if(isDisposed) {
            _measuring.update { true }
            hrDisposable = api.startHrStreaming(deviceId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { hrData: PolarHrData ->
                        for (sample in hrData.samples) {
                            _currentHR.update { sample.hr }
                            _hrList.update { hrList ->
                                hrList + sample.hr
                            }
                        }
                    },
                    { error: Throwable ->
                        Log.e(TAG, "Hr stream failed.\nReason $error")
                    },
                    { Log.d(TAG, "Hr stream complete")}
                )
        } else {
            Log.d(TAG, "Already streaming")
        }

    }

    override fun stopHrStreaming() {
        _measuring.update { false }
        hrDisposable?.dispose()
        _currentHR.update { null }
    }

    // Function to apply EWMA filter with fixed alpha (0.3)
    private fun filterEWMA(currentAngle: Double, previousOutput: Double): Double {
        val alpha = 0.3 // Fixed alpha value (can be adjusted if needed)
        return alpha * currentAngle + (1 - alpha) * previousOutput
    }

}

