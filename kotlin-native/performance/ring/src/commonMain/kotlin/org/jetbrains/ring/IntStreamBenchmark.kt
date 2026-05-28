/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.ring

import kotlinx.benchmark.Blackhole

open class IntStreamBenchmark {
    private var _data: Iterable<Int>? = null
    val data: Iterable<Int>
        get() = _data!!

    init {
        _data = intValues(BENCHMARK_SIZE)
    }
    
    //Benchmark
    fun copy(bh: Blackhole) {
        bh.consume(data.asSequence().toList())
    }
    
    //Benchmark
    fun copyManual(bh: Blackhole) {
        val list = ArrayList<Int>()
        for (item in data.asSequence()) {
            list.add(item)
        }
        bh.consume(list)
    }
    
    //Benchmark
    fun filterAndCount(bh: Blackhole) {
        bh.consume(data.asSequence().filter { filterLoad(it) }.count())
    }
    
    //Benchmark
    fun filterAndMap(bh: Blackhole) {
        for (item in data.asSequence().filter { filterLoad(it) }.map { mapLoad(it) })
            bh.consume(item)
    }
    
    //Benchmark
    fun filterAndMapManual(bh: Blackhole) {
        for (it in data.asSequence()) {
            if (filterLoad(it)) {
                val item = mapLoad(it)
                bh.consume(item)
            }
        }
    }
    
    //Benchmark
    fun filter(bh: Blackhole) {
        for (item in data.asSequence().filter { filterLoad(it) })
            bh.consume(item)
    }
    
    //Benchmark
    fun filterManual(bh: Blackhole){
        for (it in data.asSequence()) {
            if (filterLoad(it))
                bh.consume(it)
        }
    }
    
    //Benchmark
    fun countFilteredManual(bh: Blackhole) {
        var count = 0
        for (it in data.asSequence()) {
            if (filterLoad(it))
                count++
        }
        bh.consume(count)
    }
    
    //Benchmark
    fun countFiltered(bh: Blackhole) {
        bh.consume(data.asSequence().count { filterLoad(it) })
    }
    
    //Benchmark
    fun countFilteredLocal(bh: Blackhole) {
        bh.consume(data.asSequence().cnt { filterLoad(it) })
    }
    
    //Benchmark
    fun reduce(bh: Blackhole) {
        bh.consume(data.asSequence().fold(0) {acc, it -> if (filterLoad(it)) acc + 1 else acc })
    }
}
