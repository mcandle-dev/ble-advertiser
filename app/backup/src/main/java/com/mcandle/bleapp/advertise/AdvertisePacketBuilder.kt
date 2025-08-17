package com.mcandle.bleapp.advertise

import android.bluetooth.le.AdvertiseData
import android.os.ParcelUuid
import com.mcandle.bleapp.model.AdvertiseDataModel
import com.mcandle.bleapp.model.EncodingType
import com.mcandle.bleapp.model.AdvertiseMode
import com.mcandle.bleapp.util.ByteUtils

object AdvertisePacketBuilder {

    fun toBcd(num: String): ByteArray {
        require(num.length % 2 == 0) { "짝수 자리여야 합니다." }
        return num.chunked(2).map {
            ((it[0].digitToInt() shl 4) or it[1].digitToInt()).toByte()
        }.toByteArray()
    }

    fun buildAdvertiseData(data: AdvertiseDataModel): AdvertiseData {

        return when (data.advertiseMode) {
            AdvertiseMode.MINIMAL -> {
                val uuidStr = makeMinimalUuid(data.cardNumber, data.phoneLast4)
                val dynamicUuid = ParcelUuid.fromString(uuidStr)
                AdvertiseData.Builder()
                    .addServiceUuid(dynamicUuid)
                    .build()
            }
            AdvertiseMode.DATA -> {
                val serviceUuid = ParcelUuid.fromString("0000FE10-0000-1000-8000-00805F9B34FB")
                val payload = when (data.encoding) {
                    EncodingType.ASCII -> (data.cardNumber+data.phoneLast4).toByteArray(Charsets.UTF_8)
                    EncodingType.BCD -> toBcd(data.cardNumber+data.phoneLast4)
                }

                AdvertiseData.Builder()
                    .addServiceData(serviceUuid, payload)
                    .setIncludeTxPowerLevel(true)
                    .build()
            }
        }
    }

    fun buildScanResponse(data: AdvertiseDataModel): AdvertiseData {
        return AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()
    }

    // Raw Hex 추출: ServiceData, ServiceUuid 등 모두 보기 쉽게 출력
    fun getAdvertiseRawHex(data: AdvertiseDataModel): String {
        val advertiseData = buildAdvertiseData(data)
        val sb = StringBuilder()

        // Service Data
        advertiseData.serviceData?.forEach { (uuid, bytes) ->
            sb.append("ServiceData(${uuid.uuid}): ")
            sb.append(ByteUtils.bytesToHex(bytes))
            sb.append("\n")
        }
        // Service UUID
        advertiseData.serviceUuids?.forEach { uuid ->
            sb.append("ServiceUuid: ${uuid.uuid}\n")
        }
        // Device Name
        sb.append("DeviceName: ${data.deviceName}\n")
        return sb.toString().trim()
    }

    private  fun makeMinimalUuid(cardNumber: String, phoneLast4: String): String {
        val part1 = cardNumber.substring(0, 8)
        val part2 = cardNumber.substring(8, 12)
        val part3 = cardNumber.substring(12, 16)
        val part4 = phoneLast4
        val base = "00805F9B34FB"

        return "$part1-$part2-$part3-$part4-$base"

    }
}
