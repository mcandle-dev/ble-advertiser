package com.mcandle.bleapp.gatt

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import java.nio.charset.Charset

/**
 * GATT Server 관리 클래스
 *
 * 외부 Scanner가 연결하여 order_id를 write하면
 * 콜백을 통해 MainActivity에 전달
 */
class GattServerManager(
    private val context: Context,
    private val callback: GattServerCallback
) {

    companion object {
        private const val TAG = "GattServerManager"
    }

    interface GattServerCallback {
        /**
         * AT+CONNECT 명령어를 수신했을 때 호출
         */
        fun onConnectCommandReceived(device: BluetoothDevice)

        /**
         * Order 데이터가 수신되었을 때 호출
         *
         * @param orderId 주문 ID
         * @param additionalData 추가 데이터 (phone, amount 등)
         */
        fun onOrderReceived(orderId: String, additionalData: Map<String, String>?)

        /**
         * 클라이언트가 연결되었을 때 호출
         */
        fun onClientConnected(device: BluetoothDevice)

        /**
         * 클라이언트가 연결 해제되었을 때 호출
         */
        fun onClientDisconnected(device: BluetoothDevice)
    }

    private var bluetoothGattServer: BluetoothGattServer? = null
    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var responseCharacteristic: BluetoothGattCharacteristic? = null

    /**
     * GATT Server 시작
     *
     * @return 성공 여부
     */
    fun startGattServer(): Boolean {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not available or not enabled")
            return false
        }

        try {
            bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)

            if (bluetoothGattServer == null) {
                Log.e(TAG, "Failed to open GATT server")
                return false
            }

            // Service 생성
            val service = BluetoothGattService(
                GattServiceConfig.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            // Write Characteristic 추가 (Scanner → Store)
            val writeCharacteristic = BluetoothGattCharacteristic(
                GattServiceConfig.CHAR_ORDER_WRITE_UUID,
                GattServiceConfig.PROPERTY_WRITE,
                GattServiceConfig.PERMISSION_WRITE
            )
            service.addCharacteristic(writeCharacteristic)

            // Read Characteristic 추가 (Store → Scanner)
            responseCharacteristic = BluetoothGattCharacteristic(
                GattServiceConfig.CHAR_RESPONSE_READ_UUID,
                GattServiceConfig.PROPERTY_READ,
                GattServiceConfig.PERMISSION_READ
            )
            service.addCharacteristic(responseCharacteristic!!)

            // Service를 GATT Server에 추가
            val result = bluetoothGattServer?.addService(service) ?: false

            if (result) {
                Log.d(TAG, "GATT Server started successfully")
                Log.d(TAG, "Service UUID: ${GattServiceConfig.SERVICE_UUID}")
                Log.d(TAG, "Write Char UUID: ${GattServiceConfig.CHAR_ORDER_WRITE_UUID}")
                Log.d(TAG, "Read Char UUID: ${GattServiceConfig.CHAR_RESPONSE_READ_UUID}")
            } else {
                Log.e(TAG, "Failed to add service to GATT server")
            }

            return result
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting GATT server", e)
            return false
        }
    }

    /**
     * GATT Server 중지
     */
    fun stopGattServer() {
        try {
            bluetoothGattServer?.clearServices()
            bluetoothGattServer?.close()
            bluetoothGattServer = null
            responseCharacteristic = null
            Log.d(TAG, "GATT Server stopped")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception stopping GATT server", e)
        }
    }

    /**
     * 응답 데이터 전송 (현재는 Read로 응답하므로 내부적으로 사용)
     */
    private fun setResponse(data: ByteArray) {
        responseCharacteristic?.value = data
        Log.d(TAG, "Response data set: ${String(data)}")
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(
            device: BluetoothDevice,
            status: Int,
            newState: Int
        ) {
            try {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "Client connected: ${device.address}")
                        callback.onClientConnected(device)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Client disconnected: ${device.address}")
                        callback.onClientDisconnected(device)
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in connection state change", e)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            val dataString = String(value, Charset.forName("UTF-8"))
            Log.d(TAG, "Write request from ${device.address}")
            Log.d(TAG, "Characteristic UUID: ${characteristic.uuid}")
            Log.d(TAG, "Data: $dataString")

            try {
                if (characteristic.uuid == GattServiceConfig.CHAR_ORDER_WRITE_UUID) {
                    // AT+CONNECT 명령어 확인
                    if (dataString.trim().equals("AT+CONNECT", ignoreCase = true)) {
                        Log.d(TAG, "AT+CONNECT command received")

                        // 콜백 호출
                        callback.onConnectCommandReceived(device)

                        // 응답 설정
                        val response = OrderDataParser.createResponse(true, "Connected")
                        setResponse(response)

                        // Write 응답
                        if (responseNeeded) {
                            bluetoothGattServer?.sendResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_SUCCESS,
                                offset,
                                value
                            )
                        }
                        return
                    }

                    // order_id 파싱
                    try {
                        val orderRequest = OrderDataParser.parse(value)
                        Log.d(TAG, "Order parsed - ID: ${orderRequest.orderId}")

                        // 콜백 호출
                        callback.onOrderReceived(orderRequest.orderId, orderRequest.additionalData)

                        // 응답 설정
                        val response = OrderDataParser.createResponse(true, "Order received")
                        setResponse(response)

                        // Write 응답
                        if (responseNeeded) {
                            bluetoothGattServer?.sendResponse(
                                device,
                                 requestId,
                                BluetoothGatt.GATT_SUCCESS,
                                offset,
                                value
                            )
                        }
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "Failed to parse order data", e)

                        // 에러 응답
                        val errorResponse = OrderDataParser.createResponse(false, e.message ?: "Parse error")
                        setResponse(errorResponse)

                        if (responseNeeded) {
                            bluetoothGattServer?.sendResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_FAILURE,
                                offset,
                                null
                            )
                        }
                    }
                } else {
                    Log.w(TAG, "Unknown characteristic write: ${characteristic.uuid}")
                    if (responseNeeded) {
                        bluetoothGattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            offset,
                            null
                        )
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in write request", e)
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(TAG, "Read request from ${device.address}")

            try {
                if (characteristic.uuid == GattServiceConfig.CHAR_RESPONSE_READ_UUID) {
                    val response = responseCharacteristic?.value
                        ?: OrderDataParser.createResponse(true, "No data")

                    bluetoothGattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        response
                    )
                    Log.d(TAG, "Response sent: ${String(response)}")
                } else {
                    bluetoothGattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        offset,
                        null
                    )
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in read request", e)
            }
        }
    }
}
