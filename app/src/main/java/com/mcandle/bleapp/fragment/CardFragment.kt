package com.mcandle.bleapp.fragment

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.drawable.AnimationDrawable
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import android.view.View
import android.view.ViewGroup
import com.mcandle.bleapp.databinding.FragmentCardBinding
import com.mcandle.bleapp.viewmodel.BleAdvertiseViewModel
import com.mcandle.bleapp.advertise.AdvertiserManager
import com.mcandle.bleapp.gatt.GattServerManager
import com.mcandle.bleapp.util.SettingsManager
import com.mcandle.bleapp.SettingsActivity
import com.mcandle.bleapp.R
import java.io.IOException

class CardFragment : Fragment(), GattServerManager.GattServerCallback {

    private var _binding: FragmentCardBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: BleAdvertiseViewModel by viewModels()
    private lateinit var gattServerManager: GattServerManager
    private lateinit var advertiserManager: AdvertiserManager
    private lateinit var settingsManager: SettingsManager
    private var scanTimer: CountDownTimer? = null
    private var connectedTimer: CountDownTimer? = null
    private var isConnected: Boolean = false
    private var pulseAnimation: AnimationDrawable? = null

    // 결제 완료 브로드캐스트 리시버
    private val paymentCompletedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.mcandle.bleapp.PAYMENT_COMPLETED") {
                Log.d("CardFragment", "결제 완료 브로드캐스트 수신")
                stopAdvertiseAndGatt()
                showToast("결제가 완료되어 광고가 중지되었습니다.")
            }
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
        gattServerManager = GattServerManager(requireContext(), this)
        advertiserManager = AdvertiserManager(requireContext(), viewModel)

        // Assets 폴더에서 이미지 로드
        loadImageFromAssets()
        
        setupButtons()
        setupSettingsButton()
        updateCardNumberDisplay()
        observeViewModel()

        // 🔥 카드 탭 진입 시 즉시 BLE Advertise + GATT Server 시작
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
        connectedTimer?.cancel()

        // 🔥 Fragment 파괴 시 반드시 advertise/GATT 중지
        stopAdvertiseAndGatt()
        Log.d("CardFragment", "onDestroy - advertise/GATT 중지 완료")

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

    // GATT Server Callbacks
    override fun onGattServerReady(success: Boolean, errorMessage: String?) {
        requireActivity().runOnUiThread {
            if (success) {
                Log.d("CardFragment", "✅ GATT Server 준비 완료 - 이제 Advertise 시작")

                // GATT Server가 준비되었으므로 이제 안전하게 Advertise 시작
                val currentData = viewModel.currentData.value
                if (currentData != null) {
                    advertiserManager.startAdvertise(currentData)
                    Log.d("CardFragment", "BLE Advertise 시작 완료")

                    // 시각적 효과 시작
                    startWaitingEffects()
                } else {
                    Log.e("CardFragment", "ViewModel 데이터가 null입니다")
                    showToast("데이터 오류가 발생했습니다")
                    stopAdvertiseAndGatt()
                    binding.btnToggle.visibility = View.VISIBLE
                    binding.btnToggle.text = "결제 시작"
                }
            } else {
                val errorMsg = errorMessage ?: "GATT Server 초기화 실패 (원인 불명)"
                Log.e("CardFragment", "❌ GATT Server 시작 실패: $errorMsg")
                showToast("오류: $errorMsg")
                stopAdvertiseAndGatt()
                binding.btnToggle.visibility = View.VISIBLE
                binding.btnToggle.text = "결제 시작"
            }
        }
    }

    override fun onConnectCommandReceived(device: BluetoothDevice) {
        Log.d("CardFragment", "AT+CONNECT command received from: ${device.address}")
        requireActivity().runOnUiThread {
            // 초기 타이머 취소
            scanTimer?.cancel()

            // 연결 상태로 전환
            isConnected = true
            binding.tvScanTimer.text = "Connect"

            // 연결 후 60초 타이머 시작
            startConnectedTimer()

            showToast("결제 단말기 연결됨")
            Log.d("CardFragment", "AT+CONNECT - 초기 타이머 취소, 연결 타이머 시작")
        }
    }

    override fun onDisconnectCommandReceived(device: BluetoothDevice) {
        Log.d("CardFragment", "AT+DISCONNECT command received from: ${device.address}")
        requireActivity().runOnUiThread {
            // 모든 타이머 취소
            scanTimer?.cancel()
            connectedTimer?.cancel()

            // "Disconnect" 표시
            binding.tvScanTimer.text = "Disconnect"
            isConnected = false

            Log.d("CardFragment", "AT+DISCONNECT - Disconnect 표시, 1초 후 정리")

            // 1초 후 UI 정리 및 버튼 표시
            Handler(Looper.getMainLooper()).postDelayed({
                stopWaitingEffects()
                stopAdvertiseAndGatt()

                // 버튼 표시
                binding.btnToggle.visibility = View.VISIBLE
                binding.btnToggle.text = "결제 시작"

                showToast("연결이 해제되었습니다")
                Log.d("CardFragment", "AT+DISCONNECT - 정리 완료, 결제 시작 버튼 표시")
            }, 1000)  // 1초 delay
        }
    }

    override fun onOrderReceived(orderId: String, additionalData: Map<String, String>?) {
        requireActivity().runOnUiThread {
            Log.d("CardFragment", "Order received! raw=$orderId, additionalData=$additionalData")

            // 1. 접두사 제거 (order_id= or ORDER_ID=)
            var cleanOrderId = orderId.trim()
            if (cleanOrderId.startsWith("order_id=", ignoreCase = true)) {
                cleanOrderId = cleanOrderId.substring("order_id=".length).trim()
            }

            Log.d("CardFragment", "Cleaned orderId: $cleanOrderId")

            // 2. finish 명령어 확인
            if (cleanOrderId.equals("finish", ignoreCase = true)) {
                handlePaymentFinish()
                return@runOnUiThread
            }

            // 3. 일반 주문 처리
            // 🔥 모든 타이머 취소
            scanTimer?.cancel()
            connectedTimer?.cancel()
            isConnected = false

            stopAdvertiseAndGatt()

            // 주문 수신 시 상세 화면으로 전환
            showPaymentDetail(cleanOrderId)
        }
    }

    private fun showPaymentDetail(orderId: String) {
        Log.d("CardFragment", "Switching to Detail view for order: $orderId")
        
        // UI 상태 전환
        binding.layoutInitial.visibility = View.GONE
        binding.layoutPaymentFinish.visibility = View.GONE
        binding.layoutPaymentMethods.visibility = View.GONE
        binding.layoutPaymentDetail.visibility = View.VISIBLE
        
        // 설정 버튼 숨기기
        binding.btnSettings.visibility = View.GONE
        
        // 주문번호 업데이트
        binding.tvDetailOrderNumber.text = "주문번호: $orderId"
        
        // 카카오페이 결제하기 버튼 리스너
        binding.btnDoPay.setOnClickListener {
            handlePaymentFinish()
        }

        // 다른 결제수단 결제하기 버튼 리스너
        binding.btnOtherPay.setOnClickListener {
            showPaymentMethods()
        }

        // 취소하기 버튼 리스너
        binding.btnCancelPay.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("결제 취소")
                .setMessage("취소하시면 POS에서 요청온 결재 요청이 취소됩니다.\n취소후에는 POS기에서 결제 재요청하여야 합니다.")
                .setPositiveButton("확인") { _, _ ->
                    resetToInitialManualState()
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun resetToInitialManualState() {
        Log.d("CardFragment", "Resetting UI to initial manual state (cancellation)")
        
        // 1. 모든 결제 관련 화면 숨기기
        binding.layoutPaymentDetail.visibility = View.GONE
        binding.layoutPaymentMethods.visibility = View.GONE
        binding.layoutPaymentFinish.visibility = View.GONE
        
        // 2. 초기 화면 표시 및 수동 시작 버튼 노출
        binding.layoutInitial.visibility = View.VISIBLE
        binding.btnSettings.visibility = View.VISIBLE
        
        // 🔥 중요한 점: 광고는 시작하지 않고 "결제 시작" 버튼이 보이는 상태가 되어야 함
        stopAdvertiseAndGatt()
        binding.btnToggle.visibility = View.VISIBLE
        binding.btnToggle.text = "결제 시작"
    }
    private fun showPaymentMethods() {
        Log.d("CardFragment", "Switching to Payment Methods selection view")
        
        binding.layoutPaymentDetail.visibility = View.GONE
        binding.layoutPaymentMethods.visibility = View.VISIBLE
        
        // 예시 버튼들 (액션 없음)
        binding.btnPaySimple.setOnClickListener { showToast("카드 간편 결제를 선택했습니다 (예시)") }
        binding.btnPayNormal.setOnClickListener { showToast("카드 일반 결제를 선택했습니다 (예시)") }
        binding.btnPaySamsung.setOnClickListener { showToast("SAMSUNG Pay를 선택했습니다 (예시)") }
        
        // 뒤로가기 버튼
        binding.btnBackToDetail.setOnClickListener {
            binding.layoutPaymentMethods.visibility = View.GONE
            binding.layoutPaymentDetail.visibility = View.VISIBLE
        }
    }

    private fun handlePaymentFinish() {
        Log.d("CardFragment", "Payment finish received - switching UI to finish state")
        
        // 1. 모든 타이머 및 BLE 중지
        scanTimer?.cancel()
        connectedTimer?.cancel()
        isConnected = false
        stopAdvertiseAndGatt()

        // 2. 다른 레이아웃 숨기기
        binding.layoutInitial.visibility = View.GONE
        binding.layoutPaymentDetail.visibility = View.GONE
        binding.layoutPaymentMethods.visibility = View.GONE
        
        // 설정 버튼 숨기기
        binding.btnSettings.visibility = View.GONE

        // 3. 결제 완료 전용 레이아웃 표시
        binding.layoutPaymentFinish.visibility = View.VISIBLE
    }

    override fun onClientConnected(device: BluetoothDevice) {
        Log.d("CardFragment", "GATT client connected: ${device.address}")
        // 물리적 연결만 로그, AT+CONNECT 명령어를 기다림
    }

    override fun onClientDisconnected(device: BluetoothDevice) {
        Log.d("CardFragment", "GATT client disconnected: ${device.address}")
        requireActivity().runOnUiThread {
            if (isConnected) {
                Log.d("CardFragment", "연결 해제 감지 - 즉시 종료")
                isConnected = false

                // 연결 타이머 취소
                connectedTimer?.cancel()

                // 광고/GATT 중지 및 UI 초기화
                stopWaitingEffects()
                stopAdvertiseAndGatt()

                // 버튼 표시
                binding.btnToggle.visibility = View.VISIBLE
                binding.btnToggle.text = "결제 시작"

                showToast("연결이 해제되어 종료되었습니다")
            }
        }
    }



    // 🔥 카드 탭 진입 시 즉시 BLE Advertise + GATT Server 시작
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
        startAdvertiseAndGatt(cardNumber, phone4)
        Log.d("CardFragment", "카드 탭 진입 시 자동 BLE Advertise + GATT Server 시작")
    }

    private fun setupButtons() {
        binding.btnToggle.setOnClickListener {
            val cardNumber = settingsManager.getCardNumber()
            val phone4 = settingsManager.getPhoneLast4()

            if (cardNumber.isEmpty() || phone4.isEmpty()) {
                showToast("설정에서 카드번호와 전화번호를 입력해주세요")
                return@setOnClickListener
            }

            // 버튼 클릭 시 버튼 숨기고 BLE Advertise + GATT Server 시작
            binding.btnToggle.visibility = View.GONE
            startAdvertiseAndGatt(cardNumber, phone4)
            Log.d("CardFragment", "결제 시작 버튼 클릭 - BLE Advertise + GATT Server 재시작")
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
    private fun startAdvertiseAndGatt(cardNumber: String, phone4: String) {
        // 🔥 2. 잠깐 대기 (이전 advertise/GATT 완전 종료 대기)
        Handler(Looper.getMainLooper()).postDelayed({
            // UI 초기화 (상세/완료 화면 숨기고 초기화면 표시)
            binding.layoutPaymentDetail.visibility = View.GONE
            binding.layoutPaymentMethods.visibility = View.GONE
            binding.layoutPaymentFinish.visibility = View.GONE
            binding.layoutInitial.visibility = View.VISIBLE
            binding.btnSettings.visibility = View.VISIBLE
            
            // ViewModel 업데이트 - 전체 파라미터 전달
            val deviceName = settingsManager.getDeviceName()
            val encoding = settingsManager.getEncodingType()
            val advMode = settingsManager.getAdvertiseMode()
            viewModel.updateData(cardNumber, phone4, deviceName, encoding, advMode)
            viewModel.setAdvertising(true)

            // 🔥 3. GATT Server를 먼저 시작 (비동기)
            // onServiceAdded 콜백에서 Service 등록 완료를 확인한 후
            // onGattServerReady()에서 Advertise 시작
            Log.d("CardFragment", "🚀 GATT Server 시작 (Service 등록 대기 중...)")
            gattServerManager.startGattServer()

            // ⚠️ Advertise는 onGattServerReady() 콜백에서 시작됨
            // 이렇게 하면 Race Condition 방지 (Android 15 요구사항)

            Log.d("CardFragment", "GATT Server 시작 요청 완료 - 카드: $cardNumber, 폰: $phone4")
        }, 100) // 100ms delay
    }

    private fun stopAdvertiseAndGatt() {
        viewModel.setAdvertising(false)
        advertiserManager.stopAdvertise()
        gattServerManager.stopGattServer()

        // 시각적 효과 중지
        stopWaitingEffects()

        // 연결 상태 초기화
        isConnected = false
        connectedTimer?.cancel()

        Log.d("CardFragment", "광고 및 GATT Server 중지")
    }
    
    private fun startWaitingEffects() {
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
                stopWaitingEffects()
                // 타임아웃 시 광고와 GATT Server 중지
                stopAdvertiseAndGatt()
                // 🔥 카운트 종료 후 "결제 시작" 버튼 표시
                binding.btnToggle.visibility = View.VISIBLE
                binding.btnToggle.text = "결제 시작"
                Log.d("CardFragment", "카운트다운 종료 - 결제 시작 버튼 표시")
            }
        }.start()
    }

    private fun startConnectedTimer() {
        // 기존 연결 타이머가 있으면 취소
        connectedTimer?.cancel()

        connectedTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // 카운트다운 표시 없이 "연결됨" 유지
                // (필요시 로그만 기록)
                if (millisUntilFinished % 10000 == 0L) {
                    Log.d("CardFragment", "연결 타이머 남은 시간: ${millisUntilFinished / 1000}초")
                }
            }

            override fun onFinish() {
                Log.d("CardFragment", "연결 타이머 종료 - 60초 내 주문 데이터 미수신")
                requireActivity().runOnUiThread {
                    binding.tvScanTimer.text = "0"
                    isConnected = false
                    stopWaitingEffects()
                    stopAdvertiseAndGatt()

                    // 버튼 표시
                    binding.btnToggle.visibility = View.VISIBLE
                    binding.btnToggle.text = "결제 시작"

                    showToast("타임아웃으로 종료되었습니다")
                }
            }
        }.start()

        Log.d("CardFragment", "연결 후 60초 타이머 시작")
    }

    private fun stopWaitingEffects() {
        // 파형 애니메이션 중지
        pulseAnimation?.stop()
        binding.ivPulseAnimation.visibility = View.GONE

        // 카운트다운 타이머 중지
        scanTimer?.cancel()
        binding.tvScanTimer.visibility = View.GONE
    }
    
    private fun observeViewModel() {
        // ViewModel 상태 관찰
        viewModel.isAdvertising.observe(viewLifecycleOwner) { advertising ->
            Log.d("CardFragment", "Advertising 상태: $advertising")
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}