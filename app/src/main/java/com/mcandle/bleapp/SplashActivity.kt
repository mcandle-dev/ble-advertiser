package com.mcandle.bleapp

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mcandle.bleapp.databinding.ActivitySplashBinding
import java.io.IOException

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    
    // 스플래시 화면 지속 시간 (밀리초)
    private val SPLASH_DISPLAY_LENGTH = 2000L // 2초 (시스템 스플래시 + 커스텀 스플래시)
    private var isKeepSplashScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // 🔥 Splash Screen API 설치 (Cold Start 최적화)
        try {
            val splashScreen = installSplashScreen()
            splashScreen.setKeepOnScreenCondition { isKeepSplashScreen }
        } catch (e: Exception) {
            Log.w("SplashActivity", "Splash Screen API 사용 불가: ${e.message}")
        }
        
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ActionBar와 StatusBar 설정
        supportActionBar?.hide()
        window.statusBarColor = getColor(android.R.color.black)

        // Assets 폴더에서 JASMIN BLACK 카드 이미지 로드
        loadCardImageFromAssets()

        // 일정 시간 후 MainActivity로 이동
        Handler(Looper.getMainLooper()).postDelayed({
            isKeepSplashScreen = false // 시스템 스플래시 해제
            startMainActivity()
        }, SPLASH_DISPLAY_LENGTH)
    }

    private fun loadCardImageFromAssets() {
        try {
            val inputStream = assets.open("JasminBlack-463x463.png")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            
            // 스플래시에서는 카드를 세로로 표시 (90도 회전)
            val matrix = android.graphics.Matrix()
            matrix.postRotate(90f)
            val rotatedBitmap = android.graphics.Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            
            binding.ivSplashCard.setImageBitmap(rotatedBitmap)
            inputStream.close()
            Log.d("SplashActivity", "Assets에서 카드 이미지 로드 성공")
        } catch (e: IOException) {
            Log.e("SplashActivity", "Assets 이미지 로드 실패: ${e.message}")
            // 기본 drawable로 fallback
            binding.ivSplashCard.setImageResource(R.drawable.jasmin_black_card_real)
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        
        // 부드러운 전환 애니메이션
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        
        // SplashActivity 종료 (뒤로가기 방지)
        finish()
    }

    override fun onBackPressed() {
        // 스플래시 화면에서 뒤로가기 방지
        // 아무것도 하지 않음
    }
}