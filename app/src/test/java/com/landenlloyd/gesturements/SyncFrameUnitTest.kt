package com.landenlloyd.gesturements

import org.junit.Test

import org.junit.Assert.*
import kotlin.random.Random

class SyncFrameUnitTest {
    @Test
    fun test_syncFrame() {
        val numReadings = 10
        val delta = 0.00001

        // 1. Ensure that the cubic interpolation is applied correctly by making sure it is exact
        //    when used to interpolate at the nodes
        val t2 = DoubleArray(numReadings ) { 1000000 + it * 200.0 }
        val x1 = DoubleArray(numReadings) { Random.nextDouble() * 10 }
        val y1 = DoubleArray(numReadings) { Random.nextDouble() * 10 }
        val z1 = DoubleArray(numReadings) { Random.nextDouble() * 10 }

        val (x2, y2, z2) = syncFrames(t2, t2, x1, y1, z1)

        assertArrayEquals(x1, x2, delta)
        assertArrayEquals(y1, y2, delta)
        assertArrayEquals(z1, z2, delta)

        // 2. Ensure that the function behaves reasonably when asked to interpolate a linear graph
        val t4 = doubleArrayOf(1.5, 2.0, 2.5)
        val t3 = doubleArrayOf(1.0, 2.0, 3.0)
        val x3 = doubleArrayOf(1.0, 2.0, 3.0)
        val y3 = doubleArrayOf(3.0, 2.0, 1.0)
        val z3 = doubleArrayOf(2.0, 2.0, 2.0)

        val (x4, y4, z4) = syncFrames(t4, t3, x3, y3, z3)
        assertArrayEquals(x4, doubleArrayOf(1.5, 2.0, 2.5), delta)
        assertArrayEquals(y4, doubleArrayOf(2.5, 2.0, 1.5), delta)
        assertArrayEquals(z4, doubleArrayOf(2.0, 2.0, 2.0), delta)

        // 3. Ensure that the function behaves reasonably when asked to extrapolate a linear graph
        val t5 = doubleArrayOf(-0.5, 4.5, 10.0)

        val (x5, y5, z5) = syncFrames(t5, t3, x3, y3, z3)
        assertArrayEquals(x5, doubleArrayOf(1.0, 3.0, 3.0), delta)
        assertArrayEquals(y5, doubleArrayOf(3.0, 1.0, 1.0), delta)
        assertArrayEquals(z5, doubleArrayOf(2.0, 2.0, 2.0), delta)
    }
}