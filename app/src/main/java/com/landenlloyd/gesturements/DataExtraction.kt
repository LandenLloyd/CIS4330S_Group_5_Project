package com.landenlloyd.gesturements

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.apache.commons.math4.legacy.analysis.UnivariateFunction
import org.apache.commons.math4.legacy.analysis.interpolation.SplineInterpolator
import org.apache.commons.math4.legacy.analysis.interpolation.UnivariateInterpolator
import kotlin.math.roundToInt

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
 */
class Sensor3DViewModel(
    frameWidth: Int = 30,
    private val overlap: Float = 0f,
    private val frameSyncConnector: FrameSync.FrameSyncConnector? = null
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

class FrameSync(val onSync: (SensorFrame, SensorFrame) -> Unit) {
    private var numValidFramesLock = Object()
    private var numValidFrames = 0

    class FrameSyncConnector(private val frameSync: FrameSync) {
        var frame: SensorFrame? = null
            set(value) {
                if (value != null) {
                    synchronized(frameSync.numValidFramesLock) {
                        field = value
                        frameSync.numValidFrames += 1
                    }
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
            leftFrame.syncToTime(rightFrame.content.t)
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
