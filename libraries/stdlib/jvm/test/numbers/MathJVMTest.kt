/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package test.numbers

import kotlin.test.*
import kotlin.math.*

class MathJVMTest {

    @Test fun IEEEremainder() {
        val data: Array<DoubleArray> = [  //  a    a IEEErem 2.5
            [-2.0, 0.5],
            [-1.25, -1.25],
            [0.0, 0.0],
            [1.0, 1.0],
            [1.25, 1.25],
            [1.5, -1.0],
            [2.0, -0.5],
            [2.5, 0.0],
            [3.5, 1.0],
            [3.75, -1.25],
            [4.0, -1.0]
        ]
        for ([a, r] in data) {
            assertEquals(r, a.IEEErem(2.5), "($a).IEEErem(2.5)")
        }

        assertTrue(Double.NaN.IEEErem(2.5).isNaN())
        assertTrue(2.0.IEEErem(Double.NaN).isNaN())
        assertTrue(Double.POSITIVE_INFINITY.IEEErem(2.0).isNaN())
        assertTrue(2.0.IEEErem(0.0).isNaN())
        assertEquals(PI, PI.IEEErem(Double.NEGATIVE_INFINITY))
    }

}
