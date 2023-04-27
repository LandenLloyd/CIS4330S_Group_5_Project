package com.landenlloyd.gesturements

import androidx.compose.foundation.layout.*
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.landenlloyd.gesturements.ui.theme.GesturementsTheme

@Composable
fun SynthPage(modifier: Modifier = Modifier) {
    val listener = SensorListener(LocalContext.current)
    val classifier = GesturementsSimpleClassifier(synthesizer = GesturementsSynthSynthesizer())

    classifier.startPlayback()

    SynthBody(modifier = modifier.padding(16.dp), listener = listener, classifier = classifier)
}

const val statSummaryTestString =
    "Magnitude statistics summary:\nMean:                0.00\nMedian:              0.00\nStandard Deviation:  0.00\nSkewness:            0.00\nKurtosis:            0.00"

@Composable
fun VolumeBar(
    modifier: Modifier = Modifier,
    progress: Float = 0.0f
) {
    Column (modifier = modifier){
        Text(text = "Current Volume")
        LinearProgressIndicator(progress = progress)
    }
}

@Composable
fun FrequencyChart(modifier: Modifier = Modifier) {
    Text(modifier = modifier, text = "Pretend that there's a chart here.")
}

@Composable
fun SynthBody(
    modifier: Modifier = Modifier,
    listener: SensorListener? = null,
    classifier: GesturementsClassifier? = null
) {
    var gyroStats: String by remember {
        mutableStateOf(statSummaryTestString)
    }

    var accelStats: String by remember {
        mutableStateOf(statSummaryTestString)
    }

    var progress: Float by remember {
        mutableStateOf(0.5f)
    }

    listener?.featureExtractorCallback = { f1: FrameFeatureExtractor, f2: FrameFeatureExtractor ->
        accelStats = f1.summarize()
        gyroStats = f2.summarize()

        classifier?.classify(f1, f2)

        progress = classifier?.volume?.toFloat() ?: 0.0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceAround
        ) {
            VolumeBar(modifier = Modifier.align(Alignment.CenterHorizontally), progress = progress)
            FrequencyChart(modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(modifier = Modifier.align(Alignment.CenterHorizontally), text = accelStats)
            Text(modifier = Modifier.align(Alignment.CenterHorizontally), text = gyroStats)
            GesturementsButton(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onButtonClicked = { classifier?.volume = 0.5 },
                text = "Recalibrate Volume"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SynthPagePreview() {
    GesturementsTheme {
        SynthBody()
    }
}
