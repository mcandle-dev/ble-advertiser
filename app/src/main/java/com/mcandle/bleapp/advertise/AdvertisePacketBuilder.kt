package com.mcandle.bleapp.advertise

import android.bluetooth.le.AdvertiseData
import android.os.ParcelUuid
import com.mcandle.bleapp.model.AdvertiseDataModel
import com.mcandle.bleapp.model.EncodingType

object AdvertisePacketBuilder {

    fun toBcd(num: String): ByteArray {
        require(num.length % 2 == 0) {"짝수 자리여야 합니다."}
        return num.chunked(2).map {
            ((it[0].digitToInt() shl 4) or it[1].digitToInt()).toByte()
        }.toByteArray()
    }
    fun buildAdvertiseData(data: AdvertiseDataModel): AdvertiseData {
        val serviceUuid = ParcelUuid.fromString("0000FE10-0000-1000-8000-00805F9B34FB")
        val cardByte = when (data.encoding) {
            EncodingType.ASCII -> data.cardNumber.toByteArray(Charsets.UTF_8)
            EncodingType.BCD -> toBcd(data.cardNumber)
        }
        val kakaoByte = if (data.kakaoPayInstalled) 'Y'.code.toByte() else 'N'.code.toByte()
        val payload = cardByte + kakaoByte

        return AdvertiseData.Builder()
            .addServiceData(serviceUuid, payload)
            .setIncludeTxPowerLevel(true)
            .build()
    }

    fun buildScanResponse(data: AdvertiseDataModel): AdvertiseData {
        return AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()
    }
}