/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package test.collection

import kotlin.test.*

// Native-specific part of stdlib/test/collections/MutableCollectionsTest.kt
class MutableCollectionsNativeTest {
    @Test fun sortListString() {
        val x: MutableList<String> = ["x", "a", "b"]
        x.sort()
        assertContentEquals(["a", "b", "x"], x)
    }

    @Test fun sortListInt() {
        val x: MutableList<Int> = [239, 42, -1, 100500, 0]
        x.sort()
        assertContentEquals([-1, 0, 42, 239, 100500], x)
    }
}