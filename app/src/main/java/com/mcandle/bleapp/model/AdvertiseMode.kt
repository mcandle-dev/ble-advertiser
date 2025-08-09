package com.mcandle.bleapp.model

enum class AdvertiseMode {
    MINIMAL, // device name + uuid만 송신
    DATA     // 카드번호/카카오 등 데이터 포함 송신
}
