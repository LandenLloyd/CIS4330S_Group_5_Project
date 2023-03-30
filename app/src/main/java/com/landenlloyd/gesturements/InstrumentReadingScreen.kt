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

class Sensor3DViewModel : ViewModel() {
    private val _sensorState = MutableStateFlow(SensorState())
    val sensorState = _sensorState.asStateFlow()

    fun updateReadings(x: Float, y: Float, z: Float) {
        _sensorState.value = SensorState(x, y, z)
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