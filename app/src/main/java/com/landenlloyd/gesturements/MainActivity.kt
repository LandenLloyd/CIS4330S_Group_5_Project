package com.landenlloyd.gesturements

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
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
import androidx.compose.ui.platform.LocalContext
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
import com.landenlloyd.gesturements.ui.theme.GesturementsTheme
import kotlin.system.measureNanoTime


class MainActivity : ComponentActivity(), SensorEventListener {
    // CONFIGURATION OPTIONS
    private var displayTimingInformation = false
    private var displayStatistics = false

    private var sensorManager: SensorManager? = null
    private var sensorManagerEnabled = false
    private lateinit var accelerometerViewModel: Sensor3DViewModel
    private lateinit var gyroscopeViewModel: Sensor3DViewModel
    private lateinit var frameSync: FrameSync

    private lateinit var firebaseDatabaseReference: DatabaseReference

    private var accelPreprocessEntryNum = 0
    private var gyroPreprocessEntryNum = 0

    // NOTE: originally, I was using a low-pass filter for the accelerometer. Rather than
    // renaming all variables from "LowPass" to just "Filter", I left the names stand.
    private lateinit var accelPostLowPassWriteFunction: (Long, Double, Double, Double) -> Unit
    private lateinit var accelPostSmoothWriteFunction: (Long, Double, Double, Double) -> Unit

    private lateinit var gyroPostLowPassWriteFunction: (Long, Double, Double, Double) -> Unit
    private lateinit var gyroPostSmoothWriteFunction: (Long, Double, Double, Double) -> Unit

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
                        gyroscopeViewModel = gyroscopeViewModel,
                        detachListener = { this.unRegisterListener() }
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

            val accelerometerPreprocessor = SensorFramePreprocessor(
                accelerometerFrame,
                displayTimingInformation = displayTimingInformation
            )
            val gyroscopePreprocessor = SensorFramePreprocessor(
                gyroscopeFrame,
                displayTimingInformation = displayTimingInformation
            )

            // Get a baseline Fourier Transform to select parameters for filters
            accelerometerPreprocessor.fourierTransform(
                accelPreprocessEntryNum,
                firebaseDatabaseReference,
                "accel_fft"
            )
            accelPreprocessEntryNum++

            // Apply filters
            accelerometerPreprocessor.highPassFilter(5.0)
            accelerometerPreprocessor.forEach(accelPostLowPassWriteFunction)

            // Apply moving average smoothing
            accelerometerPreprocessor.smoothByMovingAverage()
            accelerometerPreprocessor.forEach(accelPostSmoothWriteFunction)

            // Get a baseline Fourier Transform to select parameters for filters
            gyroscopePreprocessor.fourierTransform(
                gyroPreprocessEntryNum,
                firebaseDatabaseReference,
                "gyro_fft"
            )
            gyroPreprocessEntryNum++

            // Apply low pass filter
            gyroscopePreprocessor.lowPassFilter(10.0)
            gyroscopePreprocessor.forEach(gyroPostLowPassWriteFunction)

            // Apply moving average smoothing
            gyroscopePreprocessor.smoothByMovingAverage()
            gyroscopePreprocessor.forEach(gyroPostSmoothWriteFunction)

            // Extract features
            val accelFeatures = FrameFeatureExtractor(accelerometerPreprocessor.frame)
            if (displayStatistics) Log.d("accelFeatures", accelFeatures.summarize())
            val gyroFeatures = FrameFeatureExtractor(gyroscopePreprocessor.frame)
            if (displayStatistics) Log.d("gyroFeatures", gyroFeatures.summarize())
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
        sensorManagerEnabled = true

        initializeFirebase()

        // Create functions that can be used by our Sensor3DViewModels to write to Firebase
        val accelRawWrite = getFirebaseWriteFunction("accel_raw")
        val gyroRawWrite = getFirebaseWriteFunction("gyro_raw")
        accelPostLowPassWriteFunction = getFirebaseWriteFunction("accel_post_low_pass")
        accelPostSmoothWriteFunction = getFirebaseWriteFunction("accel_post_smooth")
        gyroPostLowPassWriteFunction = getFirebaseWriteFunction("gyro_post_low_pass")
        gyroPostSmoothWriteFunction = getFirebaseWriteFunction("gyro_post_smooth")

        // Initialize the sensing pipeline
        frameSync = getFrameSync()
        accelerometerViewModel =
            Sensor3DViewModel(frameSyncConnector = frameSync.left, onWrite = accelRawWrite)
        gyroscopeViewModel =
            Sensor3DViewModel(frameSyncConnector = frameSync.right, onWrite = gyroRawWrite)

        // Register this listener to the Linear Acceleration (Acceleration minus gravity)
        // and the gyroscope
        sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.also {
            sensorManager?.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME,
                SensorManager.SENSOR_DELAY_GAME
            )
        }

        sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.also {
            sensorManager?.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (sensorManagerEnabled) {
            val eventProcessingDuration = measureNanoTime {
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

            if (displayTimingInformation) {
                Log.d("timing", eventProcessingDuration.toString())
            }
        }
    }

    override fun onAccuracyChanged(event: Sensor?, p1: Int) {
        return
    }

    private fun unRegisterListener() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        sensorManagerEnabled = false
    }

    override fun onDestroy() {
        unRegisterListener()
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
fun GesturementsButton(modifier: Modifier = Modifier, onButtonClicked: () -> Unit = {}, text: String = "placeholder") {
    Button(
        onClick = { onButtonClicked() },
        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.background),
        shape = RoundedCornerShape(8.dp),
        elevation = ButtonDefaults.elevation(8.dp),
        modifier = modifier.wrapContentSize(align = Alignment.Center)
    ) {
        Text(
            modifier = Modifier.padding(8.dp),
            text = text
        )
    }
}

@Composable
fun TitleColumn(
    modifier: Modifier = Modifier,
    onSynthesizerButtonClicked: () -> Unit = {},
    onInstrumentButtonClicked: () -> Unit = {},
    onSliderButtonClicked: () -> Unit = {},
    detachListener: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .wrapContentSize(align = Alignment.Center)
    ) {
        TitleText()
        GesturementsButton(onButtonClicked = onSynthesizerButtonClicked, text = stringResource(id = R.string.synth_button_text))
        GesturementsButton(onButtonClicked = onInstrumentButtonClicked, text = stringResource(id = R.string.instrument_button_text))
        GesturementsButton(onButtonClicked = onSliderButtonClicked, text = stringResource(id = R.string.slider_button_text))
        // It is helpful to have a button to detach listeners, allowing the network to catch up
        Button(
            onClick = detachListener,
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.background),
            shape = RoundedCornerShape(8.dp),
            elevation = ButtonDefaults.elevation(8.dp),
            modifier = modifier.wrapContentSize(align = Alignment.Center)
        ) {
            Text(
                modifier = Modifier.padding(8.dp),
                text = "Stop Sensing"
            )
        }
    }
}

@Composable
fun TitleScreen(
    modifier: Modifier = Modifier,
    onInstrumentButtonClicked: () -> Unit = {},
    onSliderButtonClicked: () -> Unit = {},
    detachListener: () -> Unit = {}
) {
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
        TitleColumn(
            onInstrumentButtonClicked = onInstrumentButtonClicked,
            onSliderButtonClicked = onSliderButtonClicked,
            detachListener = detachListener
        )
    }
}

@Composable
fun GesturementsApp(
    modifier: Modifier = Modifier,
    accelerometerViewModel: Sensor3DViewModel,
    gyroscopeViewModel: Sensor3DViewModel,
    detachListener: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current

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
                }, onSliderButtonClicked = {
                    navigateToSynthSlider(context)
                }, detachListener = detachListener)
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

fun navigateToSynthSlider(context: Context) {
    val intent = Intent(context, SliderTestActivity::class.java)
    context.startActivity(intent)
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