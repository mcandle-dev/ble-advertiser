package com.mcandle.bleapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mcandle.bleapp.model.AdvertiseDataModel
import com.mcandle.bleapp.model.AdvertiseMode
import com.mcandle.bleapp.model.EncodingType

class BleAdvertiseViewModel : ViewModel() {
    private val _currentData = MutableLiveData<AdvertiseDataModel?>()
    val currentData: LiveData<AdvertiseDataModel?> = _currentData

    private val _isAdvertising = MutableLiveData(false)
    val isAdvertising: LiveData<Boolean> = _isAdvertising

    fun updateData(
        cardNumber: String,
        phoneLast4: String,
        deviceName: String,
        encoding: EncodingType,
        advertiseMode: AdvertiseMode
    ) {
        val name = if (deviceName.isBlank()) "mcandle" else deviceName
        _currentData.value = AdvertiseDataModel(cardNumber, phoneLast4, name, encoding, advertiseMode)
    }
    fun setAdvertising(active: Boolean) {
        _isAdvertising.value = active
    }
}
