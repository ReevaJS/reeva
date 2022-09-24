package com.reevajs.reeva.runtime.other

import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.utils.*
import kotlin.experimental.inv

object URIParser {
    fun encode(string: String, unescapedSet: Set<Char>): String {
        val strLen = string.length
        val builder = StringBuilder()
        var i = 0

        while (true) {
            if (i == strLen)
                return builder.toString()

            val char = string[i]
            if (char in unescapedSet) {
                i += 1
                builder.append(char)
            } else {
                val codePoint = Operations.codePointAt(string, i)
                if (codePoint.isUnpairedSurrogate)
                    Errors.MalformedURI(string).throwURIError()
                i += codePoint.codeUnitCount

                try {
                    val octets = codePointToOctet(codePoint.codePoint)
                    octets.forEach {
                        builder.append("%%%02X".format(it))
                    }
                } catch (e: IllegalArgumentException) {
                    Errors.MalformedURI(string).throwURIError()
                }
            }
        }
    }

    fun decode(string: String, reservedSet: Set<Char>): String {
        val strLen = string.length
        val builder = StringBuilder()
        var i = 0

        fun hexCharAt(i: Int) = try {
            (string[i].hexValue() shl 4) or string[i + 1].hexValue()
        } catch (e: NumberFormatException) {
            Errors.MalformedURI(string).throwURIError()
        }

        while (true) {
            if (i == strLen)
                return builder.toString()

            val char = string[i]

            if (char != '%') {
                builder.append(char.toString())
            } else {
                val start = i
                if (i + 2 >= strLen)
                    Errors.MalformedURI(string).throwURIError()

                val leadingByteValue = hexCharAt(i + 1)

                i += 2
                val leadingOneBits = leadingByteValue.toByte().inv().countLeadingZeroBits()
                if (leadingOneBits == 0) {
                    val charValue = leadingByteValue.toChar()
                    if (charValue !in reservedSet) {
                        builder.append(charValue)
                    } else {
                        builder.append(string.substring(start, i + 1))
                    }
                } else {
                    if (leadingOneBits == 1 || leadingOneBits > 4)
                        Errors.MalformedURI(string).throwURIError()

                    if (i + (3 * (leadingOneBits - 1)) >= strLen)
                        Errors.MalformedURI(string).throwURIError()

                    // Other values will get overridden in the loop below
                    val octets = mutableListOf(leadingByteValue)
                    var j = 1

                    while (j < leadingOneBits) {
                        i += 1
                        if (string[i] != '%')
                            Errors.MalformedURI(string).throwURIError()

                        val byteValue = hexCharAt(i + 1)

                        i += 2
                        octets.add(byteValue)
                        j += 1
                    }

                    ecmaAssert(octets.size == leadingOneBits)

                    if (!validateOctets(octets))
                        Errors.MalformedURI(string).throwURIError()

                    builder.appendCodePoint(codePoint(octets))
                }
            }

            i += 1
        }
    }

    private fun codePoint(octets: List<Int>): Int {
        return when (octets.size) {
            1 -> octets[0] and 0x7f
            2 -> ((octets[0] and 0x1f) shl 6) or (octets[1] and 0x3f)
            3 -> ((octets[0] and 0xf) shl 12) or ((octets[1] and 0x3f) shl 6) or (octets[2] and 0x3f)
            4 -> ((octets[0] and 0x7) shl 18) or ((octets[1] and 0x3f) shl 12) or ((octets[2] and 0x3f) shl 6) or
                (octets[3] and 0x3f)
            else -> unreachable()
        }
    }

    private fun validateOctets(octets: List<Int>): Boolean {
        return when (octets.size) {
            1 -> octets[0] shr 7 == 0
            2 -> octets[0] shr 5 == 0b110 && validateEndingOctet(octets[1])
            3 -> octets[0] shr 4 == 0b1110 && octets.drop(1).all(::validateEndingOctet)
            4 -> octets[0] shr 3 == 0b11110 && octets.drop(1).all(::validateEndingOctet)
            else -> unreachable()
        }
    }

    private fun validateEndingOctet(octet: Int) = octet shr 6 == 0b10

    private fun codePointToOctet(cp: Int) = when {
        cp <= 0x7f -> listOf(cp)
        cp <= 0x7ff -> listOf(
            0xc0 or (cp shr 6),
            0x80 or (cp and 0x3f),
        )
        cp <= 0xffff -> listOf(
            0xe0 or (cp shr 12),
            0x80 or ((cp shr 6) and 0x3f),
            0x80 or (cp and 0x3f),
        )
        cp <= 0x10ffff -> listOf(
            0xf0 or (cp shr 18),
            0x80 or ((cp shr 12) and 0x3f),
            0x80 or ((cp shr 6) and 0x3f),
            0x80 or (cp and 0x3f),
        )
        else -> unreachable()
    }
}
