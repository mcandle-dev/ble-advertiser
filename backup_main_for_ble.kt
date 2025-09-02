package com.mcandle.bleapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mcandle.bleapp.databinding.ActivityMainBinding
import com.mcandle.bleapp.scan.BleScannerManager
import com.mcandle.bleapp.scan.IBeaconParser
import com.mcandle.bleapp.viewmodel.BleAdvertiseViewModel

class MainActivity : AppCompatActivity(), BleScannerManager.Listener {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: BleAdvertiseViewModel by viewModels()
    private lateinit var scannerManager: BleScannerManager
    private var pendingPhone4: String? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val granted = perms.values.all { it }
            if (granted) {
                pendingPhone4?.let {
                    startScan(it)
                    pendingPhone4 = null
                }
            } else {
                showToast("필수 권한이 거부되었습니다.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scannerManager = BleScannerManager(this, this)

        // 📌 ViewModel 관찰
        viewModel.inputPhoneLast4.observe(this) { phone4 ->
            if (!phone4.isNullOrEmpty() && phone4.length == 4) {
                Log.d("MainActivity", "ViewModel에서 phone4 업데이트: $phone4")
                ensurePermissionsAndScan(phone4)
            }
        }
    }

    private fun ensurePermissionsAndScan(phone4: String) {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (needed.isEmpty()) {
            startScan(phone4)
        } else {
            pendingPhone4 = phone4
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScan(phone4: String) {
        scannerManager.startScan(phone4)
        Log.d("MainActivity", "스캔 시작 (phone4=$phone4)")
    }

    // 🔹 Listener 구현
    override fun onMatch(frame: IBeaconParser.IBeaconFrame) {
        viewModel.setScanning(false)
        Log.d("MainActivity", "매칭 성공! order=${frame.orderNumber}, phone=${frame.phoneLast4}")
        showOrderDialog(frame)
    }

    override fun onInfo(message: String) {
        Log.d("MainActivityScan", message)
        showToast(message)
    }

    override fun onDeviceFound(result: ScanResult) {
        val raw = result.scanRecord?.bytes?.joinToString(" ") { String.format("%02X", it) } ?: "N/A"
        Log.d("MainActivityScan", """
            ---- BLE Packet ----
            Device Name : ${result.device.name ?: "N/A"}
            MAC Address : ${result.device.address}
            RSSI        : ${result.rssi}
            Service UUIDs : ${result.scanRecord?.serviceUuids ?: "N/A"}
            Raw Bytes   : $raw
            --------------------
        """.trimIndent())
    }

    private fun showOrderDialog(frame: IBeaconParser.IBeaconFrame) {
        android.app.AlertDialog.Builder(this)
            .setTitle("주문 확인")
            .setMessage(
                "주문번호: ${frame.orderNumber}\n" +
                        "전화번호: ${frame.phoneLast4}\n" +
                        "Major: ${frame.major}\n" +
                        "Minor: ${frame.minor}"
            )
            .setPositiveButton("확인") { d, _ -> d.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
