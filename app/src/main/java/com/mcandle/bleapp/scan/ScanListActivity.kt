package com.mcandle.bleapp.ui.scan

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mcandle.bleapp.R

class ScanListActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ScanListActivity"
        private const val APPLE_COMPANY_ID = 0x004C
        private const val RFSTAR_COMPANY_ID_BE = 0x4652 // Big Endian
        private const val RFSTAR_COMPANY_ID_LE = 0x5246 // Little Endian
    }

    private lateinit var lvResults: ListView
    private lateinit var tvTitle: TextView

    private val items = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private val seen = HashMap<String, Int>()

    private val btMgr by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val btAdapter: BluetoothAdapter? get() = btMgr.adapter
    private val bleScanner: BluetoothLeScanner? get() = btAdapter?.bluetoothLeScanner

    private var isScanning = false

    private fun requiredScanPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasAllScanPermissions(): Boolean {
        return requiredScanPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.any { !it.value }) {
            Toast.makeText(this, "스캔 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            startScanInternal()
        }
    }

    private fun ensurePermissionsOrRequest(): Boolean {
        return if (!hasAllScanPermissions()) {
            permissionLauncher.launch(requiredScanPermissions())
            false
        } else true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_list)

        lvResults = findViewById(R.id.lvResults)
        tvTitle = findViewById(R.id.tvTitle)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        lvResults.adapter = adapter

        if (ensurePermissionsOrRequest()) startScanInternal()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanInternal()
    }

    override fun onBackPressed() {
        stopScanInternal()
        super.onBackPressed()
    }

    private fun startScanInternal() {
        if (isScanning) return
        if (!hasAllScanPermissions()) {
            ensurePermissionsOrRequest()
            return
        }
        val adapter = btAdapter
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(this, "블루투스를 켜주세요.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val scanner = bleScanner ?: return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(null, settings, scanCallback)
            isScanning = true
            Toast.makeText(this, "스캔 시작", Toast.LENGTH_SHORT).show()
        } catch (se: SecurityException) {
            Toast.makeText(this, "권한 필요: BLUETOOTH_SCAN", Toast.LENGTH_SHORT).show()
            ensurePermissionsOrRequest()
        }
    }

    private fun stopScanInternal() {
        if (!isScanning) return
        if (!hasAllScanPermissions()) {
            isScanning = false
            return
        }
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (se: SecurityException) {
            Log.w(TAG, "stopScan SecurityException", se)
        } finally {
            isScanning = false
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            appendResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { appendResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "onScanFailed: $errorCode")
            Toast.makeText(this@ScanListActivity, "스캔 실패($errorCode)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun appendResult(r: ScanResult) {
        val sr = r.scanRecord
        val name = r.device?.name ?: sr?.deviceName ?: "N/A"
        val mac = r.device?.address ?: "??:??:??:??:??:??"
        val rssi = r.rssi

        val existed = seen.containsKey(mac)
        seen[mac] = rssi
        val line = "$name - $mac - ${rssi}dBm"
        if (!existed) {
            items.add(line)
        } else {
            val idx = items.indexOfFirst { it.contains(mac) }
            if (idx >= 0) items[idx] = line
        }
        runOnUiThread { adapter.notifyDataSetChanged() }

        val mData = sr?.manufacturerSpecificData
        var parsedBeacon = false
        var beaconCompanyId: Int? = null
        var beaconMajor: Int? = null
        var beaconMinor: Int? = null
        var beaconCustomUuidAscii: String? = null

        val manuSb = StringBuilder()
        if (mData != null && mData.size() > 0) {
            for (i in 0 until mData.size()) {
                val id = mData.keyAt(i)
                val value = mData.valueAt(i)
                manuSb.append(String.format(" [id=0x%04X data=%s]", id, value.toHex()))

                // Beacon 판별: Apple iBeacon or RFstar (BE/LE)
                if ((id == APPLE_COMPANY_ID ||
                            id == RFSTAR_COMPANY_ID_BE || id == RFSTAR_COMPANY_ID_LE) &&
                    value.size >= 23 &&
                    value[0] == 0x02.toByte() && value[1] == 0x15.toByte()
                ) {
                    val uuidBytes = value.copyOfRange(2, 18)
                    val major = ((value[18].toInt() and 0xFF) shl 8) or (value[19].toInt() and 0xFF)
                    val minor = ((value[20].toInt() and 0xFF) shl 8) or (value[21].toInt() and 0xFF)
                    val customUuidAscii = uuidBytes.toAsciiSafe()

                    // 항상 Big Endian 형태로 Company ID 표시
                    beaconCompanyId = if (id == RFSTAR_COMPANY_ID_LE) RFSTAR_COMPANY_ID_BE else id
                    beaconMajor = major
                    beaconMinor = minor
                    beaconCustomUuidAscii = customUuidAscii
                    parsedBeacon = true
                }
            }
        }

        val manuHexRaw = if (mData != null && mData.size() > 0) {
            buildString {
                for (i in 0 until mData.size()) {
                    val value = mData.valueAt(i)
                    append(value.joinToString("") { String.format("%02X", it) })
                }
            }
        } else "N/A"

        val manuAscii = if (mData != null && mData.size() > 0) {
            buildString {
                for (i in 0 until mData.size()) {
                    val value = mData.valueAt(i)
                    append(value.map { (it.toInt() and 0xFF).toChar() }
                        .joinToString("")
                        .replace(Regex("[^\\x20-\\x7E]"), "."))
                }
            }
        } else "N/A"

        val serviceUuids = sr?.serviceUuids?.joinToString { it.toString() } ?: "N/A"
        val serviceData = if (sr?.serviceData?.isNotEmpty() == true) {
            sr.serviceData.entries.joinToString { e ->
                "${e.key}=${e.value.toHex()}"
            }
        } else {
            "N/A"
        }

        if (name.startsWith("RFstar")) {
            logBlePacket(
                name, mac, rssi, serviceUuids, serviceData,
                manuSb, manuHexRaw, manuAscii, sr?.txPowerLevel,
                sr?.advertiseFlags, sr?.bytes, parsedBeacon,
                beaconCompanyId, beaconMajor, beaconMinor, beaconCustomUuidAscii
            )
        }
    }

    private fun logBlePacket(
        name: String,
        mac: String,
        rssi: Int,
        serviceUuids: String,
        serviceData: String,
        manuSb: StringBuilder,
        manuHexRaw: String,
        manuAscii: String,
        txPower: Int?,
        advFlags: Int?,
        rawBytes: ByteArray?,
        parsedBeacon: Boolean,
        beaconCompanyId: Int?,
        beaconMajor: Int?,
        beaconMinor: Int?,
        beaconCustomUuidAscii: String?
    ) {
        Log.d(TAG, """
            ---- BLE Packet ----
            Device Name       : $name
            MAC Address       : $mac
            RSSI              : $rssi
            Service UUIDs     : $serviceUuids
            Service Data      : $serviceData
            Manufacturer      : ${if (manuSb.isEmpty()) "N/A" else manuSb.toString().trim()}
            ManufacturerHex   : $manuHexRaw
            ManufacturerASCII : $manuAscii
            Tx Power          : ${txPower ?: "N/A"}
            Advertise Flags   : ${advFlags ?: "N/A"}
            Raw Bytes         : ${rawBytes?.toHex() ?: "N/A"}
            Beacon Detected   : $parsedBeacon
            --------------------
        """.trimIndent())

        if (parsedBeacon) {
            Log.d(TAG, """
                ** Beacon Extra Info **
                Company ID : 0x${String.format("%04X", beaconCompanyId)}
                Major      : ${String.format("%04d", beaconMajor)}
                Minor      : ${String.format("%04d", beaconMinor)}
                Custom UUID: $beaconCustomUuidAscii
            """.trimIndent())
        }
    }

    private fun ByteArray.toAsciiSafe(): String {
        return map { (it.toInt() and 0xFF).toChar() }
            .joinToString("")
            .replace(Regex("[^\\x20-\\x7E]"), ".")
    }

    private fun ByteArray.toHex(): String =
        joinToString(" ") { String.format("%02X", it) }
}
