package com.mcandle.bleapp

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.mcandle.bleapp.databinding.ActivitySettingsBinding
import com.mcandle.bleapp.model.AdvertiseMode
import com.mcandle.bleapp.model.EncodingType
import com.mcandle.bleapp.util.SettingsManager
import com.mcandle.bleapp.scan.ScanMode

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
        setupSaveButton()
    }
    
    private fun loadSettings() {
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
        
        // Scan 필터 설정
        when (settingsManager.getScanFilter()) {
            ScanMode.ALL -> binding.rbAll.isChecked = true
            ScanMode.RFSTAR_ONLY -> binding.rbRfstarOnly.isChecked = true
            ScanMode.IBEACON_RFSTAR -> binding.rbIbeaconRfstar.isChecked = true
            else -> binding.rbAll.isChecked = true  // 기타 모드는 ALL로 기본 설정
        }
    }
    
    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            saveSettings()
            finish() // 설정 저장 후 화면 닫기
        }
    }
    
    private fun saveSettings() {
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
        
        // Scan 필터
        val scanFilter = when {
            binding.rbRfstarOnly.isChecked -> ScanMode.RFSTAR_ONLY
            binding.rbIbeaconRfstar.isChecked -> ScanMode.IBEACON_RFSTAR
            else -> ScanMode.ALL
        }
        settingsManager.setScanFilter(scanFilter)
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