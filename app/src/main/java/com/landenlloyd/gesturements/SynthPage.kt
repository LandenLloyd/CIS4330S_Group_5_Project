package com.landenlloyd.gesturements

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.tehras.charts.line.LineChart
import com.github.tehras.charts.line.LineChartData
import com.github.tehras.charts.line.renderer.line.SolidLineDrawer
import com.github.tehras.charts.line.renderer.point.FilledCircularPointDrawer
import com.github.tehras.charts.line.renderer.xaxis.SimpleXAxisDrawer
import com.github.tehras.charts.line.renderer.yaxis.SimpleYAxisDrawer
import com.github.tehras.charts.piechart.animation.simpleChartAnimation
import com.landenlloyd.gesturements.ui.theme.GesturementsTheme

@Composable
fun SynthPage(modifier: Modifier = Modifier) {
    val listener = SensorListener(LocalContext.current)
    val classifier = GesturementsSimpleClassifier(synthesizer = GesturementsSynthSynthesizer())

    classifier.startPlayback()

    val image = painterResource(id = R.drawable.instrument)

    Box(modifier = modifier) {
        Image(
            painter = image,
            contentDescription = null,
            modifier = Modifier
                .fillMaxHeight()
                .wrapContentHeight(align = Alignment.Top),
            contentScale = ContentScale.FillWidth,
            alpha = 0.75f
        )
        Surface(
            modifier = Modifier
                .fillMaxSize(0.75f)
                .align(Alignment.Center),
            color = MaterialTheme.colors.background,
            shape = RoundedCornerShape(8.dp),
            elevation = 8.dp
        ) {
            SynthBody(
                modifier = Modifier.padding(16.dp),
                listener = listener,
                classifier = classifier
            )
        }
    }
}

const val statSummaryTestString =
    "Magnitude statistics summary:\nMean:                0.00\nMedian:              0.00\nStandard Deviation:  0.00\nSkewness:            0.00\nKurtosis:            0.00"
val freqTestList = listOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f)

@Composable
fun VolumeBar(
    modifier: Modifier = Modifier,
    progress: Float = 0.0f
) {
    Column(modifier = modifier.padding(PaddingValues(vertical = 12.dp))) {
        Text(text = "Current Volume")
        LinearProgressIndicator(progress = progress)
    }
}

@Composable
fun FrequencyChart(modifier: Modifier = Modifier, freqPoints: List<Float>) {
    Column(modifier = modifier) {
        Text(
            modifier = Modifier.padding(PaddingValues(vertical = 12.dp)),
            text = "Frequency Chart",
            style = MaterialTheme.typography.h5
        )
        Text(
            modifier = Modifier.padding(bottom = 12.dp),
            text = "X-axis is the index of each reading, with 0 being oldest, and Y-axis is the magnitude of acceleration (m/s^2)",
            style = MaterialTheme.typography.caption
        )
        LineChart(
            linesChartData = listOf(
                LineChartData(
                    points = freqPoints.withIndex()
                        .map { (index, value) -> LineChartData.Point(value, "$index") },
                    lineDrawer = SolidLineDrawer()
                )
            ),
            // Optional properties.
            modifier = modifier
                .fillMaxHeight(0.3f)
                .fillMaxWidth(0.8f),
            animation = simpleChartAnimation(),
            pointDrawer = FilledCircularPointDrawer(),
            xAxisDrawer = SimpleXAxisDrawer(),
            yAxisDrawer = SimpleYAxisDrawer(),
            horizontalOffset = 5f
        )
    }
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

    var freqPoints: List<Float> by remember {
        mutableStateOf(freqTestList)
    }

    listener?.featureExtractorCallback = { f1: FrameFeatureExtractor, f2: FrameFeatureExtractor ->
        accelStats = f1.summarize()
        gyroStats = f2.summarize()

        classifier?.classify(f1, f2)

        progress = classifier?.volume?.toFloat() ?: 0.0f
        freqPoints =
            classifier?.features?.accelValues?.toList()?.map { it.toFloat() } ?: freqTestList
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
            FrequencyChart(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                freqPoints = freqPoints
            )
            GesturementsButton(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onButtonClicked = { classifier?.volume = 0.5 },
                text = "Recalibrate Volume",
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
