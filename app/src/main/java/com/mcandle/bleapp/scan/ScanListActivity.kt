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
import android.view.LayoutInflater
import android.content.Intent
import android.app.Activity
import com.mcandle.bleapp.databinding.ActivityScanListBinding
import com.mcandle.bleapp.util.SettingsManager
import com.mcandle.bleapp.R

class ScanListActivity : AppCompatActivity(), BleScannerManager.Listener {

    private lateinit var binding: ActivityScanListBinding
    private lateinit var scannerManager: BleScannerManager
    private lateinit var settingsManager: SettingsManager
    private var pendingPhone4: String? = null
    private var isScanning = false
    private val deviceMap = mutableMapOf<String, ScanResult>() // MAC 주소를 키로 하는 맵
    private var phoneLast4: String = ""

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

        settingsManager = SettingsManager(this)
        
        // 설정에서 선택한 스캔 필터 모드를 적용
        val scanMode = settingsManager.getScanFilter()
        scannerManager = BleScannerManager(this, this, mode = scanMode)
        
        // Intent에서 전화번호 받기
        phoneLast4 = intent.getStringExtra("PHONE_LAST4") ?: ""
        if (phoneLast4.isEmpty()) {
            showToast("전화번호 정보가 없습니다.")
            finish()
            return
        }
        
        // 전화번호 표시 업데이트
        binding.tvPhoneDisplay.text = "전화번호: $phoneLast4"
        
        // 현재 설정된 스캔 모드를 로그에 출력
        appendLog("스캔 필터 모드: $scanMode")
        appendLog("대상 전화번호: $phoneLast4")

        binding.btnScanListStart.setOnClickListener {
            if (!isScanning) {
                // 스캔 시작 (메인에서 받은 전화번호 사용)
                ensurePermissionsAndScan(phoneLast4)
            } else {
                // 스캔 중지
                stopScan()
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
        isScanning = true
        updateScanButtonUI()
        binding.tvLog.text = "" // 스캔 시작할 때 로그 초기화
        deviceMap.clear() // 스캔 시작할 때 디바이스 맵 초기화
        appendLog("스캔을 시작합니다. (대상: $phone4)")
    }

    private fun stopScan() {
        scannerManager.stopScan()
        isScanning = false
        updateScanButtonUI()
        appendLog("스캔을 중지했습니다.")
    }

    private fun updateScanButtonUI() {
        if (isScanning) {
            binding.btnScanListStart.text = "스캔 중지"
            binding.btnScanListStart.contentDescription = "스캔 중지 버튼"
        } else {
            binding.btnScanListStart.text = "스캔 시작"
            binding.btnScanListStart.contentDescription = "스캔 시작 버튼"
        }
    }

    // 📌 Listener 구현
    override fun onMatch(frame: IBeaconParser.IBeaconFrame) {
        Log.d("ScanListActivity", "onMatch() 호출됨 - 매칭 성공!")
        appendLog("🎯 매칭 성공: 주문번호=${frame.orderNumber}, 전화번호=${frame.phoneLast4}")
        // 매칭 시 자동으로 스캔 중지
        scannerManager.stopScan() // 실제 스캔 중지
        isScanning = false
        updateScanButtonUI()
        Log.d("ScanListActivity", "스캔 중지됨, 결제 팝업 표시 시작")
        showOrderDialog(frame)
    }

    override fun onInfo(message: String) {
        appendLog(message)
        // 스캔이 중지되었다면 UI 업데이트
        if (message.contains("스캔 종료") || message.contains("스캔 실패")) {
            isScanning = false
            updateScanButtonUI()
        }
    }

    override fun onDeviceFound(result: ScanResult) {
        val name = result.device.name ?: "N/A"
        val mac = result.device.address
        val rssi = result.rssi
        val rawBytes = result.scanRecord?.bytes?.joinToString(" ") { String.format("%02X", it) } ?: "N/A"

        // MAC 주소가 이미 존재하는지 확인
        val isNew = !deviceMap.containsKey(mac)
        val previousRssi = deviceMap[mac]?.rssi
        
        // 디바이스 맵 업데이트
        deviceMap[mac] = result

        if (isNew) {
            // iBeacon 정보 파싱 시도
            val beaconFrame = IBeaconParser.parseFrom(result)
            val beaconInfo = if (beaconFrame != null) {
                val companyName = when (beaconFrame.companyId) {
                    0x5246 -> "RFStar"
                    0x004C -> "Apple"
                    0x0059 -> "Nordic"
                    else -> "Unknown (0x${String.format("%04X", beaconFrame.companyId)})"
                }
                
                """
                🔵 iBeacon ScanMatchInfo:
                Company     : $companyName
                UUID        : ${beaconFrame.uuid}
                Order       : ${beaconFrame.orderNumber}
                Phone       : ${beaconFrame.phoneLast4}
                Major       : ${beaconFrame.major}
                Minor       : ${beaconFrame.minor}
                TX Power    : ${beaconFrame.txPower} dBm
                """.trimIndent()
            } else {
                "🔘 Not iBeacon format"
            }

            // 새로운 디바이스만 전체 로그 출력
            val logMsg = """
                [NEW] BLE Packet ----
                Device Name : $name
                MAC Address : $mac
                RSSI        : $rssi
                Service UUIDs : ${result.scanRecord?.serviceUuids ?: "N/A"}
                $beaconInfo
                Raw Bytes   : $rawBytes
                --------------------
            """.trimIndent()
            appendLog(logMsg)
        } else {
            // 기존 디바이스의 RSSI가 변경된 경우에만 텍스트 업데이트
            if (previousRssi != null && previousRssi != rssi) {
                updateRssiInLog(mac, previousRssi, rssi)
            }
            // RSSI가 같으면 아무것도 하지 않음 (현행 유지)
        }
    }

    private fun updateRssiInLog(mac: String, oldRssi: Int, newRssi: Int) {
        val currentText = binding.tvLog.text.toString()
        
        // MAC 주소를 포함하는 [NEW] 블록을 찾아서 RSSI 값만 업데이트
        val pattern = "\\[NEW\\] BLE Packet ----[\\s\\S]*?MAC Address : $mac[\\s\\S]*?RSSI        : $oldRssi[\\s\\S]*?--------------------".toRegex()
        
        val updatedText = pattern.replace(currentText) { matchResult ->
            matchResult.value.replace("RSSI        : $oldRssi", "RSSI        : $newRssi")
        }
        
        // 변경사항이 있으면 텍스트 업데이트
        if (updatedText != currentText) {
            binding.tvLog.text = updatedText
        }
    }

    private fun showOrderDialog(frame: IBeaconParser.IBeaconFrame) {
        // 2단계 결제 팝업 시스템: 1단계 결제 알림 팝업
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
            // 2단계: 결제 상세 정보 팝업 표시
            showPaymentDetailDialog(frame)
        }
        
        // 다이얼로그 배경을 투명하게 설정
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
    
    private fun showPaymentDetailDialog(frame: IBeaconParser.IBeaconFrame) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.payment_detail_dialog, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
            
        // 결제하기 버튼 클릭 이벤트
        dialogView.findViewById<android.widget.Button>(R.id.btnPay).setOnClickListener {
            dialog.dismiss()
            showToast("결제가 완료되었습니다!")
            Log.d("ScanListActivity", "결제 완료 - 직접 브로드캐스트 발송")
            
            // 직접 브로드캐스트로 MainActivity에 알림
            val broadcastIntent = Intent("com.mcandle.bleapp.PAYMENT_COMPLETED")
            sendBroadcast(broadcastIntent)
            
            Log.d("ScanListActivity", "브로드캐스트 발송 완료, finish() 호출")
            finish() // ScanListActivity 종료
        }
        
        // 다이얼로그 배경을 투명하게 설정
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        
        // 다이얼로그 크기 조정 (화면의 90% 너비 사용)
        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.9).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
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
