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

package samples.collections

import samples.*
import kotlin.test.*


@RunWith(Enclosed::class)
class Arrays {

    class Usage {

        @Sample
        fun arrayOrEmpty() {
            val nullArray: Array<Any>? = null
            assertPrints(nullArray.orEmpty().contentToString(), "[]")

            val array: Array<Char>? = ['a', 'b', 'c']
            assertPrints(array.orEmpty().contentToString(), "[a, b, c]")
        }

        @Sample
        fun arrayIsNullOrEmpty() {
            val nullArray: Array<Any>? = null
            assertTrue(nullArray.isNullOrEmpty())

            val emptyArray: Array<Any>? = []
            assertTrue(emptyArray.isNullOrEmpty())

            val array: Array<Char>? = ['a', 'b', 'c']
            assertFalse(array.isNullOrEmpty())
        }

        @Sample
        fun arrayIfEmpty() {
            val emptyArray: Array<Any> = []

            val emptyOrNull: Array<Any>? = emptyArray.ifEmpty { null }
            assertPrints(emptyOrNull, "null")

            val emptyOrDefault: Array<Any> = emptyArray.ifEmpty { ["default"] }
            assertPrints(emptyOrDefault.contentToString(), "[default]")

            val nonEmptyArray: Array<Int> = [1]
            val sameArray = nonEmptyArray.ifEmpty { [2] }
            assertTrue(nonEmptyArray === sameArray)
        }

        @Sample
        fun getOrElse() {
            val emptyArray: Array<Any> = []
            assertPrints(emptyArray.getOrElse(0) { "default" }, "default")

            val array: Array<Int> = [1]
            assertPrints(array.getOrElse(0) { 0 }, "1")
            assertPrints(array.getOrElse(-1) { 0 }, "0")
            assertPrints(array.getOrElse(0) { "default" }, "1")
            assertPrints(array.getOrElse(-1) { "default" }, "default")

            // arrays of primitive types
            val intArray: IntArray = [1, 2, 3]
            assertPrints(intArray.getOrElse(0) { 0 }, "1")
            assertPrints(intArray.getOrElse(-1) { 0 }, "0")

            val booleanArray: BooleanArray = [true, false]
            assertPrints(booleanArray.getOrElse(0) { false }, "true")
            assertPrints(booleanArray.getOrElse(-1) { false }, "false")

            val charArray: CharArray = ['a', 'b', 'c']
            assertPrints(charArray.getOrElse(0) { 'z' }, "a")
            assertPrints(charArray.getOrElse(-1) { 'z' }, "z")

            // arrays of unsigned types
            val uIntArray: UIntArray = [1u, 2u, 3u]
            assertPrints(uIntArray.getOrElse(0) { 10u }, "1")
            assertPrints(uIntArray.getOrElse(-1) { 10u }, "10")
        }
    }

    class Transformations {

        @Sample
        fun associateArrayOfPrimitives() {
            val charCodes: IntArray = [72, 69, 76, 76, 79]

            val byCharCode = charCodes.associate { it to Char(it) }

            // 76=L only occurs once because only the last pair with the same key gets added
            assertPrints(byCharCode, "{72=H, 69=E, 76=L, 79=O}")
        }


        @Sample
        fun associateArrayOfPrimitivesBy() {
            val charCodes: IntArray = [72, 69, 76, 76, 79]

            val byChar = charCodes.associateBy { Char(it) }

            // L=76 only occurs once because only the last pair with the same key gets added
            assertPrints(byChar, "{H=72, E=69, L=76, O=79}")
        }

        @Sample
        fun associateArrayOfPrimitivesByWithValueTransform() {
            val charCodes: IntArray = [65, 65, 66, 67, 68, 69]

            val byUpperCase = charCodes.associateBy({ Char(it) }, { Char(it + 32) })

            // A=a only occurs once because only the last pair with the same key gets added
            assertPrints(byUpperCase, "{A=a, B=b, C=c, D=d, E=e}")
        }

        @Sample
        fun associateArrayOfPrimitivesByTo() {
            val charCodes: IntArray = [72, 69, 76, 76, 79]
            val byChar = mutableMapOf<Char, Int>()

            assertTrue(byChar.isEmpty())
            charCodes.associateByTo(byChar) { Char(it) }

            assertTrue(byChar.isNotEmpty())
            // L=76 only occurs once because only the last pair with the same key gets added
            assertPrints(byChar, "{H=72, E=69, L=76, O=79}")
        }

        @Sample
        fun associateArrayOfPrimitivesByToWithValueTransform() {
            val charCodes: IntArray = [65, 65, 66, 67, 68, 69]

            val byUpperCase = mutableMapOf<Char, Char>()
            charCodes.associateByTo(byUpperCase, { Char(it) }, { Char(it + 32) })

            // A=a only occurs once because only the last pair with the same key gets added
            assertPrints(byUpperCase, "{A=a, B=b, C=c, D=d, E=e}")
        }

        @Sample
        fun associateArrayOfPrimitivesTo() {
            val charCodes: IntArray = [72, 69, 76, 76, 79]

            val byChar = mutableMapOf<Int, Char>()
            charCodes.associateTo(byChar) { it to Char(it) }

            // 76=L only occurs once because only the last pair with the same key gets added
            assertPrints(byChar, "{72=H, 69=E, 76=L, 79=O}")
        }

        @Sample
        fun flattenArray() {
            val deepArray: Array<Array<Int>> = [
                [1],
                [2, 3],
                [4, 5, 6]
            ]

            assertPrints(deepArray.flatten(), "[1, 2, 3, 4, 5, 6]")
        }

        @Sample
        fun unzipArray() {
            val array: Array<Pair<Int, Char>> = [1 to 'a', 2 to 'b', 3 to 'c']
            assertPrints(array.unzip(), "([1, 2, 3], [a, b, c])")
        }

        @Sample
        fun partitionArrayOfPrimitives() {
            val array: IntArray = [1, 2, 3, 4, 5]
            val [even, odd] = array.partition { it % 2 == 0 }
            assertPrints(even, "[2, 4]")
            assertPrints(odd, "[1, 3, 5]")
        }
    }

    class ContentOperations {

        @Sample
        fun contentToString() {
            val array: Array<String> = ["apples", "oranges", "lime"]

            assertPrints(array.contentToString(), "[apples, oranges, lime]")
        }

        @Sample
        fun contentDeepToString() {
            val matrix: Array<IntArray> = [
                [3, 7, 9],
                [0, 1, 0],
                [2, 4, 8]
            ]

            assertPrints(matrix.contentDeepToString(), "[[3, 7, 9], [0, 1, 0], [2, 4, 8]]")
        }

        @Sample
        fun arrayContentEquals() {
            val array: Array<String> = ["apples", "oranges", "lime"]

            // the same size and equal elements
            assertPrints(array.contentEquals(["apples", "oranges", "lime"]), "true")

            // different size
            assertPrints(array.contentEquals(["apples", "oranges"]), "false")

            // the elements at index 1 are not equal
            assertPrints(array.contentEquals(["apples", "lime", "oranges"]), "false")
        }

        @Sample
        fun charArrayContentEquals() {
            val array: CharArray = ['a', 'b', 'c']

            // the same size and equal elements
            assertPrints(array.contentEquals(['a', 'b', 'c']), "true")

            // different size
            assertPrints(array.contentEquals(['a', 'b']), "false")

            // the elements at index 1 are not equal
            assertPrints(array.contentEquals(['a', 'c', 'b']), "false")
        }

        @Sample
        fun booleanArrayContentEquals() {
            val array: BooleanArray = [true, false, true]

            // the same size and equal elements
            assertPrints(array.contentEquals([true, false, true]), "true")

            // different size
            assertPrints(array.contentEquals([true, false]), "false")

            // the elements at index 1 are not equal
            assertPrints(array.contentEquals([true, true, false]), "false")
        }

        @Sample
        fun intArrayContentEquals() {
            val array: IntArray = [1, 2, 3]

            // the same size and equal elements
            assertPrints(array.contentEquals([1, 2, 3]), "true")

            // different size
            assertPrints(array.contentEquals([1, 2]), "false")

            // the elements at index 1 are not equal
            assertPrints(array.contentEquals([1, 3, 2]), "false")
        }

        @Sample
        fun doubleArrayContentEquals() {
            val array: DoubleArray = [1.0, Double.NaN, 0.0]

            // the same size and equal elements, NaN is equal to NaN
            assertPrints(array.contentEquals([1.0, Double.NaN, 0.0]), "true")

            // different size
            assertPrints(array.contentEquals([1.0, Double.NaN]), "false")

            // the elements at index 2 are not equal, 0.0 is not equal to -0.0
            assertPrints(array.contentEquals([1.0, Double.NaN, -0.0]), "false")

            // the elements at index 1 are not equal
            assertPrints(array.contentEquals([1.0, 0.0, Double.NaN]), "false")
        }

        @Sample
        fun contentDeepEquals() {
            val identityMatrix: Array<IntArray> = [
                [1, 0],
                [0, 1]
            ]
            val reflectionMatrix: Array<IntArray> = [
                [1, 0],
                [0, -1]
            ]

            // the elements at index [1][1] are not equal
            assertPrints(identityMatrix.contentDeepEquals(reflectionMatrix), "false")

            reflectionMatrix[1][1] = 1
            assertPrints(identityMatrix.contentDeepEquals(reflectionMatrix), "true")
        }
    }

    class CopyOfOperations {

        @Sample
        fun copyOf() {
            val array: Array<String> = ["apples", "oranges", "limes"]
            val arrayCopy = array.copyOf()
            assertPrints(arrayCopy.contentToString(), "[apples, oranges, limes]")
        }

        @Sample
        fun resizingCopyOf() {
            val array: Array<String> = ["apples", "oranges", "limes"]
            val arrayCopyPadded = array.copyOf(5)
            assertPrints(arrayCopyPadded.contentToString(), "[apples, oranges, limes, null, null]")
            val arrayCopyTruncated = array.copyOf(2)
            assertPrints(arrayCopyTruncated.contentToString(), "[apples, oranges]")
        }

        @Sample
        fun resizedPrimitiveCopyOf() {
            val array: IntArray = [1, 2, 3]
            val arrayCopyPadded = array.copyOf(5)
            assertPrints(arrayCopyPadded.contentToString(), "[1, 2, 3, 0, 0]")
            val arrayCopyTruncated = array.copyOf(2)
            assertPrints(arrayCopyTruncated.contentToString(), "[1, 2]")
        }

        @Sample
        fun copyOfBooleanArrayWithInitializer() {
            val array: BooleanArray = [true, false, true]
            val truncatedCopy = array.copyOf(2)
            assertPrints(truncatedCopy.contentToString(), "[true, false]")
            val paddedCopy = array.copyOf(5) { it % 2 == 0 }
            assertPrints(paddedCopy.contentToString(), "[true, false, true, false, true]")
        }

        @Sample
        fun copyOfCharArrayWithInitializer() {
            val array: CharArray = ['a', 'b', 'c']
            val truncatedCopy = array.copyOf(2)
            assertPrints(truncatedCopy.contentToString(), "[a, b]")
            val paddedCopy = array.copyOf(5) { '?' }
            assertPrints(paddedCopy.contentToString(), "[a, b, c, ?, ?]")
        }

        @Sample
        fun copyOfByteArrayWithInitializer() {
            val array: ByteArray = [1, 2, 3]
            val truncatedCopy = array.copyOf(2)
            assertPrints(truncatedCopy.contentToString(), "[1, 2]")
            val paddedCopy = array.copyOf(5) { -1 }
            assertPrints(paddedCopy.contentToString(), "[1, 2, 3, -1, -1]")
            val paddedCopyWithIndex = array.copyOf(6) { it.toByte() }
            assertPrints(paddedCopyWithIndex.contentToString(), "[1, 2, 3, 3, 4, 5]")
        }

        @Sample
        fun copyOfShortArrayWithInitializer() {
            val array: ShortArray = [1, 2, 3]
            val truncatedCopy = array.copyOf(2)
            assertPrints(truncatedCopy.contentToString(), "[1, 2]")
            val paddedCopy = array.copyOf(5) { -1 }
            assertPrints(paddedCopy.contentToString(), "[1, 2, 3, -1, -1]")
            val paddedCopyWithIndex = array.copyOf(6) { it.toShort() }
            assertPrints(paddedCopyWithIndex.contentToString(), "[1, 2, 3, 3, 4, 5]")
        }

        @Sample
        fun copyOfIntArrayWithInitializer() {
            val array: IntArray = [1, 2, 3]
            val truncatedCopy = array.copyOf(2)
            assertPrints(truncatedCopy.contentToString(), "[1, 2]")
            val paddedCopy = array.copyOf(5) { -1 }
            assertPrints(paddedCopy.contentToString(), "[1, 2, 3, -1, -1]")
            val paddedCopyWithIndex = array.copyOf(6) { it }
            assertPrints(paddedCopyWithIndex.contentToString(), "[1, 2, 3, 3, 4, 5]")
        }

        @Sample
        fun copyOfLongArrayWithInitializer() {
            val array: LongArray = [1, 2, 3]
            val truncatedCopy = array.copyOf(2)
            assertPrints(truncatedCopy.contentToString(), "[1, 2]")
            val paddedCopy = array.copyOf(5) { -1 }
            assertPrints(paddedCopy.contentToString(), "[1, 2, 3, -1, -1]")
            val paddedCopyWithIndex = array.copyOf(6) { it.toLong() }
            assertPrints(paddedCopyWithIndex.contentToString(), "[1, 2, 3, 3, 4, 5]")
        }

        @Sample
        fun copyOfFloatArrayWithInitializer() {
            val array: FloatArray = [1.0f, 2.0f, 3.0f]
            val truncatedCopy = array.copyOf(2)
            assertPrints(truncatedCopy.contentToString(), "[1.0, 2.0]")
            val paddedCopy = array.copyOf(5) { -1.0f }
            assertPrints(paddedCopy.contentToString(), "[1.0, 2.0, 3.0, -1.0, -1.0]")
        }

        @Sample
        fun copyOfDoubleArrayWithInitializer() {
            val array: DoubleArray = [1.0, 2.0, 3.0]
            val truncatedCopy = array.copyOf(2)
            assertPrints(truncatedCopy.contentToString(), "[1.0, 2.0]")
            val paddedCopy = array.copyOf(5) { -1.0 }
            assertPrints(paddedCopy.contentToString(), "[1.0, 2.0, 3.0, -1.0, -1.0]")
        }

        @Sample
        fun copyOfArrayWithInitializer() {
            val array: Array<String> = ["foo", "bar", "baz"]
            val truncatedCopy = array.copyOf(2)
            assertPrints(truncatedCopy.contentToString(), "[foo, bar]")
            val paddedCopy = array.copyOf(5) { "qux" }
            assertPrints(paddedCopy.contentToString(), "[foo, bar, baz, qux, qux]")
        }

        @Sample
        fun copyOfUByteArrayWithInitializer() {
            val array: UByteArray = [1u, 2u, 3u]
            val truncatedCopy = array.copyOf(2)
            assertPrints(truncatedCopy.contentToString(), "[1, 2]")
            val paddedCopy = array.copyOf(5) { 0xffu }
            assertPrints(paddedCopy.contentToString(), "[1, 2, 3, 255, 255]")
            val paddedCopyWithIndex = array.copyOf(6) { it.toUByte() }
            assertPrints(paddedCopyWithIndex.contentToString(), "[1, 2, 3, 3, 4, 5]")
        }

        @Sample
        fun copyOfUShortArrayWithInitializer() {
            val array: UShortArray = [1u, 2u, 3u]
            val truncatedCopy = array.copyOf(2)
            assertPrints(truncatedCopy.contentToString(), "[1, 2]")
            val paddedCopy = array.copyOf(5) { 0xffu }
            assertPrints(paddedCopy.contentToString(), "[1, 2, 3, 255, 255]")
            val paddedCopyWithIndex = array.copyOf(6) { it.toUShort() }
            assertPrints(paddedCopyWithIndex.contentToString(), "[1, 2, 3, 3, 4, 5]")
        }

        @Sample
        fun copyOfUIntArrayWithInitializer() {
            val array: UIntArray = [1u, 2u, 3u]
            val truncatedCopy = array.copyOf(2)
            assertPrints(truncatedCopy.contentToString(), "[1, 2]")
            val paddedCopy = array.copyOf(5) { 0xffu }
            assertPrints(paddedCopy.contentToString(), "[1, 2, 3, 255, 255]")
            val paddedCopyWithIndex = array.copyOf(6) { it.toUInt() }
            assertPrints(paddedCopyWithIndex.contentToString(), "[1, 2, 3, 3, 4, 5]")
        }

        @Sample
        fun copyOfULongArrayWithInitializer() {
            val array: ULongArray = [1u, 2u, 3u]
            val truncatedCopy = array.copyOf(2)
            assertPrints(truncatedCopy.contentToString(), "[1, 2]")
            val paddedCopy = array.copyOf(5) { 0xffu }
            assertPrints(paddedCopy.contentToString(), "[1, 2, 3, 255, 255]")
            val paddedCopyWithIndex = array.copyOf(6) { it.toULong() }
            assertPrints(paddedCopyWithIndex.contentToString(), "[1, 2, 3, 3, 4, 5]")
        }
    }

    class Sorting {

        @Sample
        fun sortArray() {
            val intArray: IntArray = [4, 3, 2, 1]

            // before sorting
            assertPrints(intArray.joinToString(), "4, 3, 2, 1")

            intArray.sort()

            // after sorting
            assertPrints(intArray.joinToString(), "1, 2, 3, 4")
        }

        @Sample
        fun sortArrayOfComparable() {
            class Person(val firstName: String, val lastName: String) : Comparable<Person> {
                override fun compareTo(other: Person): Int = this.lastName.compareTo(other.lastName)
                override fun toString(): String = "$firstName $lastName"
            }

            val people: Array<Person> = [
                Person("Ragnar", "Lodbrok"),
                Person("Bjorn", "Ironside"),
                Person("Sweyn", "Forkbeard")
            ]

            // before sorting
            assertPrints(people.joinToString(), "Ragnar Lodbrok, Bjorn Ironside, Sweyn Forkbeard")

            people.sort()

            // after sorting
            assertPrints(people.joinToString(), "Sweyn Forkbeard, Bjorn Ironside, Ragnar Lodbrok")

        }

        @Sample
        fun sortRangeOfArray() {
            val intArray: IntArray = [4, 3, 2, 1]

            // before sorting
            assertPrints(intArray.joinToString(), "4, 3, 2, 1")

            intArray.sort(0, 3)

            // after sorting
            assertPrints(intArray.joinToString(), "2, 3, 4, 1")
        }

        @Sample
        fun sortRangeOfArrayOfComparable() {
            class Person(val firstName: String, val lastName: String) : Comparable<Person> {
                override fun compareTo(other: Person): Int = this.lastName.compareTo(other.lastName)
                override fun toString(): String = "$firstName $lastName"
            }

            val people: Array<Person> = [
                Person("Ragnar", "Lodbrok"),
                Person("Bjorn", "Ironside"),
                Person("Sweyn", "Forkbeard")
            ]

            // before sorting
            assertPrints(people.joinToString(), "Ragnar Lodbrok, Bjorn Ironside, Sweyn Forkbeard")

            people.sort(0, 2)

            // after sorting
            assertPrints(people.joinToString(), "Bjorn Ironside, Ragnar Lodbrok, Sweyn Forkbeard")
        }

    }

    class Constructors {
        @Sample
        fun arrayOfSample() {
            val emptyArray: Array<Any> = []
            assertPrints(emptyArray.contentToString(), "[]")

            val strings: Array<String> = ["Hello", "world"]
            assertPrints(strings.contentToString(), "[Hello, world]")

            val numbers: Array<Number> = [3.14, 42L, 0.123f]
            assertPrints(numbers.contentToString(), "[3.14, 42, 0.123]")
        }

        @Sample
        fun doubleArrayOfSample() {
            val emptyDoubleArray: DoubleArray = []
            assertPrints(emptyDoubleArray.contentToString(), "[]")

            val doubleArray: DoubleArray = [1.0, 2.5, 3.14]
            assertPrints(doubleArray.contentToString(), "[1.0, 2.5, 3.14]")
        }

        @Sample
        fun floatArrayOfSample() {
            val emptyFloatArray: FloatArray = []
            assertPrints(emptyFloatArray.contentToString(), "[]")

            val floatArray: FloatArray = [1.0f, 2.5f, 3.14f]
            assertPrints(floatArray.contentToString(), "[1.0, 2.5, 3.14]")
        }

        @Sample
        fun longArrayOfSample() {
            val emptyLongArray: LongArray = []
            assertPrints(emptyLongArray.contentToString(), "[]")

            val longArray: LongArray = [1L, 2L, 3L]
            assertPrints(longArray.contentToString(), "[1, 2, 3]")
        }

        @Sample
        fun intArrayOfSample() {
            val emptyIntArray: IntArray = []
            assertPrints(emptyIntArray.contentToString(), "[]")

            val intArray: IntArray = [1, 2, 3]
            assertPrints(intArray.contentToString(), "[1, 2, 3]")
        }

        @Sample
        fun charArrayOfSample() {
            val emptyCharArray: CharArray = []
            assertPrints(emptyCharArray.contentToString(), "[]")

            val charArray: CharArray = ['a', 'b', 'c']
            assertPrints(charArray.contentToString(), "[a, b, c]")
        }

        @Sample
        fun shortArrayOfSample() {
            val emptyShortArray: ShortArray = []
            assertPrints(emptyShortArray.contentToString(), "[]")

            val shortArray: ShortArray = [1, 2, 3]
            assertPrints(shortArray.contentToString(), "[1, 2, 3]")
        }

        @Sample
        fun byteArrayOfSample() {
            val emptyByteArray: ByteArray = []
            assertPrints(emptyByteArray.contentToString(), "[]")

            val byteArray: ByteArray = [1, 2, 3]
            assertPrints(byteArray.contentToString(), "[1, 2, 3]")
        }

        @Sample
        fun booleanArrayOfSample() {
            val emptyBooleanArray: BooleanArray = []
            assertPrints(emptyBooleanArray.contentToString(), "[]")

            val booleanArray: BooleanArray = [true, false, true]
            assertPrints(booleanArray.contentToString(), "[true, false, true]")
        }
    }

}
