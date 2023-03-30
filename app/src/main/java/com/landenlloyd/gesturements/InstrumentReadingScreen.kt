package com.landenlloyd.gesturements

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

class Sensor3DViewModel(frameWidth: Int = 20, private val overlap: Float = 0f) :
    ViewModel() {
    private val _frame = SensorFrame(frameWidth)

    private val _sensorState = MutableStateFlow(SensorState())
    val sensorState = _sensorState.asStateFlow()

    fun updateReadings(t: Long, x: Float, y: Float, z: Float) {
        if (_frame.updateReadings(t, x, y, z)) {
            val (_x, _y, _z) = _frame.getAverages()
            _sensorState.value = SensorState(_x, _y, _z)
            _frame.clear(overlap)
        }
    }
}

class SensorFrame(private val frameWidth: Int) {
    private var _content = SensorFrameContent(frameWidth)
    private var _frameSize = 0

    // Returns true if the frame is at capacity after adding the readings.
    // Does not add any new elements if the frame is already at capacity
    fun updateReadings(t: Long, x: Float, y: Float, z: Float): Boolean {
        if (_frameSize == frameWidth) {
            return true
        }

        _content.t[_frameSize] = t
        _content.x[_frameSize] = x
        _content.y[_frameSize] = y
        _content.z[_frameSize] = z
        _frameSize += 1

        return _frameSize == frameWidth
    }

    // Removes most elements, but keeps `overlap` proportion of elements
    fun clear(overlap: Float) {
        val numOverlap = (frameWidth * overlap).roundToInt()

        val overlapElementsT = _content.t.takeLast(numOverlap).toLongArray().copyOf(frameWidth)
        val overlapElementsX = _content.x.takeLast(numOverlap).toFloatArray().copyOf(frameWidth)
        val overlapElementsY = _content.y.takeLast(numOverlap).toFloatArray().copyOf(frameWidth)
        val overlapElementsZ = _content.z.takeLast(numOverlap).toFloatArray().copyOf(frameWidth)

        _content =
            SensorFrameContent(
                frameWidth,
                overlapElementsT,
                overlapElementsX,
                overlapElementsY,
                overlapElementsZ
            )
        _frameSize = numOverlap
    }

    fun getAverages(): Triple<Float, Float, Float> {
        val x = _content.x.average().toFloat()
        val y = _content.y.average().toFloat()
        val z = _content.z.average().toFloat()
        return Triple(x, y, z)
    }
}

data class SensorFrameContent(
    val frameWidth: Int,
    val t: LongArray = LongArray(frameWidth) { 0 },
    val x: FloatArray = FloatArray(frameWidth) { 0f },
    val y: FloatArray = FloatArray(frameWidth) { 0f },
    val z: FloatArray = FloatArray(frameWidth) { 0f },
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

data class SensorState(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
)

@Composable
fun AccelerometerReading(
    modifier: Modifier = Modifier,
    accelerometerViewModel: Sensor3DViewModel,
    gyroscopeViewModel: Sensor3DViewModel
) {
    val accelerometerState by accelerometerViewModel.sensorState.collectAsState()
    val gyroscopeState by gyroscopeViewModel.sensorState.collectAsState()

    Column(modifier = modifier) {
        Text(
            text = "X: ${accelerometerState.x}, Y: ${accelerometerState.y}, Z: ${accelerometerState.z}"
        )
        Text(
            text = "X: ${gyroscopeState.x}, Y: ${gyroscopeState.y}, Z: ${gyroscopeState.z}"
        )
    }
}

@Composable
fun InstrumentReadingScreen(
    modifier: Modifier = Modifier,
    accelerometerViewModel: Sensor3DViewModel,
    gyroscopeViewModel: Sensor3DViewModel
) {
    Column {
        Text(
            modifier = modifier,
            text = stringResource(id = R.string.instrument_reading_title)
        )
        AccelerometerReading(
            accelerometerViewModel = accelerometerViewModel,
            gyroscopeViewModel = gyroscopeViewModel
        )
    }
}