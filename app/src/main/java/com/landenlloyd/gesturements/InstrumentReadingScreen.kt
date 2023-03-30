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

class AccelerometerViewModel : ViewModel() {
    private val _accelerometerState = MutableStateFlow(AccelerometerState())
    val accelerometerState = _accelerometerState.asStateFlow()

    fun updateReadings(x: Float, y: Float, z: Float) {
        _accelerometerState.value = AccelerometerState(x, y, z)
    }
}

data class AccelerometerState(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
)

class GyroscopeViewModel : ViewModel() {
    private val _gyroscopeState = MutableStateFlow(GyroscopeState())
    val gyroscopeState = _gyroscopeState.asStateFlow()

    fun updateReadings(x: Float, y: Float, z: Float) {
        _gyroscopeState.value = GyroscopeState(x, y, z)
    }
}

data class GyroscopeState(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
)

@Composable
fun AccelerometerReading(
    modifier: Modifier = Modifier,
    accelerometerViewModel: AccelerometerViewModel,
    gyroscopeViewModel: GyroscopeViewModel
) {
    val accelerometerState by accelerometerViewModel.accelerometerState.collectAsState()
    val gyroscopeState by gyroscopeViewModel.gyroscopeState.collectAsState()

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
    accelerometerViewModel: AccelerometerViewModel,
    gyroscopeViewModel: GyroscopeViewModel
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