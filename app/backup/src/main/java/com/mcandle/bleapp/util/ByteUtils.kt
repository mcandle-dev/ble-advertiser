package com.mcandle.bleapp.util

object ByteUtils {

    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { String.format("%02X", it) }
    }

    fun byteToHex(b: Byte): String {
        return String.format("%02X", b)
    }

    fun bytesToAsciiSafe(bytes: ByteArray): String {
        return bytes.map { (it.toInt() and 0xFF).toChar() }
            .joinToString("")
            .replace(Regex("[^\\x20-\\x7E]"), ".")
    }
}
