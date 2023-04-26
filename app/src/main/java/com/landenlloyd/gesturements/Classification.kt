package com.landenlloyd.gesturements

import android.util.Log
import com.jsyn.JSyn
import com.jsyn.Synthesizer
import com.jsyn.unitgen.LineOut
import com.jsyn.unitgen.SineOscillator
import com.landenlloyd.gesturements.android.JSynAndroidAudioDevice
import org.apache.commons.math3.util.FastMath.abs
import kotlin.math.roundToInt

interface GesturementsClassifier {
    var volume: Double

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
    val features = GesturementsFeatures()

    private val accelFreqCorr = 750
    private val gyroVolumeCorr = 0.02
    private val minGyroVolumeDelta = 0.005

    override var volume = 0.5

    init {
        synthesizer.initialize()
    }

    /**
     * Based on the features found in `accelFeatures` and `gyroFeatures`, use `synthesizer`
     * to play audio.
     */
    override fun classify(
        accelFeatures: FrameFeatureExtractor,
        gyroFeatures: FrameFeatureExtractor,
    ) {
        features.addFrames(accelFeatures, gyroFeatures)

        // The gyroscope controls the volume
        // change in volume = totalrads / 1 sec * ∆t ns * 1s / 10e9 ns * 1 half-rotation / π rads
        // Subtract from the volume since counter-clockwise is positive, but we expect clockwise to
        // increase the volume.
        val deltaVolume = features.gyroSum * features.gyroDeltaTime / 1000000000 / Math.PI * gyroVolumeCorr
        if (abs(deltaVolume) > minGyroVolumeDelta)
            volume -= deltaVolume

        // cap the volume between 0 and 1
        if (volume < 0.0) {
            volume = 0.0
        } else if (volume > 1.0) {
            volume = 1.0
        }

        // The accelerometer controls the frequency with its magnitude
        // We round it to an int since the accelerometer can have some noise
        val freqBeforeRounding = features.accelMean * accelFreqCorr
        var freq = freqBeforeRounding.roundToInt()

        // We move the frequency in steps of 10 to make it easier for the user to maintain frequency
        freq = (freq / 10) * 10

        Log.d("Classification", "Class id: ${this}, Volume: $volume, Freq: $freqBeforeRounding")

        // If the user is not moving the device, we turn the volume down to zero as a sort of "off"
        if (freq <= 90) {
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
    val numSteps = 10

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
        // We actually adjust the frequency and amplitude in steps so that the transition isn't
        // as "rough" to the listener.
        val deltaFreq = frequency - oscillator.frequency.get()
        val deltaAmp = amplitude - oscillator.amplitude.get()

        for (i in 0 until numSteps) {
            oscillator.frequency.set(oscillator.frequency.get() + deltaFreq / numSteps)
            oscillator.amplitude.set(oscillator.amplitude.get() + deltaAmp / numSteps)

            Thread.sleep(10)
        }
    }

    override fun startPlayback() {
        lineOut.start()
    }

    override fun stopPlayback() {
        lineOut.stop()
    }
}
