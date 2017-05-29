/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package konan.internal

@SymbolName("Konan_NumberConverter_bigIntDigitGeneratorInstImpl")
private external fun bigIntDigitGeneratorInstImpl(results: IntArray, uArray: IntArray, f: Long, e: Int,
                                                  isDenormalized: Boolean, mantissaIsZero: Boolean, p: Int)

@SymbolName("Konan_NumberConverter_ceil")
private external fun ceil(x: Double): Double

class NumberConverter {

    private var setCount: Int = 0 // number of times u and k have been gotten

    private var getCount: Int = 0 // number of times u and k have been set

    private val uArray = IntArray(64)

    private var firstK: Int = 0

    fun convertD(inputNumber: Double): String {
        val p = 1023 + 52 // the power offset (precision)
        val signMask = 0x7FFFFFFFFFFFFFFFL + 1//0x8000000000000000L // the mask to get the sign of
        // the number
        val eMask = 0x7FF0000000000000L // the mask to get the power bits
        val fMask = 0x000FFFFFFFFFFFFFL // the mask to get the significand
        // bits

        val inputNumberBits = inputNumber.bits()
        // the value of the sign... 0 is positive, ~0 is negative
        val signString = if (inputNumberBits and signMask == 0L) "" else "-"
        // the value of the 'power bits' of the inputNumber
        val e = (inputNumberBits and eMask shr 52).toInt()
        // the value of the 'significand bits' of the inputNumber
        var f = inputNumberBits and fMask
        val mantissaIsZero = f == 0L
        var pow = 0
        var numBits = 52

        if (e == 2047)
            return if (mantissaIsZero) signString + "Infinity" else "NaN"
        if (e == 0) {
            if (mantissaIsZero)
                return signString + "0.0"
            if (f == 1L)
            // special case to increase precision even though 2 *
            // Double.MIN_VALUE is 1.0e-323
                return signString + "4.9E-324"
            pow = 1 - p // a denormalized number
            var ff = f
            while (ff and 0x0010000000000000L == 0L) {
                ff = ff shl 1
                numBits--
            }
        } else {
            // 0 < e < 2047
            // a "normalized" number
            f = f or 0x0010000000000000L
            pow = e - p
        }

        if (-59 < pow && pow < 6 || pow == -59 && !mantissaIsZero)
            longDigitGenerator(f, pow, e == 0, mantissaIsZero, numBits)
        else
            bigIntDigitGeneratorInstImpl(f, pow, e == 0, mantissaIsZero, numBits)

        if (inputNumber >= 1e7 || inputNumber <= -1e7
                || inputNumber > -1e-3 && inputNumber < 1e-3)
            return signString + freeFormatExponential()

        return signString + freeFormat()
    }

    fun convertF(inputNumber: Float): String {
        val p = 127 + 23 // the power offset (precision)
        val signMask = 0x7FFFFFFF + 1 // the mask to get the sign of the number
        val eMask = 0x7F800000 // the mask to get the power bits
        val fMask = 0x007FFFFF // the mask to get the significand bits

        val inputNumberBits = inputNumber.bits()
        // the value of the sign... 0 is positive, ~0 is negative
        val signString = if (inputNumberBits and signMask == 0) "" else "-"
        // the value of the 'power bits' of the inputNumber
        val e = inputNumberBits and eMask shr 23
        // the value of the 'significand bits' of the inputNumber
        var f = inputNumberBits and fMask
        val mantissaIsZero = f == 0
        var pow = 0
        var numBits = 23

        if (e == 255)
            return if (mantissaIsZero) signString + "Infinity" else "NaN"
        if (e == 0) {
            if (mantissaIsZero)
                return signString + "0.0"
            pow = 1 - p // a denormalized number
            if (f < 8) { // want more precision with smallest values
                f = f shl 2
                pow -= 2
            }
            var ff = f
            while (ff and 0x00800000 == 0) {
                ff = ff shl 1
                numBits--
            }
        } else {
            // 0 < e < 255
            // a "normalized" number
            f = f or 0x00800000
            pow = e - p
        }

        if (-59 < pow && pow < 35 || pow == -59 && !mantissaIsZero)
            longDigitGenerator(f.toLong(), pow, e == 0, mantissaIsZero, numBits)
        else
            bigIntDigitGeneratorInstImpl(f.toLong(), pow, e == 0, mantissaIsZero, numBits)
        if (inputNumber >= 1e7f || inputNumber <= -1e7f
                || inputNumber > -1e-3f && inputNumber < 1e-3f)
            return signString + freeFormatExponential()

        return signString + freeFormat()
    }

    private fun freeFormatExponential(): String {
        // corresponds to process "Free-Format Exponential"
        val formattedDecimal = CharArray(25)
        formattedDecimal[0] = ('0' + uArray[getCount++])
        formattedDecimal[1] = '.'
        // the position the next character is to be inserted into
        // formattedDecimal
        var charPos = 2

        var k = firstK
        val expt = k
        while (true) {
            k--
            if (getCount >= setCount)
                break

            formattedDecimal[charPos++] = ('0' + uArray[getCount++])
        }

        if (k == expt - 1)
            formattedDecimal[charPos++] = '0'
        formattedDecimal[charPos++] = 'E'
        return fromCharArray(formattedDecimal, 0, charPos) + expt.toString()
    }

    private fun freeFormat(): String {
        // corresponds to process "Free-Format"
        val formattedDecimal = CharArray(25)
        // the position the next character is to be inserted into
        // formattedDecimal
        var charPos = 0
        var k = firstK
        if (k < 0) {
            formattedDecimal[0] = '0'
            formattedDecimal[1] = '.'
            charPos += 2
            for (i in k + 1 .. -1)
                formattedDecimal[charPos++] = '0'
        }

        var U = uArray[getCount++]
        do {
            if (U != -1)
                formattedDecimal[charPos++] = ('0' + U)
            else if (k >= -1)
                formattedDecimal[charPos++] = '0'

            if (k == 0)
                formattedDecimal[charPos++] = '.'

            k--
            U = if (getCount < setCount) uArray[getCount++] else -1
        } while (U != -1 || k >= -1)
        return fromCharArray(formattedDecimal, 0, charPos)
    }

    private fun bigIntDigitGeneratorInstImpl(f: Long, e: Int,
                                             isDenormalized: Boolean, mantissaIsZero: Boolean, p: Int) {
        val results = IntArray(3)
        bigIntDigitGeneratorInstImpl(results, uArray, f, e, isDenormalized, mantissaIsZero, p)
        setCount = results[0]
        getCount = results[1]
        firstK   = results[2]
    }

    private fun longDigitGenerator(f: Long, e: Int, isDenormalized: Boolean,
                                   mantissaIsZero: Boolean, p: Int) {
        var R: Long
        var S: Long
        var M: Long
        if (e >= 0) {
            M = 1L shl e
            if (!mantissaIsZero) {
                R = f shl e + 1
                S = 2
            } else {
                R = f shl e + 2
                S = 4
            }
        } else {
            M = 1
            if (isDenormalized || !mantissaIsZero) {
                R = f shl 1
                S = 1L shl 1 - e
            } else {
                R = f shl 2
                S = 1L shl 2 - e
            }
        }

        val k = ceil((e + p - 1) * invLogOfTenBaseTwo - 1e-10).toInt()

        if (k > 0) {
            S *= TEN_TO_THE[k]
        } else if (k < 0) {
            val scale = TEN_TO_THE[-k]
            R *= scale
            M = if (M == 1L) scale else M * scale
        }

        if (R + M > S) { // was M_plus
            firstK = k
        } else {
            firstK = k - 1
            R *= 10
            M *= 10
        }

        setCount = 0
        getCount = setCount // reset indices
        var low: Boolean
        var high: Boolean
        var U: Int
        val Si = longArrayOf(S, S shl 1, S shl 2, S shl 3)
        while (true) {
            // set U to be floor (R / S) and R to be the remainder
            // using a kind of "binary search" to find the answer.
            // It's a lot quicker than actually dividing since we know
            // the answer will be between 0 and 10
            U = 0
            var remainder: Long
            for (i in 3 downTo 0) {
                remainder = R - Si[i]
                if (remainder >= 0) {
                    R = remainder
                    U += 1 shl i
                }
            }

            low = R < M // was M_minus
            high = R + M > S // was M_plus

            if (low || high)
                break

            R *= 10
            M *= 10
            uArray[setCount++] = U
        }
        if (low && !high)
            uArray[setCount++] = U
        else if (high && !low)
            uArray[setCount++] = U + 1
        else if (R shl 1 < S)
            uArray[setCount++] = U
        else
            uArray[setCount++] = U + 1
    }

    companion object {

        private val invLogOfTenBaseTwo = 0.30102999566398114251 // Math.log(2.0) / Math.log(10.0)

        private val TEN_TO_THE = LongArray(20)

        init {
            TEN_TO_THE[0] = 1L
            for (i in 1..TEN_TO_THE.size - 1) {
                val previous = TEN_TO_THE[i - 1]
                TEN_TO_THE[i] = (previous shl 1) + (previous shl 3)
            }
        }

        private val converter: NumberConverter
            get() = NumberConverter()

        fun convert(input: Double): String {
            return converter.convertD(input)
        }

        fun convert(input: Float): String {
            return converter.convertF(input)
        }
    }
}