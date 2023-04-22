package com.landenlloyd.gesturements

import com.github.psambit9791.jdsp.transform.DiscreteFourier
import com.github.psambit9791.jdsp.transform._Fourier
import com.google.firebase.database.DatabaseReference
import kotlin.math.roundToInt


class SensorFramePreprocessor(private var _frame: SensorFrame) {
    /**
     * Returns the sampling frequency of this fixed time interval is Hertz (sample / second)
     */
    private fun getFrameFrequency(): Double {
        val minT = _frame.content.t[0]
        val maxT = _frame.content.t[_frame.content.t.size - 1]
        val intervalLength = maxT - minT
        val freqNs =  _frame.content.frameWidth / intervalLength // Samples / nanosecond
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
        for (index in fT.indices) {
            val subRef = reference.child(index.toString())
            subRef.child("freq").setValue(freq[index])
            subRef.child("magnitude").setValue(fT[index])
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
     * For debugging purposes; simply generates the Fourier Transform of this frame
     * and uploads it to Firebase
     */
    fun fourierTransform(entryNum: Int, reference: DatabaseReference, pathString: String) {
        val (xFT, xFreqs) = wrapDiscreteFourier(_frame.content.x)
        val (yFT, yFreqs) = wrapDiscreteFourier(_frame.content.y)
        val (zFT, zFreqs) = wrapDiscreteFourier(_frame.content.z)

        freq3DToFirebase(entryNum, reference, pathString, xFT, xFreqs, yFT, yFreqs, zFT, zFreqs)
    }
}