package com.landenlloyd.gesturements

import com.jsyn.JSyn
import com.jsyn.Synthesizer
import com.jsyn.unitgen.LineOut
import com.jsyn.unitgen.SineOscillator
import com.landenlloyd.gesturements.android.JSynAndroidAudioDevice
import kotlin.math.roundToInt

interface GesturementsClassifier {
    fun classify(accelFeatures: FrameFeatureExtractor, gyroFeatures: FrameFeatureExtractor)

    fun startPlayback()

    fun stopPlayback()
}

/**
 * The GesturementsClassifier is responsible for reading features in, and deciding what audio
 * needs to be played.
 */
class GesturementsSimpleClassifier(private val synthesizer: GesturementsSynthesizer) :
    GesturementsClassifier {
    private val accelFreqCorr = 50

    private var volume = 0.05

    init {
        synthesizer.initialize()
    }

    /**
     * Based on the features found in `accelFeatures` and `gyroFeatures`, use `synthesizer`
     * to play audio.
     */
    override fun classify(
        accelFeatures: FrameFeatureExtractor,
        gyroFeatures: FrameFeatureExtractor
    ) {
        // The gyroscope controls the volume
        // change in volume = totalrads / 1 sec * ∆t ns * 1s / 10e9 ns * 1 half-rotation / π rads
        // Subtract from the volume since counter-clockwise is positive, but we expect clockwise to
        // increase the volume.
        volume -= gyroFeatures.z.sum * (gyroFeatures.t.max - gyroFeatures.t.min) / 1000000000 / Math.PI

        // cap the volume between 0 and 1
        if (volume < 0.0) {
            volume = 0.0
        } else if (volume > 1.0) {
            volume = 1.0
        }

        // The accelerometer controls the frequency with its magnitude
        // We round it to an int since the accelerometer can have some noise
        val freq = (accelFeatures.magnitude.mean * accelFreqCorr).roundToInt()

        // If the user is not moving the device, we turn the volume down to zero as a sort of "off"
        if (freq == 0) {
            synthesizer.adjustPlayback(0.0, 0.0)
        } else {
            synthesizer.adjustPlayback(freq.toDouble(), volume)
        }
    }

    override fun startPlayback() {
        synthesizer.startPlayback()
    }

    override fun stopPlayback() {
        synthesizer.stopPlayback()
    }
}

interface GesturementsSynthesizer {
    fun initialize()

    fun adjustPlayback(frequency: Double, amplitude: Double)

    fun startPlayback()

    fun stopPlayback()
}

class GesturementsSynthSynthesizer : GesturementsSynthesizer {
    private lateinit var synth: Synthesizer
    private lateinit var oscillator: SineOscillator
    private lateinit var lineOut: LineOut

    override fun initialize() {
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
        oscillator.frequency.set(0.0)
        oscillator.amplitude.set(0.0)
    }

    override fun adjustPlayback(frequency: Double, amplitude: Double) {
        oscillator.frequency.set(frequency)
        oscillator.amplitude.set(amplitude)
    }

    override fun startPlayback() {
        lineOut.start()
    }

    override fun stopPlayback() {
        lineOut.stop()
    }
}
