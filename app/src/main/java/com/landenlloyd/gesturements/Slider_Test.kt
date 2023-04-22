package com.landenlloyd.gesturements

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.SeekBar
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


private lateinit var synth: Synthesizer
private lateinit var oscillator: SineOscillator
private lateinit var lineOut: LineOut
private lateinit var frequencySlider: SeekBar
private lateinit var amplitudeSlider: SeekBar
private lateinit var startButton: Button
private lateinit var stopButton: Button
//    private lateinit var firebaseDatabaseReference: DatabaseReference


abstract class Slider_Test : ComponentActivity(), SensorEventListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.slider_layout)

        initialize()
        createUI()
    }


    private fun initialize() {
        // Create a context for the synthesizer.
        synth = JSyn.createSynthesizer(JSynAndroidAudioDevice())

        // Add a tone generator.
        oscillator = SineOscillator()
        synth.add(oscillator)

        // Add a stereo audio output unit.
        lineOut = LineOut()
        synth.add(lineOut)

        // Connect the oscillator to both channels of the output.
        oscillator.output.connect(0, lineOut.input, 0)
        oscillator.output.connect(0, lineOut.input, 1)

        // Initialize the frequency and amplitude values.
        oscillator.frequency.set(345.0)
        oscillator.amplitude.set(0.6)
    }

    private fun setFrequency(frequency: Double) {
        oscillator.frequency.set(frequency)
    }

    private fun setAmplitude(amplitude: Double) {
        oscillator.amplitude.set(amplitude)
    }

    private fun start() {
        // Start synthesizer using default stereo output at 44100 Hz.
        synth.start()

        // Start the LineOut.
        lineOut.start()

        // Set the frequency and amplitude for the sine wave.
        setFrequency(frequencySlider.progress.toDouble())
        setAmplitude(amplitudeSlider.progress.toDouble() / 100.0)
    }

    private fun stop() {
        // Stop everything.
        synth.stop()
    }

    private fun createUI() {
        // Create the frequency slider.
        frequencySlider = findViewById(R.id.frequency_slider)
        frequencySlider.max = 1000
        frequencySlider.progress = 345
        frequencySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                setFrequency(progress.toDouble())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Create the amplitude slider.
        amplitudeSlider = findViewById(R.id.amplitude_slider)
        amplitudeSlider.max = 100
        amplitudeSlider.progress = 60
        amplitudeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                setAmplitude(progress.toDouble() / 100.0)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Create the start button.
        startButton = findViewById(R.id.start_button)
        startButton.setOnClickListener { start() }

        // Create the stop button.
        stopButton = findViewById(R.id.stop_button)
        stopButton.setOnClickListener { stop() }
    }

}