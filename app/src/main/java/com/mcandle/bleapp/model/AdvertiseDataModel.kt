package com.mcandle.bleapp.model

data class AdvertiseDataModel(
    val cardNumber: String,
    val kakaoPayInstalled: Boolean,
    val deviceName: String = "mcandle"
)