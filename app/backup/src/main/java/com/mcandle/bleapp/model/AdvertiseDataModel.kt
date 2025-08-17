package com.mcandle.bleapp.model

data class AdvertiseDataModel(
    val cardNumber: String,
    val phoneLast4: String,
    val deviceName: String = "mcandle",
    val encoding: EncodingType = EncodingType.ASCII,
    val advertiseMode: AdvertiseMode = AdvertiseMode.DATA // 이 라인 추가!
)

enum class  EncodingType {ASCII, BCD}