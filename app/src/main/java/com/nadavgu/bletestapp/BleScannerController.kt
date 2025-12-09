package com.nadavgu.bletestapp

import android.util.Log
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings

class BleScannerController(
    private val listener: Listener,
    private val bleRequirements: BleRequirements
) {
    companion object {
        private const val TAG = "BleScannerController"
    }

    interface Listener {
        fun onScanResult(result: ScanResult)
        fun onScanFailed(errorCode: Int)
    }

    private val scanner by lazy { BluetoothLeScannerCompat.getScanner() }
    private val scanSettings by lazy {
        ScanSettings.Builder()
            .setReportDelay(0L)
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
    }
    private var scanFilters = listOf<ScanFilter>()

    private var scanning = false
    
    fun setScanFilters(filters: List<ScanFilter>) {
        if (scanning) {
            Log.w(TAG, "setScanFilters: Cannot change filters while scanning")
            return
        }
        Log.d(TAG, "setScanFilters: Setting ${filters.size} scan filters")
        scanFilters = filters
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val rssi = result.rssi
            Log.v(TAG, "onScanResult: ${device.address} (RSSI: $rssi)")
            listener.onScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            Log.d(TAG, "onBatchScanResults: Received ${results.size} results")
            results.forEach(listener::onScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "onScanFailed: errorCode=$errorCode")
            scanning = false
            listener.onScanFailed(errorCode)
        }
    }

    val isScanning: Boolean
        get() = scanning

    fun startScan(): Boolean {
        if (scanning) {
            Log.w(TAG, "startScan: Already scanning")
            return false
        }
        if (!bleRequirements.hasAllPermissions()) {
            Log.w(TAG, "startScan: Missing permissions")
            return false
        }
        return try {
            Log.i(TAG, "startScan: Starting BLE scan")
            scanner.startScan(scanFilters, scanSettings, scanCallback)
            scanning = true
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "startScan: SecurityException", e)
            false
        }
    }

    fun stopScan(): Boolean {
        if (!scanning) {
            Log.w(TAG, "stopScan: Not scanning")
            return false
        }
        return try {
            Log.i(TAG, "stopScan: Stopping BLE scan")
            scanner.stopScan(scanCallback)
            scanning = false
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "stopScan: SecurityException", e)
            scanning = false
            false
        }
    }
}

