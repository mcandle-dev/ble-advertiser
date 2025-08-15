package com.mcandle.bleapp

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.mcandle.bleapp.advertise.AdvertiserManager
import com.mcandle.bleapp.databinding.ActivityMainBinding
import com.mcandle.bleapp.scan.BleScannerManager
import com.mcandle.bleapp.scan.IBeaconParser
import com.mcandle.bleapp.ui.InputFormFragment
import com.mcandle.bleapp.viewmodel.BleAdvertiseViewModel
import kotlinx.coroutines.launch

private const val TAG_MAIN = "MainActivityScan"

class MainActivity : AppCompatActivity(), BleScannerManager.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: BleAdvertiseViewModel

    // 광고 / 스캔 매니저
    private lateinit var advertiserManager: AdvertiserManager
    private lateinit var scanner: BleScannerManager

    // 광고가 켜진 직후 자동 스캔을 1회만 트리거하기 위한 가드
    private var autoScanAfterAdvTriggered = false

    // 버튼 클릭으로 스캔 요청 후, 권한 허용 시점에 사용할 임시 보관
    private var pendingPhone4: String? = null

    // 권한 런처
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.entries.any { !it.value }
        Log.d(TAG_MAIN, "permission result = $results (denied=$denied)")
        if (denied) {
            viewModel.onScanError("필수 권한이 거부되었습니다.")
            pendingPhone4 = null
            return@registerForActivityResult
        }
        pendingPhone4?.let { phone4 ->
            Log.d(TAG_MAIN, "permission OK -> startScan($phone4)")
            startScan(phone4)
        }
        pendingPhone4 = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[BleAdvertiseViewModel::class.java]

        // 광고 매니저
        advertiserManager = AdvertiserManager(this, viewModel)

        // 스캐너 (minor=3454 필터; 필요 없으면 null)
        scanner = BleScannerManager(
            context = this,
            listener = this,
            expectedMinor = 3454,
            scanTimeoutMs = 15_000L
        )
        Log.d(TAG_MAIN, "scanner configured: expectedMinor=3454, timeout=15000ms")

        // 입력 프래그먼트 (전화 4자리 + 스캔 시작 버튼)
        supportFragmentManager.beginTransaction()
            .replace(R.id.inputFormFragmentContainer, InputFormFragment())
            .commit()

        // ───────── 광고 버튼/상태 ─────────
        viewModel.isAdvertising.observe(this) { isAdv: Boolean ->
            binding.btnStart.isEnabled = !isAdv
            binding.btnStop.isEnabled = isAdv
            binding.btnStart.text = if (isAdv) "적용중..." else "Advertise Start"
            Log.d(TAG_MAIN, "isAdvertising changed: $isAdv")

            // ✅ 광고가 막 켜졌다면 → 자동 스캔 1회 트리거
            if (isAdv && !autoScanAfterAdvTriggered && !scanner.isScanning) {
                val phone4 = viewModel.inputPhoneLast4.value.orEmpty()
                Log.d(TAG_MAIN, "auto-scan trigger check: phone4='$phone4'")
                if (phone4.length == 4 && phone4.all { it.isDigit() }) {
                    autoScanAfterAdvTriggered = true      // 중복 트리거 방지
                    pendingPhone4 = phone4                // 버튼 흐름과 동일한 권한/시작 경로 사용
                    Log.d(TAG_MAIN, "auto-scan pending with phone4=$phone4 -> ensureScanPermissions()")
                    ensureScanPermissions()               // 권한 있으면 startScan(phone4)
                } else {
                    showToast("전화번호 마지막 4자리를 먼저 입력하세요.")
                }
            }

            // 광고가 꺼졌다면, 다음에 다시 켜질 때를 대비해 가드 리셋
            if (!isAdv) {
                autoScanAfterAdvTriggered = false
            }
        }

        binding.btnStart.setOnClickListener {
            val data = viewModel.currentData.value
            if (data == null) {
                showToast("패킷 데이터를 먼저 입력해주세요.")
                return@setOnClickListener
            }
            if (!advertiserManager.isSupported()) {
                showToast("BLE Advertise를 지원하지 않는 기기입니다.")
                return@setOnClickListener
            }
            try {
                Log.d(TAG_MAIN, "Advertise start requested with data=$data")
                advertiserManager.startAdvertise(data)
            } catch (se: SecurityException) {
                showToast("광고 권한이 필요합니다. (Android 12+: BLUETOOTH_ADVERTISE)")
            }
        }

        binding.btnStop.setOnClickListener {
            Log.d(TAG_MAIN, "Advertise stop requested")
            advertiserManager.stopAdvertise()
        }
        // ────────────────────────────────────────────────────────────────

        // 프래그먼트의 '스캔 시작' 버튼 → ViewModel 이벤트 → 여기서만 권한/스캔
        observeStartScanRequest()

        // (옵션) 초기 권한 프롬프트
        precheckPermissions()
    }

    // ViewModel의 스캔 시작 원샷 이벤트
    private fun observeStartScanRequest() {
        lifecycleScope.launch {
            viewModel.startScanRequest.collect { phone4 ->
                Log.d(TAG_MAIN, "startScanRequest from ViewModel: phone4=$phone4")
                pendingPhone4 = phone4
                ensureScanPermissions()
            }
        }
    }

    // 스캔 권한 체크/요청
    private fun ensureScanPermissions() {
        val need = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

        if (need.isNotEmpty()) {
            Log.d(TAG_MAIN, "requesting permissions: $need")
            permissionLauncher.launch(need.toTypedArray())
        } else {
            pendingPhone4?.let { phone4 ->
                Log.d(TAG_MAIN, "permission already granted -> startScan($phone4)")
                startScan(phone4)
                pendingPhone4 = null
            }
        }
    }

    // (선택) 초기 권한 프롬프트 (광고+스캔 한 번에)
    private fun precheckPermissions() {
        val all = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            all += listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            all += Manifest.permission.ACCESS_FINE_LOCATION
        }
        val need = all.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) {
            Log.d(TAG_MAIN, "precheck requesting: $need")
            permissionLauncher.launch(need.toTypedArray())
        }
    }

    // 실제 스캔 시작: 버튼 → 권한 OK 이후에만 호출
    private fun startScan(phoneLast4: String) {
        if (viewModel.isScanning.value == true || scanner.isScanning) {
            showToast("이미 스캔 중입니다.")
            Log.d(TAG_MAIN, "startScan ignored - already scanning (vm=${viewModel.isScanning.value}, scanner=${scanner.isScanning})")
            return
        }
        Log.d(TAG_MAIN, "startScan(phoneLast4=$phoneLast4)")
        scanner.startScan(phoneLast4)
        viewModel.setScanning(true)
    }

    private fun stopScan() {
        Log.d(TAG_MAIN, "MainActivity.stopScan() called", Throwable("trace: MainActivity.stopScan"))
        if (!scanner.isScanning) return
        scanner.stopScan()
        viewModel.setScanning(false)
    }

    // ───────── BleScannerManager.Listener ─────────

    override fun onMatch(frame: IBeaconParser.IBeaconFrame) {
        Log.d(
            TAG_MAIN,
            "onMatch: order=${frame.orderNumber}, phone4=${frame.phoneLast4}, major=${frame.major}, minor=${frame.minor}"
        )
        viewModel.setScanning(false)
        showOrderDialog(frame)
    }

    override fun onInfo(message: String) {
        Log.d(TAG_MAIN, "scanner info: $message")
        showToast(message)
    }

    // ───────── 다이얼로그 표시 (업로드된 레이아웃의 실제 ID만 사용) ─────────
    private fun showOrderDialog(frame: IBeaconParser.IBeaconFrame) {
        val view = LayoutInflater.from(this).inflate(R.layout.order_detail_dialog, null, false)

        // order_detail_dialog.xml 실제 ID 목록을 기준으로 세팅
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvStoreName = view.findViewById<TextView>(R.id.tvStoreName)
        val tvPosId = view.findViewById<TextView>(R.id.tvPosId)
        val tvStaffName = view.findViewById<TextView>(R.id.tvStaffName)
        val tvAmount = view.findViewById<TextView>(R.id.tvAmount)
        val tvPromotions = view.findViewById<TextView>(R.id.tvPromotions)
        val tvRecommended = view.findViewById<TextView>(R.id.tvRecommended)
        val tvPayAmount = view.findViewById<TextView>(R.id.tvPayAmount)

        tvTitle?.text = "주문 확인"
        // 의미상 재활용: 매장/POS/직원 필드에 스캔에서 얻은 핵심 값 표시
        tvStoreName?.text = "주문번호: ${frame.orderNumber}"
        tvPosId?.text = "전화 4자리: ${frame.phoneLast4}"
        tvStaffName?.text = "신호: major=${frame.major}, minor=${frame.minor}"

        // 아래 항목들은 필요하면 적절한 값으로 교체하세요(지금은 예시/더미)
        tvAmount?.text = "금액: -"
        tvPromotions?.text = ""
        tvRecommended?.text = ""
        tvPayAmount?.text = "[결제금액] -"

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        // 하단 버튼: 실제 레이아웃에 존재하는 ID만 사용 (btnCancel, btnPay)
        view.findViewById<Button>(R.id.btnCancel)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnPay)?.setOnClickListener {
            // TODO: 결제/확정 로직이 있다면 여기에…
            dialog.dismiss()
        }

        dialog.show()
    }

    // ───────── 생명주기 정리 ─────────
    override fun onStop() {
        Log.d(TAG_MAIN, "onStop() -> stopScan()")
        super.onStop()
        if (scanner.isScanning) stopScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        advertiserManager.stopAdvertise()
        if (scanner.isScanning) {
            Log.d(TAG_MAIN, "onDestroy() -> stopScan()")
            stopScan()
        }
    }

    // ───────── 유틸 ─────────
    private val mainHandler = Handler(Looper.getMainLooper())
    private fun showToast(msg: CharSequence) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        } else {
            mainHandler.post {
                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
