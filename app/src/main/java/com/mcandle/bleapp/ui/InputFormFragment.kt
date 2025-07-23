package com.mcandle.bleapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.mcandle.bleapp.databinding.FragmentInputFormBinding
import com.mcandle.bleapp.model.EncodingType
import com.mcandle.bleapp.viewmodel.BleAdvertiseViewModel

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
            val kakaoPay = binding.rgKakaoPay.checkedRadioButtonId == binding.rbKakaoYes.id
            val encoding = if (binding.rgEncoding.checkedRadioButtonId == binding.rbBcd.id)
                EncodingType.BCD else EncodingType.ASCII

            if (cardNumber.length != 16) {
                Toast.makeText(requireContext(), "카드번호는 16자리여야 합니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.updateData(cardNumber, kakaoPay, deviceName, encoding)
            Toast.makeText(requireContext(), "패킷이 적용되었습니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
