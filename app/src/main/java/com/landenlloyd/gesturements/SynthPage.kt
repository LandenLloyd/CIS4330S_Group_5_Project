package com.landenlloyd.gesturements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SynthPage(modifier: Modifier = Modifier) {
    val listener = SensorListener(LocalContext.current)
    val classifier = GesturementsSimpleClassifier(synthesizer = GesturementsSynthSynthesizer())

    classifier.startPlayback()

    SynthBody(modifier = modifier.padding(16.dp), listener = listener, classifier = classifier)
}

@Composable
fun SynthBody(
    modifier: Modifier = Modifier,
    listener: SensorListener,
    classifier: GesturementsClassifier
) {
    var gyroStats: String by remember {
        mutableStateOf("")
    }

    var accelStats: String by remember {
        mutableStateOf("")
    }

    listener.featureExtractorCallback = { f1: FrameFeatureExtractor, f2: FrameFeatureExtractor ->
        accelStats = f1.summarize()
        gyroStats = f2.summarize()

        classifier.classify(f1, f2)
    }

    Column(modifier = modifier) {
        Text(text = accelStats)
        Text(text = gyroStats)
    }
}