package com.mcandle.bleapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.mcandle.bleapp.databinding.ActivityMainBinding
import com.mcandle.bleapp.fragment.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // 블루투스 권한 요청 런처 (앱 실행 시 1회만)
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d("MainActivity", "모든 블루투스 권한 승인됨")
            Toast.makeText(this, "블루투스 권한이 승인되었습니다", Toast.LENGTH_SHORT).show()
        } else {
            Log.w("MainActivity", "블루투스 권한이 거부됨")
            Toast.makeText(this, "블루투스 권한이 필요합니다. 설정에서 권한을 허용해주세요.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ActionBar 숨기기
        supportActionBar?.hide()

        // 🔥 앱 실행 시 블루투스 권한 체크
        checkBluetoothPermissions()

        setupBottomNavigation()

        // 기본으로 홈 Fragment 표시
        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
            binding.bottomNavigation.selectedItemId = R.id.nav_home
        }
    }

    /**
     * 블루투스 권한 체크 (앱 실행 시 1회)
     * - 권한이 없으면 권한 요청 팝업 표시
     * - 권한이 있으면 조용히 진행
     */
    private fun checkBluetoothPermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 이상
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            // Android 12 미만
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            // 모든 권한이 있으면 조용히 진행
            Log.d("MainActivity", "모든 블루투스 권한이 이미 승인됨 - 조용히 진행")
        } else {
            // 권한 요청
            Log.d("MainActivity", "블루투스 권한 요청: ${missingPermissions.joinToString()}")
            bluetoothPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    replaceFragment(HomeFragment())
                    true
                }
                R.id.nav_store -> {
                    replaceFragment(StoreFragment())
                    true
                }
                R.id.nav_card -> {
                    replaceFragment(CardFragment())
                    true
                }
                R.id.nav_shopping -> {
                    replaceFragment(ShoppingFragment())
                    true
                }
                R.id.nav_my -> {
                    replaceFragment(MyFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}