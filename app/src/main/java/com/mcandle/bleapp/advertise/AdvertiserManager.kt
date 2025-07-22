package com.mcandle.bleapp.advertise

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.util.Log
import com.mcandle.bleapp.model.AdvertiseDataModel
import com.mcandle.bleapp.viewmodel.BleAdvertiseViewModel

class AdvertiserManager(
    private val context: Context,
    private val viewModel: BleAdvertiseViewModel
) {
    private val bluetoothAdapter: BluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val advertiser = bluetoothAdapter.bluetoothLeAdvertiser

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.i("BLE", "Advertising started successfully")
            viewModel.setAdvertising(true)
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e("BLE", "Advertising failed: $errorCode")
            viewModel.setAdvertising(false)
        }
    }

    fun isSupported(): Boolean = bluetoothAdapter.isMultipleAdvertisementSupported

    fun startAdvertise(data: AdvertiseDataModel) {
        bluetoothAdapter.name = data.deviceName
        val advData = AdvertisePacketBuilder.buildAdvertiseData(data)
        val scanResp = AdvertisePacketBuilder.buildScanResponse(data)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        advertiser.startAdvertising(settings, advData, scanResp, advertiseCallback)
    }

    fun stopAdvertise() {
        advertiser.stopAdvertising(advertiseCallback)
        viewModel.setAdvertising(false)
    }
}
