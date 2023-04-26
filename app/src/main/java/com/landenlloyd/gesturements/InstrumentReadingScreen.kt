package com.landenlloyd.gesturements

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

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
) {
    val listener = SensorListener(applicationContext = LocalContext.current)

    Column {
        Text(
            modifier = modifier,
            text = stringResource(id = R.string.instrument_reading_title)
        )
        AccelerometerReading(
            accelerometerViewModel = listener.accelerometerViewModel,
            gyroscopeViewModel = listener.gyroscopeViewModel
        )
    }
}