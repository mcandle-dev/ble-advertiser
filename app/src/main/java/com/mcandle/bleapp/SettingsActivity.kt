package com.mcandle.bleapp

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mcandle.bleapp.databinding.ActivitySettingsBinding
import com.mcandle.bleapp.databinding.RawPacketDialogBinding
import com.mcandle.bleapp.model.AdvertiseDataModel
import com.mcandle.bleapp.model.AdvertiseMode
import com.mcandle.bleapp.model.EncodingType
import com.mcandle.bleapp.util.SettingsManager
import com.mcandle.bleapp.advertise.AdvertisePacketBuilder

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 액션바 설정
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "설정"
        
        settingsManager = SettingsManager(this)
        
        loadSettings()
        setupButtons()
    }
    
    private fun loadSettings() {
        // 카드번호/전화번호 불러오기
        binding.etCardNumber.setText(settingsManager.getCardNumber())
        binding.etPhoneLast4.setText(settingsManager.getPhoneLast4())
        
        // 저장된 설정 불러오기
        binding.etDeviceName.setText(settingsManager.getDeviceName())
        
        // 전송방식 설정
        when (settingsManager.getEncodingType()) {
            EncodingType.ASCII -> binding.rbAscii.isChecked = true
            EncodingType.BCD -> binding.rbBcd.isChecked = true
        }
        
        // Advertise Mode 설정
        when (settingsManager.getAdvertiseMode()) {
            AdvertiseMode.MINIMAL -> binding.rbMinimal.isChecked = true
            AdvertiseMode.DATA -> binding.rbData.isChecked = true
        }
    }
    
    private fun setupButtons() {
        // 저장 버튼
        binding.btnSave.setOnClickListener {
            saveSettings()
            showToast("설정이 저장되었습니다")
            finish() // 설정 저장 후 화면 닫기
        }
        
        // Raw Packet 버튼
        binding.btnShowRaw.setOnClickListener {
            val dataModel = collectInputData()
            if (dataModel != null) {
                val rawHex = AdvertisePacketBuilder.getAdvertiseRawHex(dataModel)

                val dialogBinding = RawPacketDialogBinding.inflate(layoutInflater)
                dialogBinding.tvRawHex.text = rawHex
                val dialog = AlertDialog.Builder(this)
                    .setTitle("BLE Raw Packet")
                    .setView(dialogBinding.root)
                    .setPositiveButton("닫기", null)
                    .create()
                dialogBinding.btnCopyRaw.setOnClickListener {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("BLE Raw Packet", rawHex))
                    showToast("복사되었습니다")
                }
                dialog.show()
            }
        }
    }
    
    private fun saveSettings() {
        // 카드번호/전화번호 저장
        val cardNumber = binding.etCardNumber.text.toString().trim()
        val phoneLast4 = binding.etPhoneLast4.text.toString().trim()
        
        if (cardNumber.isNotEmpty()) {
            settingsManager.setCardNumber(cardNumber)
        }
        if (phoneLast4.isNotEmpty()) {
            settingsManager.setPhoneLast4(phoneLast4)
        }
        
        // 디바이스 이름
        val deviceName = binding.etDeviceName.text.toString().trim()
        if (deviceName.isNotEmpty()) {
            settingsManager.setDeviceName(deviceName)
        }
        
        // 전송방식
        val encoding = if (binding.rbBcd.isChecked) EncodingType.BCD else EncodingType.ASCII
        settingsManager.setEncodingType(encoding)
        
        // Advertise Mode
        val advMode = if (binding.rbMinimal.isChecked) AdvertiseMode.MINIMAL else AdvertiseMode.DATA
        settingsManager.setAdvertiseMode(advMode)
    }
    
    private fun collectInputData(): AdvertiseDataModel? {
        val cardNumber = binding.etCardNumber.text.toString().trim()
        val phoneLast4 = binding.etPhoneLast4.text.toString().trim()
        val deviceName = binding.etDeviceName.text.toString().trim()
        
        // 전송방식
        val encoding = if (binding.rbBcd.isChecked) EncodingType.BCD else EncodingType.ASCII
        
        // Advertise Mode
        val advMode = if (binding.rbMinimal.isChecked) AdvertiseMode.MINIMAL else AdvertiseMode.DATA

        // 유효성 검사
        if (cardNumber.length != 16 || !cardNumber.all { it.isDigit() }) {
            showToast("카드번호는 숫자 16자리여야 합니다")
            return null
        }
        if (phoneLast4.length != 4 || !phoneLast4.all { it.isDigit() }) {
            showToast("전화번호 마지막 4자리를 숫자로 입력하세요")
            return null
        }

        return AdvertiseDataModel(
            cardNumber = cardNumber,
            phoneLast4 = phoneLast4,
            deviceName = deviceName.ifEmpty { "mcandle" },
            encoding = encoding,
            advertiseMode = advMode
        )
    }
    
    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}