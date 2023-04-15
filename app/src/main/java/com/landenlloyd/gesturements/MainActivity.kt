package com.landenlloyd.gesturements

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jsyn.JSyn
import com.jsyn.Synthesizer
import com.jsyn.unitgen.LineOut
import com.jsyn.unitgen.SineOscillator
import com.landenlloyd.gesturements.android.JSynAndroidAudioDevice
import com.landenlloyd.gesturements.ui.theme.GesturementsTheme


class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometerViewModel = Sensor3DViewModel()
    private var gyroscopeViewModel = Sensor3DViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUpSensor()

        setContent {
            GesturementsTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    GesturementsApp(
                        accelerometerViewModel = accelerometerViewModel,
                        gyroscopeViewModel = gyroscopeViewModel
                    )
                }
            }
        }
    }

    private fun setUpSensor() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_FASTEST,
                SensorManager.SENSOR_DELAY_FASTEST
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            accelerometerViewModel.updateReadings(event.timestamp, event.values[0], event.values[1], event.values[2])
        } else if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            gyroscopeViewModel.updateReadings(event.timestamp, event.values[0], event.values[1], event.values[2])
        }
    }

    override fun onAccuracyChanged(event: Sensor?, p1: Int) {
        return
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }
}

enum class GesturementsScreen {
    Title, Instrument
}

@Composable
fun TitleText(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colors.background,
        shape = RoundedCornerShape(8.dp),
        elevation = 8.dp,
        modifier = modifier
            .wrapContentSize(align = Alignment.Center)
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = stringResource(id = R.string.app_name),
            style = Typography().h5
        )
    }
}

@Composable
fun InstrumentButton(modifier: Modifier = Modifier, onInstrumentButtonClicked: () -> Unit = {}) {
    Button(
        onClick = { onInstrumentButtonClicked() },
        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.background),
        shape = RoundedCornerShape(8.dp),
        elevation = ButtonDefaults.elevation(8.dp),
        modifier = modifier.wrapContentSize(align = Alignment.Center)
    ) {
        Text(
            modifier = Modifier.padding(8.dp),
            text = stringResource(id = R.string.instrument_button_text)
        )
    }
}

/* https://github.com/philburk/jsyn/blob/master/examples/src/main/java/com/jsyn/examples/PlayTone.java
 * Example function to test sound playback functionality. TODO: remove */
private fun testBing() {

    // Create a context for the synthesizer.
    val synth: Synthesizer = JSyn.createSynthesizer(JSynAndroidAudioDevice())

    // Start synthesizer using default stereo output at 44100 Hz.
    synth.start()

    // Add a tone generator.
    val oscillator = SineOscillator()
    synth.add(oscillator)
    // Add a stereo audio output unit.
    val lineOut = LineOut()
    synth.add(lineOut)

    // Connect the oscillator to both channels of the output.
    oscillator.output.connect(0, lineOut.input, 0)
    oscillator.output.connect(0, lineOut.input, 1)

    // Set the frequency and amplitude for the sine wave.
    oscillator.frequency.set(345.0)
    oscillator.amplitude.set(0.6)

    // We only need to start the LineOut. It will pull data from the
    // oscillator.
    lineOut.start()

    // Sleep while the sound is generated in the background.
    try {
        val time: Double = synth.getCurrentTime()
        // Sleep for a few seconds.
        synth.sleepUntil(time + 4.0)
    } catch (e: InterruptedException) {
        e.printStackTrace()
    }

    // Stop everything.
    synth.stop()
}

@Composable
fun TitleColumn(modifier: Modifier = Modifier, onInstrumentButtonClicked: () -> Unit = {}) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .wrapContentSize(align = Alignment.Center)
    ) {
        TitleText()
        InstrumentButton(onInstrumentButtonClicked = onInstrumentButtonClicked)
        Button(
            onClick = { testBing() },
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.background),
            shape = RoundedCornerShape(8.dp),
            elevation = ButtonDefaults.elevation(8.dp),
            modifier = modifier.wrapContentSize(align = Alignment.Center)
        ) {
            Text(
                modifier = Modifier.padding(8.dp),
                text = "Bing"
            )
        }
    }
}

@Composable
fun TitleScreen(modifier: Modifier = Modifier, onInstrumentButtonClicked: () -> Unit = {}) {
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
        TitleColumn(onInstrumentButtonClicked = onInstrumentButtonClicked)
    }
}

@Composable
fun GesturementsApp(
    modifier: Modifier = Modifier,
    accelerometerViewModel: Sensor3DViewModel,
    gyroscopeViewModel: Sensor3DViewModel
) {
    val navController = rememberNavController()

    Scaffold {
        NavHost(
            navController = navController,
            startDestination = GesturementsScreen.Title.name,
            modifier = modifier.padding(it)
        ) {
            composable(route = GesturementsScreen.Title.name) {
                TitleScreen(onInstrumentButtonClicked = {
                    navController.navigate(
                        GesturementsScreen.Instrument.name
                    )
                })
            }

            composable(route = GesturementsScreen.Instrument.name) {
                InstrumentReadingScreen(
                    accelerometerViewModel = accelerometerViewModel,
                    gyroscopeViewModel = gyroscopeViewModel
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    GesturementsTheme {
        InstrumentReadingScreen(accelerometerViewModel = Sensor3DViewModel(), gyroscopeViewModel = Sensor3DViewModel())
    }
}