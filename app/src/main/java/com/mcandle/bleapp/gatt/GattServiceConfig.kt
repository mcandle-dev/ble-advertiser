package com.mcandle.bleapp.gatt

import android.bluetooth.BluetoothGattCharacteristic
import java.util.UUID

/**
 * mCandle GATT Service 설정
 *
 * Service 구조:
 * - Service UUID: 0000fff0-0000-1000-8000-00805f9b34fb
 * - Order Write Characteristic: 0000fff1-0000-1000-8000-00805f9b34fb (Scanner → Store)
 * - Response Read Characteristic: 0000fff2-0000-1000-8000-00805f9b34fb (Store → Scanner)
 */
object GattServiceConfig {

    // mCandle GATT Service UUID
    val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")

    // Order Write Characteristic (Scanner가 order_id를 write)
    val CHAR_ORDER_WRITE_UUID: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")

    // Response Read Characteristic (Store가 응답을 보냄)
    val CHAR_RESPONSE_READ_UUID: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

    // Descriptor UUID for notifications
    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Characteristic Properties
    const val PROPERTY_WRITE = BluetoothGattCharacteristic.PROPERTY_WRITE or
                                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE

    const val PROPERTY_READ = BluetoothGattCharacteristic.PROPERTY_READ or
                               BluetoothGattCharacteristic.PROPERTY_NOTIFY

    // Characteristic Permissions
    const val PERMISSION_WRITE = BluetoothGattCharacteristic.PERMISSION_WRITE
    const val PERMISSION_READ = BluetoothGattCharacteristic.PERMISSION_READ
}
