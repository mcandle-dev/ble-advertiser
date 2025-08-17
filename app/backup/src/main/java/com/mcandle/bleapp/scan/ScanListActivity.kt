package com.mcandle.bleapp.scan

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mcandle.bleapp.databinding.ActivityScanListBinding

class ScanListActivity : AppCompatActivity(), BleScannerManager.Listener {

    private lateinit var binding: ActivityScanListBinding
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
        binding = ActivityScanListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scannerManager = BleScannerManager(this, this)

        binding.btnScanListStart.setOnClickListener {
            val phone4 = binding.etPhoneLast4.text.toString()
            if (phone4.length != 4) {
                showToast("전화번호 마지막 4자리를 입력해주세요.")
                return@setOnClickListener
            }
            ensurePermissionsAndScan(phone4)
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
        binding.tvLog.text = "" // 스캔 시작할 때 로그 초기화
        appendLog("스캔을 시작합니다. (phone4=$phone4)")
    }

    // 📌 Listener 구현
    override fun onMatch(frame: IBeaconParser.IBeaconFrame) {
        appendLog("매칭 성공: 주문번호=${frame.orderNumber}, 전화번호=${frame.phoneLast4}")
        showOrderDialog(frame)
    }

    override fun onInfo(message: String) {
        appendLog(message)
    }

    override fun onDeviceFound(result: ScanResult) {
        val name = result.device.name ?: "N/A"
        val mac = result.device.address
        val rssi = result.rssi
        val rawBytes = result.scanRecord?.bytes?.joinToString(" ") { String.format("%02X", it) } ?: "N/A"

        val logMsg = """
            ---- BLE Packet ----
            Device Name : $name
            MAC Address : $mac
            RSSI        : $rssi
            Service UUIDs : ${result.scanRecord?.serviceUuids ?: "N/A"}
            Raw Bytes   : $rawBytes
            --------------------
        """.trimIndent()

        appendLog(logMsg)
    }

    private fun showOrderDialog(frame: IBeaconParser.IBeaconFrame) {
        AlertDialog.Builder(this)
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

    // 📌 공통 로그 함수 (화면 + Logcat 동시 출력)
    private fun appendLog(msg: String) {
        Log.d("ScanListActivity", msg)
        binding.tvLog.append(msg + "\n\n")

        // 스크롤 맨 아래로 이동
        binding.tvLog.post {
            val scrollView = binding.tvLog.parent as? android.widget.ScrollView
            scrollView?.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }
}
