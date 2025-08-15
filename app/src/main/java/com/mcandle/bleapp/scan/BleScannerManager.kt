package com.mcandle.bleapp.scan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * BLE 스캔 매니저
 *
 * - MainActivity에서 권한 체크/요청 후 사용하세요.
 * - startScan(phoneLast4) 호출 → iBeacon 유사 광고 탐지
 * - UUID 16B(ASCII) 의 뒤 4자리(전화번호) == phoneLast4 면 onMatch 콜백
 * - (선택) expectedMinor 를 지정하면 minor 일치도 검사합니다.
 */
private const val TAG_BLE_SCAN = "BleScannerManager"

class BleScannerManager(
    private val context: Context,
    private val listener: Listener,
    private val expectedMinor: Int? = 3454, // 요구사항에 맞춰 기본 3454, 필요없으면 null 전달
    private val scanTimeoutMs: Long = 15_000L // 매칭 안되면 자동 중지

) {

    interface Listener {
        /** 매칭 성공 시 호출 (스캔은 내부에서 stopScan()으로 이미 중지됨) */
        fun onMatch(frame: IBeaconParser.IBeaconFrame)
        /** 타임아웃/오류 등 사용자 피드백이 필요할 때 */
        fun onInfo(message: String) {} // 선택 구현
    }

    private val tag = "BleScannerManager"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val btMgr: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = btMgr.adapter
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner

    @Volatile
    var isScanning: Boolean = false
        private set

    private var targetPhone4: String = ""
    private var timeoutPosted = false

    /**
     * iBeacon prefix 매칭 필터 (Manufacturer ID=0x004C, data prefix=0x02,0x15)
     */
    private fun buildFilters(): List<ScanFilter> {
        val prefix = byteArrayOf(0x02, 0x15)
        val mask = byteArrayOf(0xFF.toByte(), 0xFF.toByte()) // 두 바이트 모두 일치
        return listOf(
            ScanFilter.Builder()
                .setManufacturerData(0x004C, prefix, mask)
                .build()
        )
    }

    private fun buildSettings(): ScanSettings =
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

    /**
     * 스캔 시작.
     * @param phoneLast4 사용자 입력 전화번호 4자리 (숫자 4자리)
     */
    @SuppressLint("MissingPermission")
    fun startScan(phoneLast4: String) {
        Log.d(tag, "isScanning"+isScanning)
        if (isScanning) {
            Log.d(tag, "Already scanning. Ignored.")
            return
        }
        if (phoneLast4.length != 4 || !phoneLast4.all { it.isDigit() }) {
            listener.onInfo("전화번호 4자리를 정확히 입력하세요.")
            return
        }
        val ad = adapter
        if (ad == null) {
            listener.onInfo("블루투스 어댑터를 사용할 수 없습니다.")
            return
        }
        if (!ad.isEnabled) {
            listener.onInfo("블루투스를 켜주세요.")
            return
        }
        val sc = scanner
        if (sc == null) {
            listener.onInfo("스캐너를 초기화할 수 없습니다.")
            return
        }

        targetPhone4 = phoneLast4
        try {
            sc.startScan(buildFilters(), buildSettings(), callback)
            isScanning = true
            postTimeout()
            Log.d(tag, "Scan started (phone4=$phoneLast4)")
        } catch (se: SecurityException) {
            // 권한 미승인 등
            Log.w(tag, "startScan security error", se)
            listener.onInfo("스캔 권한이 필요합니다.")
        } catch (t: Throwable) {
            Log.e(tag, "startScan error", t)
            listener.onInfo("스캔 시작 중 오류가 발생했습니다.")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        Log.d(TAG_BLE_SCAN, "stopScan() called", Throwable("trace: stopScan"))
        if (!isScanning) return
        try {
            scanner?.stopScan(callback)
        } catch (se: SecurityException) {
            Log.w(tag, "stopScan security error", se)
        } catch (t: Throwable) {
            Log.e(tag, "stopScan error", t)
        } finally {
            isScanning = false
            removeTimeout()
            Log.d(tag, "Scan stopped")
        }
    }

    private fun postTimeout() {
        if (timeoutPosted) return
        timeoutPosted = true
        mainHandler.postDelayed({
            if (isScanning) {
                stopScan()
                listener.onInfo("주변에서 일치하는 신호를 찾지 못했습니다.")
            }
        }, scanTimeoutMs)
    }

    private fun removeTimeout() {
        if (!timeoutPosted) return
        timeoutPosted = false
        mainHandler.removeCallbacksAndMessages(null)
    }

    // --- Scan Callback ---

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (r in results) handleResult(r)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(tag, "onScanFailed: $errorCode")
            listener.onInfo("스캔 실패: $errorCode")
            stopScan()
        }
    }

    private fun handleResult(result: ScanResult) {
        val frame = IBeaconParser.parseFrom(result) ?: return

        // phone 4자리 매칭
        if (frame.phoneLast4 != targetPhone4) return

        // (선택) minor 필터
        if (expectedMinor != null && frame.minor != expectedMinor) return

        // 조건 만족 → 스캔 중지 & 콜백
        stopScan()
        Log.d(tag, "Match found: order=${frame.orderNumber}, major=${frame.major}, minor=${frame.minor}")
        listener.onMatch(frame)
    }
}
