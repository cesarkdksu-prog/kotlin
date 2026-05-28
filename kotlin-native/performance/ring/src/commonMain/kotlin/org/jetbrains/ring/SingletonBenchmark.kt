/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package org.jetbrains.ring

import kotlin.random.Random
import kotlinx.benchmark.Blackhole

private object A {
    // Use the same seed for reproducibility
    private val rnd = Random(0)

    val a = rnd.nextInt(100)
}

open class SingletonBenchmark {
    init {
        // Make sure A is initialized.
        A.a
    }

    // Benchmark
    fun access(bh: Blackhole) {
        for (i in 0 until BENCHMARK_SIZE) {
            bh.consume(A.a)
        }
    }
}
