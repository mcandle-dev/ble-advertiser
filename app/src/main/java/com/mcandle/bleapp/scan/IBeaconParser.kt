package com.mcandle.bleapp.scan

import android.bluetooth.le.ScanResult
import android.util.Log
import java.nio.charset.Charset

/**
 * iBeacon 유사 Manufacturer Data 파서
 *
 * 기대 형식 (Manufacturer ID = 0x004C):
 * [0]   = 0x02
 * [1]   = 0x15
 * [2..17]  = UUID 16B (ASCII)  → 주문번호(1~12, space padding) + 전화번호 4자리
 * [18..19] = major (BE)
 * [20..21] = minor (BE)
 * [22]     = txPower (signed)
 */
object IBeaconParser {
    private const val TAG = "IBeaconParser"

    data class IBeaconFrame(
        val uuidAscii: String, // 16B ASCII
        val orderNumber: String, // uuidAscii[0..11] (trimEnd로 공백 제거)
        val phoneLast4: String,  // uuidAscii[12..15]
        val major: Int,
        val minor: Int,
        val txPower: Int
    )

    /**
     * ScanResult에서 Apple(0x004C) Manufacturer Data를 꺼내고 iBeacon 프레임으로 파싱
     * - 실패 시 null
     */
    fun parseFrom(result: ScanResult): IBeaconFrame? {
        val record = result.scanRecord ?: return null
        val data = record.getManufacturerSpecificData(0x004C) ?: return null
        // 최소 길이: 2(prefix) + 16(uuid) + 2(major) + 2(minor) + 1(tx) = 23
        if (data.size < 23) return null
        if (data[0] != 0x02.toByte() || data[1] != 0x15.toByte()) return null

        return try {
            val uuidBytes = data.copyOfRange(2, 18) // 16 bytes
            val uuidAscii = uuidBytes.toString(Charset.forName("US-ASCII"))

            // Safety: 16자 보장
            if (uuidAscii.length != 16) return null

            val orderNumber = uuidAscii.substring(0, 12).trimEnd(' ')
            val phone4 = uuidAscii.substring(12, 16)

            val major = ((data[18].toInt() and 0xFF) shl 8) or (data[19].toInt() and 0xFF)
            val minor = ((data[20].toInt() and 0xFF) shl 8) or (data[21].toInt() and 0xFF)
            val txPower = data[22].toInt() // signed

            IBeaconFrame(
                uuidAscii = uuidAscii,
                orderNumber = orderNumber,
                phoneLast4 = phone4,
                major = major,
                minor = minor,
                txPower = txPower
            )
        } catch (t: Throwable) {
            Log.w(TAG, "parse error", t)
            null
        }
    }
}
