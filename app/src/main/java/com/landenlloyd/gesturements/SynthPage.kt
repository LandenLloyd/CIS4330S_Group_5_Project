package com.landenlloyd.gesturements

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun SynthPage(modifier: Modifier = Modifier) {
    val listener = SensorListener(LocalContext.current)

    SynthStatistics(modifier = modifier, listener = listener)
}

@Composable
fun SynthStatistics(modifier: Modifier = Modifier, listener: SensorListener) {
    var gyroStats: String by remember {
        mutableStateOf("")
    }
    var accelStats: String by remember {
        mutableStateOf("")
    }

    listener.featureExtractorCallback = {f1: FrameFeatureExtractor, f2: FrameFeatureExtractor ->
        accelStats = f1.summarize()
        gyroStats = f2.summarize()
    }

    Column(modifier = modifier) {
        Text(text = accelStats)
        Text(text = gyroStats)
    }
}