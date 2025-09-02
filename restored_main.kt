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
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import android.view.Menu
import android.view.MenuItem
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mcandle.bleapp.databinding.ActivityMainBinding
import com.mcandle.bleapp.scan.BleScannerManager
import com.mcandle.bleapp.scan.IBeaconParser
import com.mcandle.bleapp.viewmodel.BleAdvertiseViewModel
import com.mcandle.bleapp.advertise.AdvertiserManager
import com.mcandle.bleapp.util.SettingsManager

class MainActivity : AppCompatActivity(), BleScannerManager.Listener {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: BleAdvertiseViewModel by viewModels()
    private lateinit var scannerManager: BleScannerManager
    private lateinit var advertiserManager: AdvertiserManager
    private lateinit var settingsManager: SettingsManager
    private var pendingPhone4: String? = null
    
    // 결제 완료 브로드캐스트 리시버
    private val paymentCompletedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.mcandle.bleapp.PAYMENT_COMPLETED") {
                Log.d("MainActivity", "결제 완료 브로드캐스트 수신")
                // 광고와 스캔 중지
                stopAdvertiseAndScan()
                showToast("결제가 완료되어 광고가 중지되었습니다.")
            }
        }
    }

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

        settingsManager = SettingsManager(this)
        // 설정에 따른 스캔 모드로 scannerManager 초기화
        val scanMode = settingsManager.getScanFilter()
        scannerManager = BleScannerManager(this, this, mode = scanMode)
        advertiserManager = AdvertiserManager(this, viewModel)

        setupButtons()
        observeViewModel()
        
        // 결제 완료 브로드캐스트 리시버 등록 (Android 13+ 호환)
        val filter = IntentFilter("com.mcandle.bleapp.PAYMENT_COMPLETED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(paymentCompletedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(paymentCompletedReceiver, filter)
        }
        Log.d("MainActivity", "결제 완료 브로드캐스트 리시버 등록 완료")
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
        // 매칭 성공 시 스캔과 광고 모두 중단
        Log.d("MainActivity", "매칭 성공! order=${frame.orderNumber}, phone=${frame.phoneLast4}")
        Log.d("MainActivity", "매칭 성공 - stopAdvertiseAndScan() 호출")
        stopAdvertiseAndScan()
        showOrderDialog(frame)
    }

    override fun onInfo(message: String) {
        Log.d("MainActivityScan", message)
        // 타임아웃이나 스캔 종료 시 광고와 스캔 모두 중지
        if (message.contains("주변에서 일치하는 신호를 찾지 못했습니다") || 
            message.contains("스캔 종료") || 
            message.contains("스캔 실패")) {
            Log.d("MainActivity", "타임아웃/실패 - 광고와 스캔 모두 중지")
            stopAdvertiseAndScan()  // 광고와 스캔 모두 중지
        }
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
        // 1단계: 결제 요청 도착 확인 팝업
        showPaymentNotificationDialog(frame)
    }
    
    private fun showPaymentNotificationDialog(frame: IBeaconParser.IBeaconFrame) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.payment_notification_dialog, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
            
        dialogView.findViewById<android.widget.Button>(R.id.btnConfirm).setOnClickListener {
            dialog.dismiss()
            // 2단계: 주문 확인 팝업 표시
            showOrderDetailDialog(frame)
        }
        
        // 다이얼로그 배경을 투명하게 설정
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
    
    private fun showOrderDetailDialog(frame: IBeaconParser.IBeaconFrame) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.payment_detail_dialog, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
            
        // 결제하기 버튼 클릭 이벤트
        dialogView.findViewById<android.widget.Button>(R.id.btnPay).setOnClickListener {
            dialog.dismiss()
            showToast("결제가 완료되었습니다!")
        }
        
        // 다이얼로그 배경을 투명하게 설정
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        
        // 다이얼로그 크기 조정 (화면의 90% 너비 사용)
        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.9).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun setupButtons() {
        // Advertise Start 버튼
        binding.btnStart.setOnClickListener {
            // 현재 입력된 데이터를 가져와서 패킷 적용
            val fragment = supportFragmentManager.findFragmentById(R.id.inputFormFragmentContainer) as? com.mcandle.bleapp.ui.InputFormFragment
            if (fragment != null) {
                val packetData = fragment.collectInputData()
                if (packetData != null) {
                    // ViewModel에 데이터 적용
                    viewModel.updateData(
                        packetData.cardNumber,
                        packetData.phoneLast4,
                        packetData.deviceName,
                        packetData.encoding,
                        packetData.advertiseMode
                    )
                    
                    // Advertise 시작
                    if (checkAdvertisePermissions()) {
                        advertiserManager.startAdvertise(packetData)
                        
                        // Scan도 동시에 시작
                        if (packetData.phoneLast4.isNotEmpty()) {
                            viewModel.setScanning(true) // 스캔 상태 설정
                            ensurePermissionsAndScan(packetData.phoneLast4)
                        }
                    } else {
                        requestAdvertisePermissions()
                    }
                } else {
                    showToast("입력 데이터를 확인해주세요")
                }
            }
        }
        
        // Advertise Stop 버튼
        binding.btnStop.setOnClickListener {
            stopAdvertiseAndScan()
        }
    }
    
    // 광고와 스캔을 모두 중지하는 공용 메서드
    fun stopAdvertiseAndScan() {
        Log.d("MainActivity", "stopAdvertiseAndScan() 시작")
        
        // 1. 실제 광고/스캔 중지
        advertiserManager.stopAdvertise()
        scannerManager.stopScan()
        Log.d("MainActivity", "advertiserManager.stopAdvertise() 및 scannerManager.stopScan() 호출 완료")
        
        // 2. ViewModel 상태 업데이트
        viewModel.setAdvertising(false)
        viewModel.setScanning(false)
        Log.d("MainActivity", "ViewModel 상태 업데이트 완료: advertising=false, scanning=false")
        
        // 3. UI 강제 업데이트
        updateButtonUI()
        Log.d("MainActivity", "updateButtonUI() 강제 호출 완료")
    }
    
    private fun observeViewModel() {
        // Advertise 상태 관찰
        viewModel.isAdvertising.observe(this) { advertising ->
            updateButtonUI()
        }
        
        // Scan 상태 관찰
        viewModel.isScanning.observe(this) { scanning ->
            updateButtonUI()
        }
    }
    
    private fun updateButtonUI() {
        val advertising = viewModel.isAdvertising.value ?: false
        val scanning = viewModel.isScanning.value ?: false
        
        Log.d("MainActivity", "updateButtonUI - advertising: $advertising, scanning: $scanning")
        
        when {
            advertising && scanning -> {
                binding.btnStart.text = "광고 중 (Scan 모드)"
                binding.btnStart.isEnabled = false
                binding.btnStop.isEnabled = true
                Log.d("MainActivity", "버튼 상태: 광고 중 (Scan 모드)")
            }
            advertising -> {
                binding.btnStart.text = "광고 중..."
                binding.btnStart.isEnabled = false
                binding.btnStop.isEnabled = true
                Log.d("MainActivity", "버튼 상태: 광고 중...")
            }
            else -> {
                binding.btnStart.text = "Advertise Start"
                binding.btnStart.isEnabled = true
                binding.btnStop.isEnabled = false
                Log.d("MainActivity", "버튼 상태: Advertise Start")
            }
        }
    }
    
    private fun checkAdvertisePermissions(): Boolean {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        return needed.isEmpty()
    }
    
    private fun requestAdvertisePermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 브로드캐스트 리시버 해제
        unregisterReceiver(paymentCompletedReceiver)
        Log.d("MainActivity", "브로드캐스트 리시버 해제 완료")
    }
}
