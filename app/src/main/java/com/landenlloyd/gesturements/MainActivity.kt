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
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.jsyn.JSyn
import com.jsyn.Synthesizer
import com.jsyn.unitgen.LineOut
import com.jsyn.unitgen.SineOscillator
import com.landenlloyd.gesturements.android.JSynAndroidAudioDevice
import com.landenlloyd.gesturements.ui.theme.GesturementsTheme


class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometerViewModel: Sensor3DViewModel
    private lateinit var gyroscopeViewModel: Sensor3DViewModel
    private lateinit var frameSync: FrameSync

    private lateinit var firebaseDatabaseReference: DatabaseReference

    var accelPreprocessEntryNum = 0

    lateinit var accelPostLowPassWriteFunction: (Long, Double, Double, Double) -> Unit
    lateinit var accelPostSmoothWriteFunction: (Long, Double, Double, Double) -> Unit

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

    private fun initializeFirebase() {
        FirebaseApp.initializeApp(applicationContext)
        firebaseDatabaseReference =
            Firebase.database("https://gesturements-default-rtdb.firebaseio.com").reference.child("data")
    }

    private fun getFrameSync(): FrameSync {
        return FrameSync { accelerometerFrame: SensorFrame, gyroscopeFrame: SensorFrame ->
            accelerometerViewModel.updateReadings(accelerometerFrame.getAverages())
            gyroscopeViewModel.updateReadings(gyroscopeFrame.getAverages())

            val accelerometerPreprocessor = SensorFramePreprocessor(accelerometerFrame)
            val gyroscopePreprocessor = SensorFramePreprocessor(gyroscopeFrame)

            // Get a baseline Fourier Transform to select parameters for filters
            accelerometerPreprocessor.fourierTransform(
                accelPreprocessEntryNum,
                firebaseDatabaseReference,
                "accel_fft"
            )
            accelPreprocessEntryNum++

            // Apply low pass filter
            accelerometerPreprocessor.lowPassFilter(10.0)
            accelerometerPreprocessor.forEach(accelPostLowPassWriteFunction)

            // Apply moving average smoothing
            accelerometerPreprocessor.smoothByMovingAverage()
            accelerometerPreprocessor.forEach(accelPostSmoothWriteFunction)
        }
    }

    /**
     * Create a function that can be used to easily upload sensor data to Firebase
     * The Firebase write function is intended to take in time, x, y, and z.
     * Ensure that initializeFirebase() is called before using the returned function.
     *
     * @param pathString the sub-path in the Firebase Realtime DB to write to
     */
    private fun getFirebaseWriteFunction(pathString: String): (Long, Double, Double, Double) -> Unit {
        // Create functions that can be used by our Sensor3DViewModels to write to Firebase
        var readNumber = 0
        return { t: Long, x: Double, y: Double, z: Double ->
            val target =
                firebaseDatabaseReference.child(pathString).child(readNumber.toString())
            readNumber++
            target.child("t").setValue(t)
            target.child("x").setValue(x)
            target.child("y").setValue(y)
            target.child("z").setValue(z)
        }
    }

    private fun setUpSensor() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        initializeFirebase()

        // Create functions that can be used by our Sensor3DViewModels to write to Firebase
        val accelRawWrite = getFirebaseWriteFunction("accel_raw")
        val gyroRawWrite = getFirebaseWriteFunction("gyro_raw")
        accelPostLowPassWriteFunction = getFirebaseWriteFunction("accel_post_low_pass")
        accelPostSmoothWriteFunction = getFirebaseWriteFunction("accel_post_smooth")

        // Initialize the sensing pipeline
        frameSync = getFrameSync()
        accelerometerViewModel =
            Sensor3DViewModel(frameSyncConnector = frameSync.left, onWrite = accelRawWrite)
        gyroscopeViewModel =
            Sensor3DViewModel(frameSyncConnector = frameSync.right, onWrite = gyroRawWrite)

        // Register this listener to the Linear Acceleration (Acceleration minus gravity)
        // and the gyroscope
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.also {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME,
                SensorManager.SENSOR_DELAY_GAME
            )
        }

        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.also {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            accelerometerViewModel.appendReadings(
                event.timestamp,
                event.values[0],
                event.values[1],
                event.values[2]
            )
        } else if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            gyroscopeViewModel.appendReadings(
                event.timestamp,
                event.values[0],
                event.values[1],
                event.values[2]
            )
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
        val time: Double = synth.currentTime
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
        InstrumentReadingScreen(
            accelerometerViewModel = Sensor3DViewModel(),
            gyroscopeViewModel = Sensor3DViewModel()
        )
    }
}