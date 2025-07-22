package com.mcandle.bleapp.advertise

import android.bluetooth.le.AdvertiseData
import android.os.ParcelUuid
import com.mcandle.bleapp.model.AdvertiseDataModel

object AdvertisePacketBuilder {
    fun buildAdvertiseData(data: AdvertiseDataModel): AdvertiseData {
        val serviceUuid = ParcelUuid.fromString("0000FE10-0000-1000-8000-00805F9B34FB")
        val serviceData = (data.cardNumber + if (data.kakaoPayInstalled) "Y" else "N")
            .toByteArray(Charsets.UTF_8)

        return AdvertiseData.Builder()
            .addServiceData(serviceUuid, serviceData)
            .setIncludeTxPowerLevel(true)
            .build()
    }

    fun buildScanResponse(data: AdvertiseDataModel): AdvertiseData {
        return AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()
    }
}