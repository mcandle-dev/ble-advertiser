package com.mcandle.bleapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mcandle.bleapp.advertise.AdvertiserManager
import com.mcandle.bleapp.databinding.ActivityMainBinding
import com.mcandle.bleapp.viewmodel.BleAdvertiseViewModel
import androidx.lifecycle.ViewModelProvider
import com.mcandle.bleapp.ui.InputFormFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var advertiserManager: AdvertiserManager
    private lateinit var viewModel: BleAdvertiseViewModel

    // Android 12 이상 BLE 권한
    private val blePermissions = arrayOf(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN
    )

    // Android 11 이하 위치 권한
    private val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ViewModel
        viewModel = ViewModelProvider(this)[BleAdvertiseViewModel::class.java]

        // Advertiser 초기화 (ViewModel 주입)
        advertiserManager = AdvertiserManager(this, viewModel)

        // InputFormFragment 표시
        supportFragmentManager.beginTransaction()
            .replace(R.id.inputFormFragmentContainer, InputFormFragment())
            .commit()

        // 광고 상태 변화에 따라 UI 변경
        viewModel.isAdvertising.observe(this) { isAdvertising ->
            binding.btnStart.isEnabled = !isAdvertising
            binding.btnStop.isEnabled = isAdvertising
            binding.btnStart.text = if (isAdvertising) "적용중..." else "Advertise Start"
        }

        // 버튼 클릭 이벤트
        binding.btnStart.setOnClickListener {
            if (!advertiserManager.isSupported()) {
                showToast("BLE Advertise를 지원하지 않는 기기입니다.")
                return@setOnClickListener
            }

            val data = viewModel.currentData.value
            if (data == null) {
                showToast("패킷 데이터를 먼저 입력해주세요.")
                return@setOnClickListener
            }

            advertiserManager.startAdvertise(data)
        }

        binding.btnStop.setOnClickListener {
            advertiserManager.stopAdvertise()
        }

        checkPermissions()
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissions() {
        val neededPermissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blePermissions.forEach {
                if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                    neededPermissions.add(it)
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, locationPermission) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(locationPermission)
            }
        }

        if (neededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, neededPermissions.toTypedArray(), 1001)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        advertiserManager.stopAdvertise()
    }
}
