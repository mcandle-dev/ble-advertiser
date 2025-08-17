package com.mcandle.bleapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcandle.bleapp.model.AdvertiseDataModel
import com.mcandle.bleapp.model.AdvertiseMode
import com.mcandle.bleapp.model.EncodingType
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * 광고(Advertise) + 스캔(Scan)의 상태/입력값/이벤트를 관리하는 ViewModel.
 */
class BleAdvertiseViewModel : ViewModel() {

    // ---------------------------
    // 광고(Advertise) 상태/데이터
    // ---------------------------

    private val _isAdvertising = MutableLiveData(false)
    val isAdvertising: LiveData<Boolean> = _isAdvertising
    fun setAdvertising(on: Boolean) { _isAdvertising.value = on }

    private val _currentData = MutableLiveData<AdvertiseDataModel?>(null)
    val currentData: LiveData<AdvertiseDataModel?> = _currentData

    fun updateData(
        cardNumber: String,
        phoneLast4: String,
        deviceName: String,
        encoding: EncodingType,
        advMode: AdvertiseMode
    ) {
        if (cardNumber.length != 16 || !cardNumber.all { it.isDigit() }) {
            notifyMessage("카드번호는 숫자 16자리여야 합니다.")
            return
        }
        if (phoneLast4.length != 4 || !phoneLast4.all { it.isDigit() }) {
            notifyMessage("전화번호 마지막 4자리를 숫자로 입력하세요.")
            return
        }

        // 🔧 여기! named argument를 모델의 실제 필드명과 맞춥니다.
        // 대부분의 프로젝트에서 AdvertiseDataModel은 advertiseMode라는 필드를 씁니다.
        _currentData.value = AdvertiseDataModel(
            cardNumber = cardNumber,
            phoneLast4 = phoneLast4,
            deviceName = deviceName,
            encoding = encoding,
            advertiseMode = advMode   // ← advMode가 아니라 advertiseMode로!
            // 만약 당신 모델이 mode라면: mode = advMode
        )
    }

    // ---------------------------
    // 스캔(Scan) 입력/상태
    // ---------------------------

    private val _inputPhoneLast4 = MutableLiveData("")
    val inputPhoneLast4: LiveData<String> = _inputPhoneLast4

    fun setPhoneLast4(v: String) {
        val t = v.trim()
        if (t.length <= 4 && t.all { it.isDigit() }) {
            _inputPhoneLast4.value = t
        } else {
            viewModelScope.launch { _showMessage.emit("전화번호는 숫자 4자리만 입력하세요.") }
        }
    }

    private val _isScanning = MutableLiveData(false)
    val isScanning: LiveData<Boolean> = _isScanning
    fun setScanning(on: Boolean) { _isScanning.value = on }

    // ---------------------------
    // 스캔 시작 원샷 이벤트
    // ---------------------------

    private val _startScanRequest = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val startScanRequest: SharedFlow<String> = _startScanRequest

    fun onStartClicked() {
        val phone4 = _inputPhoneLast4.value.orEmpty()
        if (phone4.length != 4 || !phone4.all { it.isDigit() }) {
            viewModelScope.launch { _showMessage.emit("전화번호 마지막 4자리를 정확히 입력하세요.") }
            return
        }
        setScanning(true)
        viewModelScope.launch { _startScanRequest.emit(phone4) }
    }

    // ---------------------------
    // 메시지/매칭 이벤트
    // ---------------------------

    private val _showMessage = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val showMessage: SharedFlow<String> = _showMessage

    fun notifyMessage(msg: String) {
        viewModelScope.launch { _showMessage.emit(msg) }
    }

    data class ScanMatchInfo(
        val orderNumber: String,
        val phoneLast4: String,
        val major: Int,
        val minor: Int
    )

    private val _scanMatched = MutableSharedFlow<ScanMatchInfo>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val scanMatched: SharedFlow<ScanMatchInfo> = _scanMatched

    fun onScanMatched(info: ScanMatchInfo) {
        setScanning(false)
        viewModelScope.launch { _scanMatched.emit(info) }
    }

    fun onScanStopped() { setScanning(false) }

    fun onScanError(message: String) {
        setScanning(false)
        viewModelScope.launch { _showMessage.emit(message) }
    }
}
