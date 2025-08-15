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
import com.mcandle.bleapp.ui.scan.ScanListActivity
import kotlinx.coroutines.launch

class InputFormFragment : Fragment() {
    private var _binding: FragmentInputFormBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BleAdvertiseViewModel by activityViewModels()

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

        // 전화번호 4자리 입력 → ViewModel에 값만 반영 (자동 스캔 X)
        binding.etPhoneLast4.doOnTextChanged { text, _, _, _ ->
            viewModel.setPhoneLast4(text?.toString().orEmpty())
        }

        // [변경점] 스캔 시작 버튼: ScanListActivity 실행 (간단 목록 화면)
        binding.btnStart?.setOnClickListener {
            // 필요 시 입력값 전달 예시:
            // val phone4 = binding.etPhoneLast4.text?.toString()?.trim()
            // val intent = Intent(requireContext(), ScanListActivity::class.java).apply {
            //     putExtra("phone_last4", phone4)
            // }
            // startActivity(intent)

            val intent = Intent(requireContext(), ScanListActivity::class.java)
            startActivity(intent)
        }

        // 패킷 적용 버튼 (기존 동작 유지)
        binding.btnApplyPacket.setOnClickListener {
            val cardNumber = binding.etCardNumber.text.toString()
            val deviceName = binding.etDeviceName.text.toString()
            val phoneLast4 = binding.etPhoneLast4.text.toString().trim()
            val encoding =
                if (binding.rgEncoding.checkedRadioButtonId == binding.rbBcd.id) EncodingType.BCD
                else EncodingType.ASCII
            val advMode =
                if (binding.rgAdvMode.checkedRadioButtonId == binding.rbMinimal.id) AdvertiseMode.MINIMAL
                else AdvertiseMode.DATA

            if (cardNumber.length != 16) {
                toast("카드번호는 16자리여야 합니다")
                return@setOnClickListener
            }
            if (phoneLast4.length != 4 || !phoneLast4.all { it.isDigit() }) {
                toast("전화번호 마지막 4자리를 숫자로 입력하세요.")
                return@setOnClickListener
            }

            viewModel.updateData(cardNumber, phoneLast4, deviceName, encoding, advMode)
            toast("패킷이 적용되었습니다")
        }

        // RAW 보기 버튼 (기존 동작 유지)
        binding.btnShowRaw.setOnClickListener {
            val cardNumber = binding.etCardNumber.text.toString()
            val deviceName = binding.etDeviceName.text.toString()
            val phoneLast4 = binding.etPhoneLast4.text.toString().trim()
            val encoding =
                if (binding.rgEncoding.checkedRadioButtonId == binding.rbBcd.id) EncodingType.BCD
                else EncodingType.ASCII
            val advMode =
                if (binding.rgAdvMode.checkedRadioButtonId == binding.rbMinimal.id) AdvertiseMode.MINIMAL
                else AdvertiseMode.DATA

            val dataModel = com.mcandle.bleapp.model.AdvertiseDataModel(
                cardNumber, phoneLast4, deviceName, encoding, advMode
            )
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

        // 스캔 상태 관찰 → 스캔 중 UI 잠그기/풀기 (조건 매칭용 스캔 플로우 유지)
        viewModel.isScanning.observe(viewLifecycleOwner) { scanning ->
            binding.etCardNumber.isEnabled = !scanning
            binding.etDeviceName.isEnabled = !scanning
            binding.etPhoneLast4.isEnabled = !scanning
            binding.rgEncoding.isEnabled = !scanning
            binding.rbAscii.isEnabled = !scanning
            binding.rbBcd.isEnabled = !scanning
            binding.rgAdvMode.isEnabled = !scanning
            binding.rbMinimal.isEnabled = !scanning
            binding.rbData.isEnabled = !scanning
            binding.btnApplyPacket.isEnabled = !scanning
            binding.btnShowRaw.isEnabled = !scanning
            binding.btnStart?.isEnabled = !scanning
        }

        // ViewModel에서 오는 사용자 메시지(토스트)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.showMessage.collect { msg -> toast(msg) }
        }
    }

    private fun toast(msg: CharSequence) {
        Toast.makeText(requireContext().applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
