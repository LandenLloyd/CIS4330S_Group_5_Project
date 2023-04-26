package com.landenlloyd.gesturements

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.apache.commons.math3.util.FastMath.*

/**
 * The FrameFeatureExtractor takes in a SensorFrame, and generates a bunch of descriptive statistics
 * for the frame. In order to access, say, the mean of the magnitude array, write
 * `frameFeatureExtractor.x.mean`.
 */
class FrameFeatureExtractor(
    frame: SensorFrame,
    private val magnitudeArray: DoubleArray = getMagnitudeArray(
        frame.content.x,
        frame.content.y,
        frame.content.z
    ),
    val magnitude: DoubleArrayFeatureWrapper = DoubleArrayFeatureWrapper(magnitudeArray),
    val x: DoubleArrayFeatureWrapper = DoubleArrayFeatureWrapper(frame.content.x),
    val y: DoubleArrayFeatureWrapper = DoubleArrayFeatureWrapper(frame.content.y),
    val z: DoubleArrayFeatureWrapper = DoubleArrayFeatureWrapper(frame.content.z),
    val t: DoubleArrayFeatureWrapper = DoubleArrayFeatureWrapper(frame.content.t)
) {
    /**
     * Returns a summary of the features extracted from the magnitude array.
     */
    fun summarize(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.appendLine("Magnitude statistics summary:")
        stringBuilder.appendLine("Mean:                ${magnitude.mean}")
        stringBuilder.appendLine("Median:              ${magnitude.median}")
        stringBuilder.appendLine("Standard Deviation:  ${magnitude.standardDeviation}")
        stringBuilder.appendLine("Skewness:            ${magnitude.skewness}")
        stringBuilder.appendLine("Kurtosis:            ${magnitude.kurtosis}")
        return stringBuilder.toString()
    }

}

/**
 * This wrapper calls the Apache API to generate descriptive statistics for `array`, and then
 * exposes properties for accessing those statistics.
 */
class DoubleArrayFeatureWrapper(array: DoubleArray) {
    private val stats: DescriptiveStatistics = DescriptiveStatistics()

    init {
        for (value in array) {
            stats.addValue(value)
        }
    }

    val mean: Double by lazy { stats.mean }
    val median: Double by lazy { stats.getPercentile(50.0) }
    val variance: Double by lazy { stats.variance }
    val standardDeviation: Double by lazy { stats.standardDeviation }
    val min: Double by lazy { stats.min }
    val max: Double by lazy { stats.max }
    val range: Double by lazy { max - min }
    val iqr: Double by lazy { stats.getPercentile(75.0) - stats.getPercentile(25.0) }
    val skewness: Double by lazy { stats.skewness }
    val kurtosis: Double by lazy { stats.skewness }
    val sum: Double by lazy { stats.sum }
}

/**
 * Returns the Euclidean norm of the multiple xs
 */
fun eucNorm(vararg xs: Double): Double {
    return sqrt(xs.sumOf { pow(it, 2) })
}

/**
 * Returns a double array constructed by taking the Euclidean norm of the corresponding elements
 * in x, y, and z.
 */
fun getMagnitudeArray(x: DoubleArray, y: DoubleArray, z: DoubleArray): DoubleArray {
    return DoubleArray(x.size) { eucNorm(x[it], y[it], z[it]) }
}
