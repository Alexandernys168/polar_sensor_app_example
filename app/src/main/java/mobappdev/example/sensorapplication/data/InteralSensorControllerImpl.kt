package mobappdev.example.sensorapplication.data

/**
 * File: InternalSensorControllerImpl.kt
 * Purpose: Implementation of the Internal Sensor Controller.
 * Author: Jitse van Esch
 * Created: 2023-09-21
 * Last modified: 2023-09-21
 */

import android.content.ContentValues.TAG
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mobappdev.example.sensorapplication.domain.InternalSensorController
import kotlin.math.asin
import kotlin.math.pow
import kotlin.math.sqrt
import java.time.Instant
import java.io.File


private const val LOG_TAG = "Internal Sensor Controller"

class InternalSensorControllerImpl(
    private val context: Context
): InternalSensorController, SensorEventListener {

    // Expose acceleration to the UI
    private val _currentLinAccUI = MutableStateFlow<Triple<Float, Float, Float>?>(null)
    override val currentLinAccUI: StateFlow<Triple<Float, Float, Float>?>
        get() = _currentLinAccUI.asStateFlow()

    private var _currentLinAcc: Triple<Float,Float,Float>? = null // samma som private var _currentElevation: ElevationData? = null

    private var _currentGyro: Triple<Float, Float, Float>? = null



    // ELEVATION -->

    private var _currentElevation: ElevationData? = null

    private var _currentElevationUI= MutableStateFlow<ElevationData?>(null) // samma som UI
    override val currentElevation: StateFlow<ElevationData?>
        get() = _currentElevationUI.asStateFlow()

    private val _elevationList = MutableStateFlow<List<ElevationData>>(emptyList())

    override val elevationList: StateFlow<List<ElevationData>>
        get() = _elevationList.asStateFlow()

    private val _streamingElevation = MutableStateFlow(false)
    override val streamingElevation: StateFlow<Boolean>
        get() = _streamingElevation.asStateFlow()

    ////////





    // Expose gyro to the UI on a certain interval
    private val _currentGyroUI = MutableStateFlow<Triple<Float, Float, Float>?>(null)
    override val currentGyroUI: StateFlow<Triple<Float, Float, Float>?>
        get() = _currentGyroUI.asStateFlow()

    private val _streamingGyro = MutableStateFlow(false)
    override val streamingGyro: StateFlow<Boolean>
        get() = _streamingGyro.asStateFlow()

    private val _streamingLinAcc = MutableStateFlow(false)
    override val streamingLinAcc: StateFlow<Boolean>
        get() = _streamingLinAcc.asStateFlow()

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroSensor: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }
    private val linAccSensor: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private val elevationSensor: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(DelicateCoroutinesApi::class)
    override fun startImuStream() {

        var zTanBefore : Double = 0.0
        if(elevationSensor == null) {
            Log.e(LOG_TAG, "Accelerometer sensor is not available on this device")
            return
        }
        if(_streamingElevation.value) {
            Log.e(LOG_TAG, "Accelerometer sensor is already streaming")
            return
        }

        sensorManager.registerListener(this, elevationSensor, SensorManager.SENSOR_DELAY_UI)
        GlobalScope.launch(Dispatchers.Main) {
            _streamingElevation.value = true
            while (_streamingElevation.value) {
                //Log.e(TAG, "Value: $_currentLinAcc ")
                Log.e(TAG, "Value: $_currentLinAcc ")
                val angle = _currentLinAcc?.let { calculateElevationAngle(it.first, it.second, it.third) }
                val filteredAngleZ = filterEWMA(angle, zTanBefore)
                val formattedAngle = String.format("%.1f", filteredAngleZ)
                val currentTimeStamp = Instant.now().toEpochMilli() // Current timestamp in milliseconds

                Log.d(TAG, "Angle: $formattedAngle, Timestamp: $currentTimeStamp")
                zTanBefore = filteredAngleZ

                _currentElevationUI.update {
                    ElevationData(filteredAngleZ, currentTimeStamp.toString())

                }

                //_currentLinAccUI.update { _currentLinAcc }



                _elevationList.update {
                    it.plus(ElevationData(filteredAngleZ, currentTimeStamp.toString()))
                }


                delay(500)
            }
        }
    }
    override fun stopImuStream() {
        // Todo: implement
        if (_streamingElevation.value) {

            // Unregister the listener to stop receiving gyroscope events (automatically stops the coroutine as well
            sensorManager.unregisterListener(this, elevationSensor)
            writeElevationListToFile("internal_elevation_data.txt")
            _streamingElevation.value = false
        }
    }
    @OptIn(DelicateCoroutinesApi::class)
    override fun startGyroStream() {

        if (gyroSensor == null) {
            Log.e(LOG_TAG, "Gyroscope sensor is not available on this device")
            return
        }
        if (_streamingGyro.value) {
            Log.e(LOG_TAG, "Gyroscope sensor is already streaming")
            return
        }
        // Register this class as a listener for gyroscope events
        sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_UI)
        // Start a coroutine to update the UI variable on a 2 Hz interval
        GlobalScope.launch(Dispatchers.Main) {
            _streamingGyro.value = true
            while (_streamingGyro.value) {
                // Update the UI variable
                _currentGyroUI.update { _currentGyro }
                delay(500)
            }
        }

    }

    override fun stopGyroStream() {
        if (_streamingGyro.value) {
            // Unregister the listener to stop receiving gyroscope events (automatically stops the coroutine as well
            sensorManager.unregisterListener(this, gyroSensor)
            _streamingGyro.value = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            // Extract gyro data (angular speed around X, Y, and Z axes
            _currentGyro = Triple(event.values[0], event.values[1], event.values[2])
        }
        else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            _currentLinAcc = Triple(event.values[0], event.values[1], event.values[2])
        }
    }

    private fun filterEWMA(currentAngle: Float?, previousOutput: Double): Double {
        val alpha = 0.6 // Fixed alpha value (can be adjusted if needed)
        return alpha * currentAngle!! + (1 - alpha) * previousOutput
    }

    fun calculateElevationAngle(x: Float, y: Float, z: Float): Float {
        // Calculate the magnitude of the accelerometer data
        val magnitude = sqrt(x.pow(2) + y.pow(2) + z.pow(2))

        // Ensure the magnitude is not zero to avoid division by zero
        if (magnitude != 0f) {
            // Calculate the elevation angle in radians using the magnitude
            val elevationRadians = asin(z / magnitude)
            return Math.toDegrees(elevationRadians.toDouble()).toFloat() // Convert radians to degrees and then to float
        }

        // If magnitude is zero, return 0 degrees or any default value
        return 0f // Or any default value you prefer for zero magnitude
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Not used in this example
    }

    override fun writeElevationListToFile(fileName: String) {
        val elevationList = _elevationList.value

        val fileContent = elevationList.joinToString("\n") { elevationData ->
            "${elevationData.elevation.toInt()}; ${elevationData.timestamp}"
        }

        try {
            val file = File(context.filesDir, fileName)
            file.writeText(fileContent)
            Log.d(LOG_TAG, "Elevation list written to file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error writing elevation list to file: ${e.message}")
        }
    }
}