package com.mcandle.bleapp.scan

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

class BleScannerManager(
    private val context: Context,
    private val listener: Listener,
    private val expectedMinor: Int? = null,
    private val maxTimeoutMs: Long = 60_000L,      // ✅ 1분으로 통일
    private val mode: ScanMode = ScanMode.ALL
) {

    interface Listener {
        fun onMatch(frame: IBeaconParser.IBeaconFrame)
        fun onInfo(message: String)
        fun onDeviceFound(result: ScanResult)
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private var matched = false
    private var expectedPhoneLast4: String? = null
    val scanning: Boolean get() = isScanning

    @SuppressLint("MissingPermission")
    fun startScan(phoneLast4: String? = null) {
        if (isScanning) return
        matched = false
        expectedPhoneLast4 = phoneLast4

        if (scanner == null) {
            listener.onInfo("BLE 스캐너를 초기화할 수 없습니다.")
            return
        }

        val perm = if (Build.VERSION.SDK_INT >= 31)
            Manifest.permission.BLUETOOTH_SCAN
        else
            Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
            listener.onInfo("스캔 권한이 필요합니다.")
            return
        }

        val filters = buildScanFilters(mode)
        val settings = buildScanSettings()

        try {
            scanner.startScan(filters, settings, scanCallback)
            isScanning = true
            listener.onInfo("스캔 시작 (mode=$mode)")

            // ✅ 1분 타임아웃 → 성공 여부 확인 후 메시지 출력
            handler.postDelayed({
                if (isScanning) {
                    stopScan()
                    if (!matched) {
                        listener.onInfo("주변에서 일치하는 신호를 찾지 못했습니다. (1분 경과)")
                    } else {
                        listener.onInfo("스캔 종료 (매칭 완료)")
                    }
                }
            }, maxTimeoutMs)

        } catch (se: SecurityException) {
            listener.onInfo("스캔 권한이 필요합니다. (SecurityException)")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        try {
            scanner?.stopScan(scanCallback)
            Log.d("BleScannerManager", "스캔 중지됨")
        } catch (se: SecurityException) {
            listener.onInfo("스캔 중지 권한 오류")
        } finally {
            isScanning = false
            handler.removeCallbacksAndMessages(null)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            listener.onDeviceFound(result)

            val frame = IBeaconParser.parseFrom(result)
            if (frame != null) {
                Log.d("BleScannerManager", "Parsed frame: order=${frame.orderNumber}, phone=${frame.phoneLast4}")
                Log.d("BleScannerManager", "Expected phone: $expectedPhoneLast4, Frame phone: ${frame.phoneLast4}")
                Log.d("BleScannerManager", "Phone match: ${frame.phoneLast4 == expectedPhoneLast4}")

                if (expectedPhoneLast4 != null && frame.phoneLast4 == expectedPhoneLast4) {
                    if (expectedMinor == null || frame.minor == expectedMinor) {
                        matched = true
                        Log.d("BleScannerManager", "매칭 성공! listener.onMatch() 호출")
                        listener.onMatch(frame)
                        stopScan()
                    } else {
                        Log.d("BleScannerManager", "Minor 불일치: expected=$expectedMinor, actual=${frame.minor}")
                    }
                } else {
                    Log.d("BleScannerManager", "전화번호 불일치 또는 expectedPhoneLast4가 null")
                }
            } else {
                Log.d("BleScannerManager", "iBeacon 파싱 실패 - 일반 BLE 디바이스")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            listener.onInfo("스캔 실패: $errorCode")
        }
    }

    private fun buildScanFilters(mode: ScanMode): List<ScanFilter> {
        val RFSTAR = 0x5246
        val APPLE = 0x004C

        return when (mode) {
            ScanMode.ALL -> emptyList()

            ScanMode.RFSTAR_ONLY ->
                listOf(
                    ScanFilter.Builder()
                        .setManufacturerData(RFSTAR, byteArrayOf(), byteArrayOf())
                        .build()
                )

            ScanMode.IBEACON_RFSTAR ->
                listOf(
                    ScanFilter.Builder()
                        .setManufacturerData(
                            RFSTAR,
                            byteArrayOf(0x02, 0x15),
                            byteArrayOf(0xFF.toByte(), 0xFF.toByte())
                        )
                        .build()
                )

            ScanMode.IBEACON_APPLE ->
                listOf(
                    ScanFilter.Builder()
                        .setManufacturerData(
                            APPLE,
                            byteArrayOf(0x02, 0x15),
                            byteArrayOf(0xFF.toByte(), 0xFF.toByte())
                        )
                        .build()
                )

            ScanMode.IBEACON_ANY ->
                listOf(
                    ScanFilter.Builder()
                        .setManufacturerData(
                            RFSTAR,
                            byteArrayOf(0x02, 0x15),
                            byteArrayOf(0xFF.toByte(), 0xFF.toByte())
                        )
                        .build(),
                    ScanFilter.Builder()
                        .setManufacturerData(
                            APPLE,
                            byteArrayOf(0x02, 0x15),
                            byteArrayOf(0xFF.toByte(), 0xFF.toByte())
                        )
                        .build()
                )
        }
    }

    private fun buildScanSettings(): ScanSettings {
        val b = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            b.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        }
        return b.build()
    }
}

enum class ScanMode {
    ALL,
    RFSTAR_ONLY,
    IBEACON_RFSTAR,
    IBEACON_APPLE,
    IBEACON_ANY
}
