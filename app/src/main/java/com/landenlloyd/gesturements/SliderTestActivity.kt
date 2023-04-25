package com.landenlloyd.gesturements

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import com.jsyn.JSyn
import com.jsyn.Synthesizer
import com.jsyn.unitgen.LineOut
import com.jsyn.unitgen.SineOscillator
import com.landenlloyd.gesturements.android.JSynAndroidAudioDevice


private lateinit var synth: Synthesizer
private lateinit var oscillator: SineOscillator
private lateinit var lineOut: LineOut


class SliderTestActivity : Activity() {
    private lateinit var synth: Synthesizer
    private lateinit var oscillator: SineOscillator
    private lateinit var lineOut: LineOut
    private lateinit var freqSeekBar: SeekBar
    private lateinit var ampSeekBar: SeekBar
    private lateinit var startStopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create layout
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Frequency SeekBar
            freqSeekBar = SeekBar(this@SliderTestActivity).apply {
                max = 1000
                progress = 345
                setOnSeekBarChangeListener(seekBarListener)
            }
            // Amplitude SeekBar
            ampSeekBar = SeekBar(this@SliderTestActivity).apply {
                max = 100
                progress = 60
                setOnSeekBarChangeListener(seekBarListener)
            }
            // Start/Stop button
            startStopButton = Button(this@SliderTestActivity).apply {
                text = "Start"
                setOnClickListener {
                    toggleAudio()
                }
            }
            // Add views to the layout
            addView(freqSeekBar)
            addView(ampSeekBar)
            addView(startStopButton)
        }

        // Set the layout as the content view
        setContentView(layout)

        // Set up the synthesizer
        synth = JSyn.createSynthesizer(JSynAndroidAudioDevice())
        synth.start()
        oscillator = SineOscillator()
        synth.add(oscillator)
        lineOut = LineOut()
        synth.add(lineOut)
        oscillator.output.connect(0, lineOut.input, 0)
        oscillator.output.connect(0, lineOut.input, 1)

        // Set the frequency and amplitude for the sine wave.
        oscillator.frequency.set(freqSeekBar.progress.toDouble())
        oscillator.amplitude.set(ampSeekBar.progress.toDouble() / 100)

        // Sleep while the sound is generated in the background.
        Thread.sleep(500)
    }

    override fun onDestroy() {
        super.onDestroy()
        synth.stop()
    }

    private val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
        override fun
                onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            when (seekBar) {
                freqSeekBar -> oscillator.frequency.set(progress.toDouble())
                ampSeekBar -> oscillator.amplitude.set(progress.toDouble() / 100)
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private fun toggleAudio() {
        if (startStopButton.text == "Start") {
            // Start audio playback
            lineOut.start()
            startStopButton.text = "Stop"
        } else {
            // Stop audio playback
            lineOut.stop()
            startStopButton.text = "Start"
        }
    }

}
