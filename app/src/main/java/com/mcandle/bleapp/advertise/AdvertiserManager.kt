package com.mcandle.bleapp.advertise

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.mcandle.bleapp.model.AdvertiseDataModel
import com.mcandle.bleapp.viewmodel.BleAdvertiseViewModel

class AdvertiserManager(
    private val context: Context,
    private val viewModel: BleAdvertiseViewModel
) {
    companion object {
        private const val TAG = "AdvertiserManager"
    }

    private val bluetoothAdapter: BluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val advertiser = bluetoothAdapter.bluetoothLeAdvertiser

    // API 26+ (Android 8.0+)용 AdvertisingSet
    private var currentAdvertisingSet: AdvertisingSet? = null

    // Legacy API (API 25 이하)용 Callback
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.i(TAG, "✅ [Legacy API] Advertising started successfully")
            viewModel.setAdvertising(true)
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(TAG, "❌ [Legacy API] Advertising failed: $errorCode")
            viewModel.setAdvertising(false)
        }
    }

    // API 26+ (Android 8.0+)용 Callback
    @RequiresApi(Build.VERSION_CODES.O)
    private val advertisingSetCallback = object : AdvertisingSetCallback() {
        override fun onAdvertisingSetStarted(
            advertisingSet: AdvertisingSet?,
            txPower: Int,
            status: Int
        ) {
            if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                currentAdvertisingSet = advertisingSet
                Log.i(TAG, "✅ [Modern API] AdvertisingSet started successfully (Legacy Mode)")
                Log.d(TAG, "TX Power: $txPower dBm")
                viewModel.setAdvertising(true)
            } else {
                Log.e(TAG, "❌ [Modern API] AdvertisingSet failed: status=$status")
                viewModel.setAdvertising(false)
            }
        }

        override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
            Log.i(TAG, "ℹ️ [Modern API] AdvertisingSet stopped")
            currentAdvertisingSet = null
        }
    }

    fun isSupported(): Boolean = bluetoothAdapter.isMultipleAdvertisementSupported

    fun startAdvertise(data: AdvertiseDataModel) {
        bluetoothAdapter.name = data.deviceName
        val advData = AdvertisePacketBuilder.buildAdvertiseData(data)
        val scanResp = AdvertisePacketBuilder.buildScanResponse(data)

        // Android API 버전별 분기
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // API 26+ (Android 8.0+): AdvertisingSetParameters 사용
            // AOSP 11 Scanner 호환을 위해 Legacy Mode 강제
            startAdvertiseModern(advData, scanResp)
        } else {
            // API 25 이하: 기존 AdvertiseSettings 사용 (자동으로 Legacy Mode)
            startAdvertiseLegacy(advData, scanResp)
        }
    }

    /**
     * API 26+ (Android 8.0+)용 Advertise 시작
     * Legacy Mode 강제 설정으로 AOSP 11 Scanner 호환
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun startAdvertiseModern(advData: AdvertiseData, scanResp: AdvertiseData) {
        Log.d(TAG, "🚀 [Modern API] Starting AdvertisingSet with Legacy Mode (AOSP 11 호환)")

        val parameters = AdvertisingSetParameters.Builder()
            // ✅ Legacy Mode 강제 (AOSP 11 Scanner 호환을 위해 필수!)
            .setLegacyMode(true)

            // Connectable 설정 (GATT 연결 허용)
            .setConnectable(true)

            // Interval 설정 (LOW_LATENCY: 빠른 검색, 배터리 소모 큼)
            .setInterval(AdvertisingSetParameters.INTERVAL_LOW)

            // TX Power 설정 (HIGH: 긴 도달 거리)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)

            // Primary PHY 설정 (1M PHY 사용, Extended Advertising 방지)
            .setPrimaryPhy(BluetoothDevice.PHY_LE_1M)
            .setSecondaryPhy(BluetoothDevice.PHY_LE_1M)

            // Scannable 설정 (Scan Response 전송 허용)
            .setScannable(true)

            .build()

        try {
            // AdvertisingSet 시작
            advertiser.startAdvertisingSet(
                parameters,
                advData,
                scanResp,
                null,  // periodicParameters (사용 안함)
                null,  // periodicData (사용 안함)
                advertisingSetCallback
            )
            Log.d(TAG, "Legacy Mode: true (AOSP 11 Scanner 호환)")
            Log.d(TAG, "Connectable: true")
            Log.d(TAG, "Primary PHY: 1M")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception starting AdvertisingSet", e)
            viewModel.setAdvertising(false)
        }
    }

    /**
     * API 25 이하용 Advertise 시작
     * 구형 API는 자동으로 Legacy Mode 사용
     */
    private fun startAdvertiseLegacy(advData: AdvertiseData, scanResp: AdvertiseData) {
        Log.d(TAG, "🚀 [Legacy API] Starting Advertising (자동 Legacy Mode)")

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        try {
            advertiser.startAdvertising(settings, advData, scanResp, advertiseCallback)
            Log.d(TAG, "Legacy Mode: true (자동, API 25 이하)")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception starting Advertising", e)
            viewModel.setAdvertising(false)
        }
    }

    fun stopAdvertise() {
        // API 26+ AdvertisingSet 중지
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && currentAdvertisingSet != null) {
            try {
                advertiser.stopAdvertisingSet(advertisingSetCallback)
                currentAdvertisingSet = null
                Log.d(TAG, "✅ [Modern API] AdvertisingSet stopped")
            } catch (e: SecurityException) {
                Log.e(TAG, "❌ Security exception stopping AdvertisingSet", e)
            }
        }

        // Legacy API Advertising 중지
        try {
            advertiser.stopAdvertising(advertiseCallback)
            Log.d(TAG, "✅ [Legacy API] Advertising stopped")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception stopping Advertising", e)
        }

        viewModel.setAdvertising(false)
    }
}
