package com.nadavgu.bletestapp

import android.annotation.SuppressLint
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings

class BleScannerController(
    private val listener: Listener,
    private val bleRequirements: BleRequirements
) {

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
    private val scanFilters = listOf<ScanFilter>()

    private var scanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            listener.onScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(listener::onScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            listener.onScanFailed(errorCode)
        }
    }

    val isScanning: Boolean
        get() = scanning

    // Permission check is performed before calling scanner.startScan()
    @SuppressLint("MissingPermission")
    fun startScan(): Boolean {
        if (scanning) return false
        if (!bleRequirements.hasAllPermissions()) {
            return false
        }
        return try {
            scanner.startScan(scanFilters, scanSettings, scanCallback)
            scanning = true
            true
        } catch (e: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan(): Boolean {
        if (!scanning) return false
        return try {
            scanner.stopScan(scanCallback)
            scanning = false
            true
        } catch (e: SecurityException) {
            scanning = false
            false
        }
    }
}

