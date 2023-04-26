package com.landenlloyd.gesturements

import android.util.Log
import com.github.psambit9791.jdsp.filter.Butterworth
import com.github.psambit9791.jdsp.signal.Smooth
import com.github.psambit9791.jdsp.transform.DiscreteFourier
import com.github.psambit9791.jdsp.transform._Fourier
import com.google.firebase.database.DatabaseReference
import kotlin.math.roundToInt


class SensorFramePreprocessor(
    var frame: SensorFrame,
    private val displayTimingInformation: Boolean = false,
    private val uploadToFirebase: Boolean = false,
) {
    /**
     * Returns the sampling frequency of this fixed time interval is Hertz (sample / second)
     */
    private fun getFrameFrequency(): Double {
        val minT = frame.content.t[0]
        val maxT = frame.content.t[frame.content.t.size - 1]
        val intervalLength = maxT - minT
        val freqNs = frame.content.frameWidth / intervalLength // Samples / nanosecond

        if (displayTimingInformation) {
            Log.d("timing", "time between each sample: ${1 / freqNs}")
        }

        return freqNs * 1000000000
    }

    /**
     * A thin wrapper over JDSP's Fourier Transform with our desired settings
     */
    private fun wrapDiscreteFourier(signal: DoubleArray): Pair<DoubleArray, DoubleArray> {
        val ft: _Fourier = DiscreteFourier(signal)
        ft.transform()
        return Pair(ft.getMagnitude(true), ft.getFFTFreq(getFrameFrequency().roundToInt(), true))
    }

    /**
     * A helper function that can be used to log complex output from JDSP
     */
    private fun toStringRecursive(a: Array<DoubleArray>): String {
        return "[" + a.joinToString { it.contentToString() } + "]"
    }

    /**
     * Debug function to help send a frequency domain graph to Firebase
     */
    private fun freqToFirebase(reference: DatabaseReference, fT: DoubleArray, freq: DoubleArray) {
        if (uploadToFirebase) {
            for (index in fT.indices) {
                val subRef = reference.child(index.toString())
                subRef.child("freq").setValue(freq[index])
                subRef.child("magnitude").setValue(fT[index])
            }
        }
    }

    /**
     * Debug function to help send a 3D frequency domain graph to Firebase
     */
    private fun freq3DToFirebase(
        entryNum: Int,
        reference: DatabaseReference,
        pathString: String,
        xFT: DoubleArray,
        xFreqs: DoubleArray,
        yFT: DoubleArray,
        yFreqs: DoubleArray,
        zFT: DoubleArray,
        zFreqs: DoubleArray
    ) {
        val subReference = reference.child(pathString).child(entryNum.toString())

        freqToFirebase(subReference.child("x"), xFT, xFreqs)
        freqToFirebase(subReference.child("y"), yFT, yFreqs)
        freqToFirebase(subReference.child("z"), zFT, zFreqs)
    }

    /**
     * Calls `function` over each txyz pair wrapped by this Preprocessor
     */
    fun forEach(function: (Long, Double, Double, Double) -> Unit) {
        for (index in frame.content.t.indices) {
            function(
                frame.content.t[index].toLong(),
                frame.content.x[index],
                frame.content.y[index],
                frame.content.z[index]
            )
        }
    }

    /**
     * For debugging purposes; simply generates the Fourier Transform of this frame
     * and uploads it to Firebase
     */
    fun fourierTransform(entryNum: Int, reference: DatabaseReference, pathString: String) {
        val (xFT, xFreqs) = wrapDiscreteFourier(frame.content.x)
        val (yFT, yFreqs) = wrapDiscreteFourier(frame.content.y)
        val (zFT, zFreqs) = wrapDiscreteFourier(frame.content.z)

        freq3DToFirebase(entryNum, reference, pathString, xFT, xFreqs, yFT, yFreqs, zFT, zFreqs)
    }

    /**
     * Applies a Butterworth low pass filter to the SensorFrame
     *
     * @param freqCap: all frequencies above this are discarded
     * @param order: the order of the Butterworth filter
     */
    fun lowPassFilter(freqCap: Double, order: Int = 4) {
        val filter = Butterworth(getFrameFrequency())
        val newX = filter.lowPassFilter(frame.content.x, order, freqCap)
        val newY = filter.lowPassFilter(frame.content.y, order, freqCap)
        val newZ = filter.lowPassFilter(frame.content.z, order, freqCap)
        frame.content =
            SensorFrameContent(frame.content.frameWidth, frame.content.t, newX, newY, newZ)
    }

    /**
     * Applies a Butterworth low pass filter to the SensorFrame
     *
     * @param freqMin: all frequencies below this are discarded
     * @param order: the order of the Butterworth filter
     */
    fun highPassFilter(freqMin: Double, order: Int = 4) {
        val filter = Butterworth(getFrameFrequency())
        val newX = filter.highPassFilter(frame.content.x, order, freqMin)
        val newY = filter.highPassFilter(frame.content.y, order, freqMin)
        val newZ = filter.highPassFilter(frame.content.z, order, freqMin)
        frame.content =
            SensorFrameContent(frame.content.frameWidth, frame.content.t, newX, newY, newZ)
    }

    /**
     * Applies a Butterworth band pass filter to the SensorFrame
     *
     * @param freqMin: all frequencies below this are discarded
     * @param freqCap: all frequencies above this are discarded
     * @param order: the order of the Butterworth filter
     */
    fun bandPassFilter(freqMin: Double, freqCap: Double, order: Int = 4) {
        val filter = Butterworth(getFrameFrequency())
        val newX = filter.bandPassFilter(frame.content.x, order, freqMin, freqCap)
        val newY = filter.bandPassFilter(frame.content.y, order, freqMin, freqCap)
        val newZ = filter.bandPassFilter(frame.content.z, order, freqMin, freqCap)
        frame.content =
            SensorFrameContent(frame.content.frameWidth, frame.content.t, newX, newY, newZ)
    }

    /**
     * Uses JDSP's smoothing function, which is a moving average, to smooth the SensorFrame.
     *
     * @param windowSize the size of the sliding window; in other words, the number of samples
     * that are factored into each moving average calculation.
     * @param mode "rectangular" slides a window over the data, calculating a simple moving average,
     * or "triangular" which performs the "rectangular" operation twice for more smoothing.
     */
    fun smoothByMovingAverage(windowSize: Int = 7, mode: String = "rectangular") {
        val xSmooth = Smooth(frame.content.x, windowSize, mode)
        val newX = xSmooth.smoothSignal()
        val ySmooth = Smooth(frame.content.y, windowSize, mode)
        val newY = ySmooth.smoothSignal()
        val zSmooth = Smooth(frame.content.z, windowSize, mode)
        val newZ = zSmooth.smoothSignal()

        // In taking a moving average, the num of samples is reduced, so we cut down edge timestamps
        val newSampleCount = newX.size
        val newT = frame.content.t.take((frame.content.frameWidth + newSampleCount) / 2)
            .takeLast(newSampleCount).toDoubleArray()

        frame.content =
            SensorFrameContent(newSampleCount, newT, newX, newY, newZ)
    }
}