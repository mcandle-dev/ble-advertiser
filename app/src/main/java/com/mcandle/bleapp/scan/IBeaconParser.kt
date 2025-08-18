package com.mcandle.bleapp.scan

import android.bluetooth.le.ScanResult
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

object IBeaconParser {

    private const val TAG = "IBeaconParser"

    data class IBeaconFrame(
        val companyId: Int,        // 제조사 ID
        val uuid: String,          // 전체 UUID (16바이트)
        val orderNumber: String,   // 1~12자리 (space padding)
        val phoneLast4: String,    // 13~16자리
        val major: Int,
        val minor: Int,
        val txPower: Int
    )

    /**
     * ScanResult에서 Manufacturer Data 찾아서 iBeacon 형식이면 Frame 반환
     */
    fun parseFrom(result: ScanResult): IBeaconFrame? {
        val manuData = result.scanRecord?.manufacturerSpecificData ?: return null
        for (i in 0 until manuData.size()) {
            val companyId = manuData.keyAt(i)
            val data = manuData.valueAt(i)

            if (data != null && data.size >= 23 &&
                data[0] == 0x02.toByte() && data[1] == 0x15.toByte()
            ) {
                return parse(data, companyId)
            }
        }
        return null
    }

    /**
     * iBeacon raw manufacturer data 파싱
     */
    fun parse(data: ByteArray, companyId: Int = 0): IBeaconFrame? {
        if (data.size < 23) {
            Log.w(TAG, "Manufacturer data too short for iBeacon: ${data.size}")
            return null
        }
        try {
            // iBeacon 구조: [0]=0x02, [1]=0x15, [2..17]=UUID(16B), [18..19]=Major, [20..21]=Minor, [22]=TxPower
            val uuidBytes = data.copyOfRange(2, 18)
            val uuidAscii = uuidBytes.toString(Charsets.US_ASCII)
            val uuidHex = uuidBytes.joinToString("") { String.format("%02X", it) }

            val orderNumber = uuidAscii.substring(0, 12).trimEnd()
            val phoneLast4 = uuidAscii.substring(12, 16)

            val major = ByteBuffer.wrap(data.copyOfRange(18, 20))
                .order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
            val minor = ByteBuffer.wrap(data.copyOfRange(20, 22))
                .order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
            val txPower = data[22].toInt()

            return IBeaconFrame(companyId, uuidHex, orderNumber, phoneLast4, major, minor, txPower)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse iBeacon: ${e.message}")
        }
        return null
    }
}
