package com.mcandle.bleapp.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.drawable.AnimationDrawable
import android.os.CountDownTimer
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.core.content.ContextCompat
import android.view.View
import android.view.ViewGroup
import com.mcandle.bleapp.databinding.FragmentCardBinding
import com.mcandle.bleapp.scan.BleScannerManager
import com.mcandle.bleapp.scan.IBeaconParser
import com.mcandle.bleapp.viewmodel.BleAdvertiseViewModel
import com.mcandle.bleapp.advertise.AdvertiserManager
import com.mcandle.bleapp.util.SettingsManager
import com.mcandle.bleapp.SettingsActivity
import com.mcandle.bleapp.R
import java.io.IOException

class CardFragment : Fragment(), BleScannerManager.Listener {

    private var _binding: FragmentCardBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: BleAdvertiseViewModel by viewModels()
    private lateinit var scannerManager: BleScannerManager
    private lateinit var advertiserManager: AdvertiserManager
    private lateinit var settingsManager: SettingsManager
    private var pendingPhone4: String? = null
    private var scanTimer: CountDownTimer? = null
    private var pulseAnimation: AnimationDrawable? = null
    
    // 결제 완료 브로드캐스트 리시버
    private val paymentCompletedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.mcandle.bleapp.PAYMENT_COMPLETED") {
                Log.d("CardFragment", "결제 완료 브로드캐스트 수신")
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        settingsManager = SettingsManager(requireContext())
        // 설정에 따른 스캔 모드로 scannerManager 초기화
        val scanMode = settingsManager.getScanFilter()
        scannerManager = BleScannerManager(requireContext(), this, mode = scanMode)
        advertiserManager = AdvertiserManager(requireContext(), viewModel)

        // Assets 폴더에서 이미지 로드
        loadImageFromAssets()
        
        setupButtons()
        setupSettingsButton()
        updateCardNumberDisplay()
        observeViewModel()
        
        // 🔥 카드 탭 진입 시 즉시 BLE 시작 (버튼 없이)
        startInitialBleProcess()
        
        // 결제 완료 브로드캐스트 리시버 등록 (Android 13+ 호환)
        val filter = IntentFilter("com.mcandle.bleapp.PAYMENT_COMPLETED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(paymentCompletedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(paymentCompletedReceiver, filter)
        }
        Log.d("CardFragment", "결제 완료 브로드캐스트 리시버 등록 완료")
    }
    
    override fun onResume() {
        super.onResume()
        // 설정에서 돌아왔을 때 카드번호 업데이트
        updateCardNumberDisplay()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            requireContext().unregisterReceiver(paymentCompletedReceiver)
        } catch (e: Exception) {
            Log.e("CardFragment", "브로드캐스트 리시버 해제 중 오류: ${e.message}")
        }
        scanTimer?.cancel()
        _binding = null
    }
    
    private fun loadImageFromAssets() {
        try {
            val inputStream = requireContext().assets.open("JasminBlack-463x463.png")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            
            // 카드를 세로로 회전시키기 위해 90도 회전
            val matrix = android.graphics.Matrix()
            matrix.postRotate(90f)
            val rotatedBitmap = android.graphics.Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            
            binding.ivCard.setImageBitmap(rotatedBitmap)
            inputStream.close()
            Log.d("CardFragment", "Assets에서 이미지 로드 및 세로 회전 성공")
        } catch (e: IOException) {
            Log.e("CardFragment", "Assets 이미지 로드 실패: ${e.message}")
            // 기본 drawable로 fallback
            binding.ivCard.setImageResource(R.drawable.jasmin_black_card_real)
        }
    }

    private fun ensurePermissionsAndScan(phone4: String) {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
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
        Log.d("CardFragment", "스캔 시작 (phone4=$phone4)")
    }

    // 🔹 Listener 구현
    override fun onMatch(frame: IBeaconParser.IBeaconFrame) {
        // 매칭 성공 시 스캔과 광고 모두 중단
        Log.d("CardFragment", "매칭 성공! order=${frame.orderNumber}, phone=${frame.phoneLast4}")
        Log.d("CardFragment", "매칭 성공 - stopAdvertiseAndScan() 호출")
        stopAdvertiseAndScan()
        // 🔥 매칭 성공 후 버튼 표시
        binding.btnToggle.visibility = View.VISIBLE
        binding.btnToggle.text = "결제 시작"
        showOrderDialog(frame)
    }

    override fun onInfo(message: String) {
        Log.d("CardFragmentScan", message)
        // 타임아웃이나 스캔 종료 시 광고와 스캔 모두 중지
        if (message.contains("주변에서 일치하는 신호를 찾지 못했습니다") || 
            message.contains("스캔 종료") || 
            message.contains("스캔 실패")) {
            Log.d("CardFragment", "타임아웃/실패 - 광고와 스캔 모두 중지")
            stopAdvertiseAndScan()  // 광고와 스캔 모두 중지
            // 🔥 스캔 실패/종료 후 버튼 표시
            binding.btnToggle.visibility = View.VISIBLE
            binding.btnToggle.text = "결제 시작"
        }
        showToast(message)
    }

    override fun onDeviceFound(result: ScanResult) {
        val raw = result.scanRecord?.bytes?.joinToString(" ") { String.format("%02X", it) } ?: "N/A"
        Log.d("CardFragmentScan", """
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
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.payment_notification_dialog, null)
        val dialog = AlertDialog.Builder(requireContext())
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
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.payment_detail_dialog, null)
        val dialog = AlertDialog.Builder(requireContext())
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

    // 🔥 카드 탭 진입 시 즉시 BLE 시작하는 함수
    private fun startInitialBleProcess() {
        val cardNumber = settingsManager.getCardNumber()
        val phone4 = settingsManager.getPhoneLast4()
        
        if (cardNumber.isEmpty() || phone4.isEmpty()) {
            showToast("설정에서 카드번호와 전화번호를 입력해주세요")
            // 설정이 없으면 버튼 표시
            binding.btnToggle.visibility = View.VISIBLE
            binding.btnToggle.text = "설정 확인 필요"
            return
        }
        
        // 버튼 숨기고 즉시 BLE 시작
        binding.btnToggle.visibility = View.GONE
        startAdvertiseAndScan(cardNumber, phone4)
        Log.d("CardFragment", "카드 탭 진입 시 자동 BLE 시작")
    }

    private fun setupButtons() {
        binding.btnToggle.setOnClickListener {
            val cardNumber = settingsManager.getCardNumber()
            val phone4 = settingsManager.getPhoneLast4()
            
            if (cardNumber.isEmpty() || phone4.isEmpty()) {
                showToast("설정에서 카드번호와 전화번호를 입력해주세요")
                return@setOnClickListener
            }
            
            // 버튼 클릭 시 버튼 숨기고 BLE 시작
            binding.btnToggle.visibility = View.GONE
            startAdvertiseAndScan(cardNumber, phone4)
            Log.d("CardFragment", "결제 시작 버튼 클릭 - BLE 재시작")
        }
    }
    
    private fun setupSettingsButton() {
        binding.btnSettings.setOnClickListener {
            val intent = Intent(requireContext(), SettingsActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun updateCardNumberDisplay() {
        val cardNumber = settingsManager.getCardNumber()
        if (cardNumber.isNotEmpty()) {
            // 카드번호를 4자리씩 나누어 표시
            val formattedNumber = cardNumber.chunked(4).joinToString(" ")
            binding.tvCardNumber.text = formattedNumber
        } else {
            binding.tvCardNumber.text = "**** **** **** ****"
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun startAdvertiseAndScan(cardNumber: String, phone4: String) {
        // ViewModel 업데이트 - 전체 파라미터 전달
        val deviceName = settingsManager.getDeviceName()
        val encoding = settingsManager.getEncodingType() 
        val advMode = settingsManager.getAdvertiseMode()
        viewModel.updateData(cardNumber, phone4, deviceName, encoding, advMode)
        viewModel.setAdvertising(true)
        viewModel.setScanning(true)
        
        // 광고 시작
        val currentData = viewModel.currentData.value
        if (currentData != null) {
            advertiserManager.startAdvertise(currentData)
        }
        
        // 스캔 시작
        ensurePermissionsAndScan(phone4)
        
        // 시각적 효과 시작
        startScanningEffects()
        
        Log.d("CardFragment", "광고 및 스캔 시작 - 카드: $cardNumber, 폰: $phone4")
    }
    
    private fun stopAdvertiseAndScan() {
        viewModel.setAdvertising(false)
        viewModel.setScanning(false)
        advertiserManager.stopAdvertise()
        scannerManager.stopScan()
        
        // 시각적 효과 중지
        stopScanningEffects()
        
        Log.d("CardFragment", "광고 및 스캔 중지")
    }
    
    private fun startScanningEffects() {
        // 파형 애니메이션 시작
        binding.ivPulseAnimation.visibility = View.VISIBLE
        pulseAnimation = binding.ivPulseAnimation.drawable as? AnimationDrawable
        pulseAnimation?.start()
        
        // 카운트다운 타이머 시작 (60초)
        binding.tvScanTimer.visibility = View.VISIBLE
        scanTimer?.cancel()
        scanTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.tvScanTimer.text = seconds.toString()
            }
            override fun onFinish() {
                binding.tvScanTimer.text = "0"
                stopScanningEffects()
                // 타임아웃 시 스캔과 광고 중지
                stopAdvertiseAndScan()
                // 🔥 카운트 종료 후 "결제 시작" 버튼 표시
                binding.btnToggle.visibility = View.VISIBLE
                binding.btnToggle.text = "결제 시작"
                Log.d("CardFragment", "카운트다운 종료 - 결제 시작 버튼 표시")
            }
        }.start()
    }
    
    private fun stopScanningEffects() {
        // 파형 애니메이션 중지
        pulseAnimation?.stop()
        binding.ivPulseAnimation.visibility = View.GONE
        
        // 카운트다운 타이머 중지
        scanTimer?.cancel()
        binding.tvScanTimer.visibility = View.GONE
    }
    
    private fun observeViewModel() {
        // ViewModel 상태 관찰은 유지하지만 버튼 상태는 수동으로 관리
        viewModel.isAdvertising.observe(viewLifecycleOwner) { advertising ->
            Log.d("CardFragment", "Advertising 상태: $advertising")
        }
        
        viewModel.isScanning.observe(viewLifecycleOwner) { scanning ->
            Log.d("CardFragment", "Scanning 상태: $scanning")
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}