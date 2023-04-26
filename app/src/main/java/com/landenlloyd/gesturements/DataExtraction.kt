package com.landenlloyd.gesturements

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.apache.commons.math4.legacy.analysis.UnivariateFunction
import org.apache.commons.math4.legacy.analysis.interpolation.SplineInterpolator
import org.apache.commons.math4.legacy.analysis.interpolation.UnivariateInterpolator
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.system.measureNanoTime

/**
 * The SensorListener is responsible for registering a callback to the accelerometer and gyroscope,
 * and then processing the received data through a pipeline.
 */
class SensorListener(
    private val applicationContext: Context,
    var featureExtractorCallback: (FrameFeatureExtractor, FrameFeatureExtractor) -> Unit = { _: FrameFeatureExtractor, _: FrameFeatureExtractor -> }
) : SensorEventListener {
    // CONFIGURATION OPTIONS
    private var displayTimingInformation = false
    private var displayStatistics = false
    private var uploadDataToFirebase = false

    private var sensorManager: SensorManager? = null
    private var sensorManagerEnabled = false

    lateinit var accelerometerViewModel: Sensor3DViewModel
    lateinit var gyroscopeViewModel: Sensor3DViewModel

    // NOTE: originally, I was using a low-pass filter for the accelerometer. Rather than
    // renaming all variables from "LowPass" to just "Filter", I left the names stand.
    private lateinit var accelPostLowPassWriteFunction: (Long, Double, Double, Double) -> Unit
    private lateinit var accelPostSmoothWriteFunction: (Long, Double, Double, Double) -> Unit

    private lateinit var gyroPostLowPassWriteFunction: (Long, Double, Double, Double) -> Unit
    private lateinit var gyroPostSmoothWriteFunction: (Long, Double, Double, Double) -> Unit

    private lateinit var frameSync: FrameSync

    private lateinit var firebaseDatabaseReference: DatabaseReference

    private var accelPreprocessEntryNum = 0
    private var gyroPreprocessEntryNum = 0

    init {
        setUpSensor()
    }


    private fun initializeFirebase() {
        FirebaseApp.initializeApp(applicationContext)
        firebaseDatabaseReference =
            Firebase.database("https://gesturements-default-rtdb.firebaseio.com").reference.child("data")
    }

    private fun getFrameSync(): FrameSync {
        return FrameSync { accelerometerFrame: SensorFrame, gyroscopeFrame: SensorFrame ->
            accelerometerViewModel.updateReadings(accelerometerFrame.getAverages())
            gyroscopeViewModel.updateReadings(gyroscopeFrame.getAverages())

            val accelerometerPreprocessor = SensorFramePreprocessor(
                accelerometerFrame,
                displayTimingInformation = displayTimingInformation,
                uploadToFirebase = uploadDataToFirebase
            )
            val gyroscopePreprocessor = SensorFramePreprocessor(
                gyroscopeFrame,
                displayTimingInformation = displayTimingInformation,
                uploadToFirebase = uploadDataToFirebase
            )

            // Get a baseline Fourier Transform to select parameters for filters
            accelerometerPreprocessor.fourierTransform(
                accelPreprocessEntryNum,
                firebaseDatabaseReference,
                "accel_fft"
            )
            accelPreprocessEntryNum++

            // Apply filters
            accelerometerPreprocessor.highPassFilter(5.0)
            accelerometerPreprocessor.forEach(accelPostLowPassWriteFunction)

            // Apply moving average smoothing
            accelerometerPreprocessor.smoothByMovingAverage()
            accelerometerPreprocessor.forEach(accelPostSmoothWriteFunction)

            // Get a baseline Fourier Transform to select parameters for filters
            gyroscopePreprocessor.fourierTransform(
                gyroPreprocessEntryNum,
                firebaseDatabaseReference,
                "gyro_fft"
            )
            gyroPreprocessEntryNum++

            // Apply low pass filter
            gyroscopePreprocessor.lowPassFilter(10.0)
            gyroscopePreprocessor.forEach(gyroPostLowPassWriteFunction)

            // Apply moving average smoothing
            gyroscopePreprocessor.smoothByMovingAverage()
            gyroscopePreprocessor.forEach(gyroPostSmoothWriteFunction)

            // Extract features
            val accelFeatures = FrameFeatureExtractor(accelerometerPreprocessor.frame)
            if (displayStatistics) Log.d("accelFeatures", accelFeatures.summarize())
            val gyroFeatures = FrameFeatureExtractor(gyroscopePreprocessor.frame)
            if (displayStatistics) Log.d("gyroFeatures", gyroFeatures.summarize())

            featureExtractorCallback(accelFeatures, gyroFeatures)
        }
    }

    /**
     * Create a function that can be used to easily upload sensor data to Firebase
     * The Firebase write function is intended to take in time, x, y, and z.
     * Ensure that initializeFirebase() is called before using the returned function.
     *
     * @param pathString the sub-path in the Firebase Realtime DB to write to
     */
    private fun getFirebaseWriteFunction(pathString: String): (Long, Double, Double, Double) -> Unit {
        if (uploadDataToFirebase) {
            // Create functions that can be used by our Sensor3DViewModels to write to Firebase
            var readNumber = 0
            return { t: Long, x: Double, y: Double, z: Double ->
                val target =
                    firebaseDatabaseReference.child(pathString).child(readNumber.toString())
                readNumber++
                target.child("t").setValue(t)
                target.child("x").setValue(x)
                target.child("y").setValue(y)
                target.child("z").setValue(z)
            }
        } else {
            return { _: Long, _: Double, _: Double, _: Double -> }
        }
    }

    private fun setUpSensor() {
        sensorManager =
            applicationContext.getSystemService(ComponentActivity.SENSOR_SERVICE) as SensorManager
        sensorManagerEnabled = true

        initializeFirebase()

        // Create functions that can be used by our Sensor3DViewModels to write to Firebase
        val accelRawWrite = getFirebaseWriteFunction("accel_raw")
        val gyroRawWrite = getFirebaseWriteFunction("gyro_raw")
        accelPostLowPassWriteFunction = getFirebaseWriteFunction("accel_post_low_pass")
        accelPostSmoothWriteFunction = getFirebaseWriteFunction("accel_post_smooth")
        gyroPostLowPassWriteFunction = getFirebaseWriteFunction("gyro_post_low_pass")
        gyroPostSmoothWriteFunction = getFirebaseWriteFunction("gyro_post_smooth")

        // Initialize the sensing pipeline
        frameSync = getFrameSync()
        accelerometerViewModel =
            Sensor3DViewModel(frameSyncConnector = frameSync.left, onWrite = accelRawWrite)
        gyroscopeViewModel =
            Sensor3DViewModel(frameSyncConnector = frameSync.right, onWrite = gyroRawWrite)

        // Register this listener to the Linear Acceleration (Acceleration minus gravity)
        // and the gyroscope
        sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.also {
            sensorManager?.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME,
                SensorManager.SENSOR_DELAY_GAME
            )
        }

        sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.also {
            sensorManager?.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (sensorManagerEnabled) {
            val eventProcessingDuration = measureNanoTime {
                if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                    accelerometerViewModel.appendReadings(
                        event.timestamp,
                        event.values[0],
                        event.values[1],
                        event.values[2]
                    )
                } else if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
                    gyroscopeViewModel.appendReadings(
                        event.timestamp,
                        event.values[0],
                        event.values[1],
                        event.values[2]
                    )
                }
            }

            if (displayTimingInformation) {
                Log.d("timing", eventProcessingDuration.toString())
            }
        }
    }

    override fun onAccuracyChanged(event: Sensor?, p1: Int) {
        return
    }

    private fun unRegisterListener() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        sensorManagerEnabled = false
    }
}

class OverlapValueException(overlap: Float) :
    Exception("Sensor3DViewModel: property `overlap` has invalid value ${overlap}; `overlap` must fall between 0f and 1f")

/**
 * The Sensor3DViewModel serves as the UI state for xyz sensor readings.
 *
 * @property sensorState the read-only sensor state containing the readings from the most recent
 * frame. This should be used when accessing Sensor3DViewModel data from the UI.
 * @param frameWidth the number of sensor readings stored in each data frame.
 * @param overlap the portion of frame entries (between 0f and 1f) that are carried over from
 * the previous frame.
 * @param frameSyncConnector If you want this 3DViewModel to be synced with another, pass in
 * one of the connectors from an instance of FrameSync (refer to FrameSync for more details)
 */
class Sensor3DViewModel(
    frameWidth: Int = 20,
    private val overlap: Float = 0f,
    private val frameSyncConnector: FrameSync.FrameSyncConnector? = null,
    private val onWrite: ((Long, Double, Double, Double) -> Unit)? = null
) :
    ViewModel() {
    init {
        if (overlap < 0f || overlap > 1f) {
            throw OverlapValueException(overlap)
        }
    }

    private val _frame = SensorFrame(frameWidth)

    private val _sensorState = MutableStateFlow(SensorState())
    val sensorState = _sensorState.asStateFlow()

    /**
     * When a sensor returns new data readings, call this function to add those new readings to
     * the Sensor3DViewModel. If the frame reaches frameWidth, sensorState is updated and a new
     * empty frame is created in the background.
     *
     * @param t the time at which sensor readings were taken
     * @param x the x-coordinate for a sensor reading
     * @param y the y-coordinate for a sensor reading
     * @param z the z-coordinate for a sensor reading
     */
    fun appendReadings(t: Long, x: Float, y: Float, z: Float) {
        // If we are waiting on the go-ahead from FrameSync, ignore this reading
        val acceptReadings = frameSyncConnector?.acceptReadings?.get()
        if (acceptReadings != null && !acceptReadings) return

        onWrite?.invoke(t, x.toDouble(), y.toDouble(), z.toDouble())

        if (_frame.updateReadings(t.toDouble(), x.toDouble(), y.toDouble(), z.toDouble())) {
            if (frameSyncConnector == null) { // legacy: update UI with the raw values
                val (_x, _y, _z) = _frame.getAverages()
                updateReadings(_x, _y, _z)
            } else { // current: inform the frameSyncConnector that a new frame is available
                // We duplicate the sensorFrame so that we can later clear the frame
                frameSyncConnector.frame = SensorFrame.from(_frame)
            }

            // Make sure the frame gets cleared in both cases
            _frame.clear(overlap)
        }
    }

    /**
     * Updates the `sensorState` to reflect a SensorState with values `x`, `y`, and `z`
     */
    fun updateReadings(x: Float, y: Float, z: Float) {
        _sensorState.value = SensorState(x, y, z)
    }

    /**
     * Updates the `sensorState` to xyz corresponding to the triplet `values`
     */
    fun updateReadings(readings: Triple<Float, Float, Float>) {
        updateReadings(readings.first, readings.second, readings.third)
    }
}

/**
 * SensorState is used to store the xyz readings from a sensor.
 */
data class SensorState(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
)

/**
 * SensorFrame is a wrapper over SensorFrameContent to manage
 * appending, clearing, and calculating averages
 *
 * @param frameWidth the sensor reading capacity of this frame
 */
class SensorFrame(private val frameWidth: Int) {
    companion object {
        /**
         * Initialize a SensorFrame instance from an existing instance
         */
        fun from(sensorFrame: SensorFrame): SensorFrame {
            val newSensorFrame = SensorFrame(sensorFrame.frameWidth)
            newSensorFrame.content = sensorFrame.content
            newSensorFrame._frameSize = sensorFrame._frameSize
            return newSensorFrame
        }
    }

    var content = SensorFrameContent(frameWidth)
    private var _frameSize = 0

    /**
     * Returns true if the frame is at capacity after adding the readings.
     * Does not add any new elements if the frame is already at capacity
     *
     * @param t the time at which sensor readings were taken
     * @param x the x-coordinate for a sensor reading
     * @param y the y-coordinate for a sensor reading
     * @param z the z-coordinate for a sensor reading
     */
    fun updateReadings(t: Double, x: Double, y: Double, z: Double): Boolean {
        if (_frameSize == frameWidth) {
            return true
        }

        content.t[_frameSize] = t
        content.x[_frameSize] = x
        content.y[_frameSize] = y
        content.z[_frameSize] = z
        _frameSize += 1

        return _frameSize == frameWidth
    }

    /** Clears this current frame
     *
     * @param overlap this proportion of entries from the current frame is carried over after clear
     */
    fun clear(overlap: Float) {
        if (overlap < 0f || overlap > 1f) {
            throw OverlapValueException(overlap)
        }

        val numOverlap = (frameWidth * overlap).roundToInt()

        val overlapElementsT = content.t.takeLast(numOverlap).toDoubleArray().copyOf(frameWidth)
        val overlapElementsX = content.x.takeLast(numOverlap).toDoubleArray().copyOf(frameWidth)
        val overlapElementsY = content.y.takeLast(numOverlap).toDoubleArray().copyOf(frameWidth)
        val overlapElementsZ = content.z.takeLast(numOverlap).toDoubleArray().copyOf(frameWidth)

        content =
            SensorFrameContent(
                frameWidth,
                overlapElementsT,
                overlapElementsX,
                overlapElementsY,
                overlapElementsZ
            )
        _frameSize = numOverlap
    }

    /**
     * Returns the average x, y, and z readings, respectively
     */
    fun getAverages(): Triple<Float, Float, Float> {
        val x = content.x.average().toFloat()
        val y = content.y.average().toFloat()
        val z = content.z.average().toFloat()
        return Triple(x, y, z)
    }

    /**
     * Calls `syncFrame` to sync the content of this `SensorFrame` with the time values in `t`
     */
    fun syncToTime(t: DoubleArray) {
        val triple = syncFrames(t, content.t, content.x, content.y, content.z)
        content = SensorFrameContent(frameWidth, t, triple.first, triple.second, triple.third)
    }
}

/**
 * SensorFrameContent has capacity for `frameWidth` txyz sensor readings
 */
data class SensorFrameContent(
    val frameWidth: Int,
    val t: DoubleArray = DoubleArray(frameWidth) { 0.0 },
    val x: DoubleArray = DoubleArray(frameWidth) { 0.0 },
    val y: DoubleArray = DoubleArray(frameWidth) { 0.0 },
    val z: DoubleArray = DoubleArray(frameWidth) { 0.0 },
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SensorFrameContent

        if (frameWidth != other.frameWidth) return false
        if (!t.contentEquals(other.t)) return false
        if (!x.contentEquals(other.x)) return false
        if (!y.contentEquals(other.y)) return false
        if (!z.contentEquals(other.z)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = frameWidth
        result = 31 * result + t.contentHashCode()
        result = 31 * result + x.contentHashCode()
        result = 31 * result + y.contentHashCode()
        result = 31 * result + z.contentHashCode()
        return result
    }
}

/**
 * This class synchronizes the timestamps for two Sensor3DViewModels. Example of use:
 *
 * // Create a FrameSync with a callback that uses the data contained in the two frames
 * frameSync = FrameSync { accelerometerFrame: SensorFrame, gyroscopeFrame: SensorFrame ->
 *      Log.d("setUpSensor", "Frames were successfully synced")
 *
 *      accelerometerViewModel.updateReadings(accelerometerFrame.getAverages())
 *      gyroscopeViewModel.updateReadings(gyroscopeFrame.getAverages())
 * }
 *
 * // Create two instances of Sensor3DViewModel that are connected to frameSync
 * accelerometerViewModel = Sensor3DViewModel(frameSyncConnector = frameSync.left)
 * gyroscopeViewModel = Sensor3DViewModel(frameSyncConnector = frameSync.right)
 */
class FrameSync(val onSync: (SensorFrame, SensorFrame) -> Unit) {
    // numValidFrames tracks the number of FrameSyncConnectors that contain
    // a new frame to be synced. This variable is synchronized as the sensor
    // event callback may be multithreaded, and we want to ensure no frame is lost.
    private var numValidFramesLock = Object()
    private var numValidFrames = 0

    class FrameSyncConnector(private val frameSync: FrameSync) {
        var acceptReadings = AtomicBoolean(true)

        var frame: SensorFrame? = null
            set(value) {
                if (value != null) {
                    synchronized(frameSync.numValidFramesLock) {
                        field = value
                        frameSync.numValidFrames += 1
                    }

                    acceptReadings.set(false)
                    frameSync.trySync()
                } else {
                    field = null
                }
            }
    }

    val left: FrameSyncConnector = FrameSyncConnector(this)
    val right: FrameSyncConnector = FrameSyncConnector(this)

    fun trySync() {
        val leftFrame: SensorFrame?
        val rightFrame: SensorFrame?

        synchronized(numValidFramesLock) {
            leftFrame = left.frame
            rightFrame = right.frame

            if (numValidFrames >= 2) {
                left.frame = null
                right.frame = null
                numValidFrames = 0
            }
        }

        if (leftFrame != null && rightFrame != null) {
            // NOTE: we assume that both frames are "full"; their component array sizes
            // equals the frameSize

            // Allow frames to continue accepting readings
            left.acceptReadings.set(true)
            right.acceptReadings.set(true)

            // If the max of the leftFrame is less than the min of the rightFrame, there's no overlap
            if (leftFrame.content.t[leftFrame.content.t.size - 1] < rightFrame.content.t[0]) {
                Log.d("trySync", "leftFrame and rightFrame have no overlap: filling with zeroes")
                leftFrame.content = SensorFrameContent(leftFrame.content.frameWidth)
                rightFrame.content = SensorFrameContent(rightFrame.content.frameWidth)

                onSync(leftFrame, rightFrame)
                return
            }

            // Select the bounds for this frame
            val minT = max(leftFrame.content.t[0], rightFrame.content.t[0])
            val maxT = min(
                leftFrame.content.t[leftFrame.content.t.size - 1],
                rightFrame.content.t[rightFrame.content.t.size - 1]
            )

            // Create an evenly spaced interval
            val stepSize = (maxT - minT) / (leftFrame.content.frameWidth - 1)
            val newT =
                DoubleArray(leftFrame.content.frameWidth) { index -> minT + index * stepSize }

            // Sync both frames to the new time stamps
            leftFrame.syncToTime(newT)
            rightFrame.syncToTime(newT)

            onSync(leftFrame, rightFrame)
        }
    }
}

/**
 * Syncs the content in x2, y2, and z2 to the time t1.
 * Returns the new x2, y2, and z2.
 *
 * TODO: Currently, syncFrames does not extrapolate properly; if
 * t1 contains time points outside the range of t2, then it
 * simply uses the first or last value from x/y/z2.
 */
fun syncFrames(
    t1: DoubleArray,
    t2: DoubleArray,
    x2: DoubleArray,
    y2: DoubleArray,
    z2: DoubleArray
): Triple<DoubleArray, DoubleArray, DoubleArray> {
    val interpolator: UnivariateInterpolator = SplineInterpolator()
    val functionX: UnivariateFunction = interpolator.interpolate(t2, x2)
    val functionY: UnivariateFunction = interpolator.interpolate(t2, y2)
    val functionZ: UnivariateFunction = interpolator.interpolate(t2, z2)

    val min_t2 = t2[0]
    val max_t2 = t2[t2.size - 1]

    val newX1 = DoubleArray(t1.size) {
        val t = t1[it]
        if (t < min_t2) {
            x2[0]
        } else if (t > max_t2) {
            x2[x2.size - 1]
        } else {
            functionX.value(t)
        }
    }
    val newY1 = DoubleArray(t1.size) {
        val t = t1[it]
        if (t < min_t2) {
            y2[0]
        } else if (t > max_t2) {
            y2[y2.size - 1]
        } else {
            functionY.value(t)
        }
    }
    val newZ1 = DoubleArray(t1.size) {
        val t = t1[it]
        if (t < min_t2) {
            z2[0]
        } else if (t > max_t2) {
            z2[z2.size - 1]
        } else {
            functionZ.value(t)
        }
    }

    return Triple(newX1, newY1, newZ1)
}
