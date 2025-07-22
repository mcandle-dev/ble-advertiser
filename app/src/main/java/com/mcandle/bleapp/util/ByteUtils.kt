package com.mcandle.bleapp.util

object ByteUtils {
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { String.format("%02X", it) }
    }
}