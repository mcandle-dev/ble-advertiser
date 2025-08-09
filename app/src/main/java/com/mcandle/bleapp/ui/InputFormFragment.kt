package com.mcandle.bleapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.mcandle.bleapp.databinding.FragmentInputFormBinding
import com.mcandle.bleapp.databinding.RawPacketDialogBinding
import com.mcandle.bleapp.model.EncodingType
import com.mcandle.bleapp.model.AdvertiseMode
import com.mcandle.bleapp.viewmodel.BleAdvertiseViewModel
import com.mcandle.bleapp.advertise.AdvertisePacketBuilder

class InputFormFragment : Fragment() {
    private var _binding: FragmentInputFormBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BleAdvertiseViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInputFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnApplyPacket.setOnClickListener {
            val cardNumber = binding.etCardNumber.text.toString()
            val deviceName = binding.etDeviceName.text.toString()
            val phoneLast4 = binding.etPhoneLast4.text.toString().trim()
            val encoding = if (binding.rgEncoding.checkedRadioButtonId == binding.rbBcd.id)
                EncodingType.BCD else EncodingType.ASCII
            val advMode = if (binding.rgAdvMode.checkedRadioButtonId == binding.rbMinimal.id)
                AdvertiseMode.MINIMAL else AdvertiseMode.DATA

            if (cardNumber.length != 16) {
                Toast.makeText(requireContext(), "카드번호는 16자리여야 합니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (phoneLast4.length != 4 || !phoneLast4.all { it.isDigit() }) {
                Toast.makeText(requireContext(), "전화번호 마지막 4자리를 숫자로 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.updateData(cardNumber, phoneLast4, deviceName, encoding, advMode)
            Toast.makeText(requireContext(), "패킷이 적용되었습니다", Toast.LENGTH_SHORT).show()
        }

        // Raw 버튼
        binding.btnShowRaw.setOnClickListener {
            val cardNumber = binding.etCardNumber.text.toString()
            val deviceName = binding.etDeviceName.text.toString()
            val phoneLast4 = binding.etPhoneLast4.text.toString().trim()
            val encoding = if (binding.rgEncoding.checkedRadioButtonId == binding.rbBcd.id)
                EncodingType.BCD else EncodingType.ASCII
            val advMode = if (binding.rgAdvMode.checkedRadioButtonId == binding.rbMinimal.id)
                AdvertiseMode.MINIMAL else AdvertiseMode.DATA

            val dataModel = com.mcandle.bleapp.model.AdvertiseDataModel(
                cardNumber, phoneLast4, deviceName, encoding, advMode
            )
            val rawHex = AdvertisePacketBuilder.getAdvertiseRawHex(dataModel)

            // 팝업
            val dialogBinding = RawPacketDialogBinding.inflate(layoutInflater)
            dialogBinding.tvRawHex.text = rawHex
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("BLE Raw Packet")
                .setView(dialogBinding.root)
                .setPositiveButton("닫기", null)
                .create()
            dialogBinding.btnCopyRaw.setOnClickListener {
                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("BLE Raw Packet", rawHex))
                Toast.makeText(requireContext(), "복사되었습니다", Toast.LENGTH_SHORT).show()
            }
            dialog.show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
