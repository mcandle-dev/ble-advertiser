package com.mcandle.bleapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.mcandle.bleapp.databinding.FragmentInputFormBinding
import com.mcandle.bleapp.databinding.RawPacketDialogBinding
import com.mcandle.bleapp.model.AdvertiseMode
import com.mcandle.bleapp.model.EncodingType
import com.mcandle.bleapp.viewmodel.BleAdvertiseViewModel
import com.mcandle.bleapp.advertise.AdvertisePacketBuilder
import com.mcandle.bleapp.scan.ScanListActivity
import com.mcandle.bleapp.util.SettingsManager
import kotlinx.coroutines.launch

class InputFormFragment : Fragment() {
    private var _binding: FragmentInputFormBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BleAdvertiseViewModel by activityViewModels()
    private lateinit var settingsManager: SettingsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInputFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        settingsManager = SettingsManager(requireContext())

        // 전화번호 4자리 입력 (자동 ViewModel 저장 제거)
        // binding.etPhoneLast4.doOnTextChanged { text, _, _, _ ->
        //     viewModel.setPhoneLast4(text?.toString().orEmpty())
        // }

        // [수정] 스캔 시작 버튼
        binding.btnStart?.setOnClickListener {
            val phone4 = binding.etPhoneLast4.text?.toString()?.trim()
            if (phone4.isNullOrEmpty() || phone4.length != 4 || !phone4.all { it.isDigit() }) {
                toast("전화번호 4자리를 입력하세요.")
                return@setOnClickListener
            }

            // ViewModel에도 값 반영
            viewModel.setPhoneLast4(phone4)

            // ScanListActivity 실행
            val intent = Intent(requireContext(), ScanListActivity::class.java).apply {
                putExtra("PHONE_LAST4", phone4)
            }
            startActivity(intent)
        }


        // RAW 보기 버튼
        binding.btnShowRaw.setOnClickListener {
            val dataModel = collectInputData()
            if (dataModel != null) {
                val rawHex = AdvertisePacketBuilder.getAdvertiseRawHex(dataModel)

                val dialogBinding = RawPacketDialogBinding.inflate(layoutInflater)
                dialogBinding.tvRawHex.text = rawHex
                val dialog = AlertDialog.Builder(requireContext())
                    .setTitle("BLE Raw Packet")
                    .setView(dialogBinding.root)
                    .setPositiveButton("닫기", null)
                    .create()
                dialogBinding.btnCopyRaw.setOnClickListener {
                    val cm =
                        requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("BLE Raw Packet", rawHex))
                    toast("복사되었습니다")
                }
                dialog.show()
            }
        }

        // 스캔 상태 관찰
        viewModel.isScanning.observe(viewLifecycleOwner) { scanning ->
            binding.etCardNumber.isEnabled = !scanning
            binding.etPhoneLast4.isEnabled = !scanning
            binding.btnShowRaw.isEnabled = !scanning
            binding.btnStart?.isEnabled = !scanning
        }

        // 사용자 메시지 수집
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.showMessage.collect { msg -> toast(msg) }
        }
    }

    fun collectInputData(): com.mcandle.bleapp.model.AdvertiseDataModel? {
        val cardNumber = binding.etCardNumber.text.toString().trim()
        val phoneLast4 = binding.etPhoneLast4.text.toString().trim()
        
        // 설정에서 값 가져오기
        val deviceName = settingsManager.getDeviceName()
        val encoding = settingsManager.getEncodingType()
        val advMode = settingsManager.getAdvertiseMode()

        // 유효성 검사
        if (cardNumber.length != 16 || !cardNumber.all { it.isDigit() }) {
            toast("카드번호는 숫자 16자리여야 합니다")
            return null
        }
        if (phoneLast4.length != 4 || !phoneLast4.all { it.isDigit() }) {
            toast("전화번호 마지막 4자리를 숫자로 입력하세요")
            return null
        }

        return com.mcandle.bleapp.model.AdvertiseDataModel(
            cardNumber = cardNumber,
            phoneLast4 = phoneLast4,
            deviceName = deviceName,
            encoding = encoding,
            advertiseMode = advMode
        )
    }

    private fun toast(msg: CharSequence) {
        Toast.makeText(requireContext().applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
