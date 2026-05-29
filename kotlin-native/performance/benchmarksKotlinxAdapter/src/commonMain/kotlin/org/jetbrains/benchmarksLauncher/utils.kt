package org.jetbrains.benchmarksLauncher

import kotlinx.benchmark.Param

const val BENCHMARK_SIZE = 10000

abstract class SkipWhenBaseOnly {
    @Param("false")
    var baseOnly = false

    fun skipWhenBaseOnly() {
        check(!baseOnly) { "Skipping because baseOnly=true" }
    }
}
