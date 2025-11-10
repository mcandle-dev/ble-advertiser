package com.mcandle.bleapp.util

import android.content.Context
import android.content.SharedPreferences
import com.mcandle.bleapp.model.AdvertiseMode
import com.mcandle.bleapp.model.EncodingType

class SettingsManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_CARD_NUMBER = "card_number"
        private const val KEY_PHONE_LAST4 = "phone_last4"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_ENCODING = "encoding"
        private const val KEY_ADVERTISE_MODE = "advertise_mode"

        // 기본값들 (테스트용)
        const val DEFAULT_CARD_NUMBER = "1234567812345678"
        const val DEFAULT_PHONE_LAST4 = "1234"
        const val DEFAULT_DEVICE_NAME = "mcandle"
        val DEFAULT_ENCODING = EncodingType.ASCII
        val DEFAULT_ADVERTISE_MODE = AdvertiseMode.DATA
    }
    
    // 카드번호
    fun getCardNumber(): String {
        return prefs.getString(KEY_CARD_NUMBER, DEFAULT_CARD_NUMBER) ?: DEFAULT_CARD_NUMBER
    }
    
    fun setCardNumber(cardNumber: String) {
        prefs.edit().putString(KEY_CARD_NUMBER, cardNumber).apply()
    }
    
    // 전화번호 마지막 4자리
    fun getPhoneLast4(): String {
        return prefs.getString(KEY_PHONE_LAST4, DEFAULT_PHONE_LAST4) ?: DEFAULT_PHONE_LAST4
    }
    
    fun setPhoneLast4(phoneLast4: String) {
        prefs.edit().putString(KEY_PHONE_LAST4, phoneLast4).apply()
    }
    
    // 디바이스 이름
    fun getDeviceName(): String {
        return prefs.getString(KEY_DEVICE_NAME, DEFAULT_DEVICE_NAME) ?: DEFAULT_DEVICE_NAME
    }
    
    fun setDeviceName(deviceName: String) {
        prefs.edit().putString(KEY_DEVICE_NAME, deviceName).apply()
    }
    
    // 인코딩 타입
    fun getEncodingType(): EncodingType {
        val encodingName = prefs.getString(KEY_ENCODING, DEFAULT_ENCODING.name) ?: DEFAULT_ENCODING.name
        return try {
            EncodingType.valueOf(encodingName)
        } catch (e: IllegalArgumentException) {
            DEFAULT_ENCODING
        }
    }
    
    fun setEncodingType(encoding: EncodingType) {
        prefs.edit().putString(KEY_ENCODING, encoding.name).apply()
    }
    
    // Advertise 모드
    fun getAdvertiseMode(): AdvertiseMode {
        val modeName = prefs.getString(KEY_ADVERTISE_MODE, DEFAULT_ADVERTISE_MODE.name) ?: DEFAULT_ADVERTISE_MODE.name
        return try {
            AdvertiseMode.valueOf(modeName)
        } catch (e: IllegalArgumentException) {
            DEFAULT_ADVERTISE_MODE
        }
    }
    
    fun setAdvertiseMode(mode: AdvertiseMode) {
        prefs.edit().putString(KEY_ADVERTISE_MODE, mode.name).apply()
    }
}