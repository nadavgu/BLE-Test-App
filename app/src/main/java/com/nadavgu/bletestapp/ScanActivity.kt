package com.nadavgu.bletestapp

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import no.nordicsemi.android.support.v18.scanner.ScanResult

class ScanActivity : AppCompatActivity(), BleScannerController.Listener {

    private lateinit var statusText: TextView
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var emptyStateCard: MaterialCardView
    private lateinit var toggleScanButton: MaterialButton
    private lateinit var recyclerView: RecyclerView

    private val scanResults = linkedMapOf<String, ScannedDevice>()
    private val resultsAdapter = ScanResultAdapter()

    private lateinit var bleRequirements: BleRequirements
    private lateinit var scannerController: BleScannerController

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grantResults ->
            val allGranted = grantResults.values.all { it }
            if (allGranted) {
                ensureBluetoothEnabledAndScan()
            } else {
                showPermissionDenied()
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (bleRequirements.isBluetoothEnabled()) {
                startBleScan()
            } else {
                showBluetoothRequired()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        bleRequirements = BleRequirements(this)
        scannerController = BleScannerController(this, bleRequirements)
        bindViews()
        configureToolbar()
        configureRecyclerView()
        updateUiForScanState()
        toggleScanButton.setOnClickListener {
            if (scannerController.isScanning) {
                stopBleScan()
            } else {
                onStartScanClicked()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Automatically prompt for permissions so the user can scan right away.
        if (!scannerController.isScanning) {
            onStartScanClicked()
        }
    }

    override fun onStop() {
        super.onStop()
        stopBleScan()
    }

    private fun bindViews() {
        statusText = findViewById(R.id.scanStatusText)
        progressIndicator = findViewById(R.id.scanProgressIndicator)
        emptyStateCard = findViewById(R.id.emptyStateCard)
        toggleScanButton = findViewById(R.id.toggleScanButton)
        recyclerView = findViewById(R.id.scanResultsRecyclerView)
    }

    private fun configureToolbar() {
        val toolbar: MaterialToolbar = findViewById(R.id.scanToolbar)
        setSupportActionBar(toolbar)
    }

    private fun configureRecyclerView() {
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ScanActivity)
            adapter = resultsAdapter
            addItemDecoration(
                DividerItemDecoration(
                    context,
                    DividerItemDecoration.VERTICAL
                )
            )
        }
    }

    private fun onStartScanClicked() {
        when {
            !bleRequirements.isBleSupported() -> {
                Snackbar.make(
                    recyclerView,
                    R.string.scan_error_ble_not_supported,
                    Snackbar.LENGTH_LONG
                ).setAction(R.string.scan_settings_button) {
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }.show()
            }

            !bleRequirements.hasAllPermissions() -> {
                permissionLauncher.launch(bleRequirements.requiredRuntimePermissions())
            }

            !bleRequirements.isBluetoothEnabled() -> {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }

            else -> startBleScan()
        }
    }

    private fun ensureBluetoothEnabledAndScan() {
        if (bleRequirements.isBluetoothEnabled()) {
            startBleScan()
        } else {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    private fun startBleScan() {
        if (!bleRequirements.hasAllPermissions()) {
            onStartScanClicked()
            return
        }
        if (!scannerController.startScan()) {
            // If startScan returns false, it might be due to permissions
            if (!bleRequirements.hasAllPermissions()) {
                onStartScanClicked()
            }
            return
        }
        scanResults.clear()
        resultsAdapter.submitDevices(emptyList())
        updateUiForScanState()
    }

    private fun stopBleScan() {
        scannerController.stopScan()
        updateUiForScanState()
    }

    private fun updateUiForScanState() {
        val scanning = scannerController.isScanning
        progressIndicator.isVisible = scanning
        toggleScanButton.apply {
            isEnabled = true
            text = if (scanning) {
                getString(R.string.scan_stop_button)
            } else {
                getString(R.string.scan_start_button)
            }
        }
        statusText.text = if (scanning) {
            getString(R.string.scan_status_scanning)
        } else {
            getString(R.string.scan_status_idle)
        }
        emptyStateCard.isVisible = scanResults.isEmpty() && !scanning
    }

    private fun showPermissionDenied() {
        Snackbar.make(
            recyclerView,
            R.string.scan_error_permission_denied,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.scan_open_settings_button) {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", packageName, null)
            )
            startActivity(intent)
        }.show()
    }

    private fun showBluetoothRequired() {
        Snackbar.make(
            recyclerView,
            R.string.scan_error_bluetooth_disabled,
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun addOrUpdateResult(result: ScanResult) {
        val device = result.device
        val name = try {
            device.name ?: result.scanRecord?.deviceName ?: getString(R.string.scan_unknown_device)
        } catch (_: SecurityException) {
            result.scanRecord?.deviceName ?: getString(R.string.scan_unknown_device)
        }
        val scannedDevice = ScannedDevice(
            address = device.address,
            name = name,
            rssi = result.rssi,
            isConnectable = result.isConnectable,
            lastSeen = System.currentTimeMillis()
        )
        scanResults[device.address] = scannedDevice
        resultsAdapter.submitDevices(scanResults.values.toList())
        emptyStateCard.isVisible = scanResults.isEmpty() && !scannerController.isScanning
    }

    override fun onScanResult(result: ScanResult) {
        runOnUiThread { addOrUpdateResult(result) }
    }

    override fun onScanFailed(errorCode: Int) {
        runOnUiThread {
            updateUiForScanState()
            Snackbar.make(
                recyclerView,
                getString(R.string.scan_error_generic, errorCode),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }
}

