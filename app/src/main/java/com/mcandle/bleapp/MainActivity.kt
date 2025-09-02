package com.mcandle.bleapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.mcandle.bleapp.databinding.ActivityMainBinding
import com.mcandle.bleapp.fragment.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ActionBar 숨기기
        supportActionBar?.hide()

        setupBottomNavigation()
        
        // 기본으로 홈 Fragment 표시
        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
            binding.bottomNavigation.selectedItemId = R.id.nav_home
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