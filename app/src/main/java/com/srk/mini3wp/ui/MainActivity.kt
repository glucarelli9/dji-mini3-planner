package com.srk.mini3wp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.srk.mini3wp.DJIMini3App
import com.srk.mini3wp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    /** Listener registrado durante onResume/onPause */
    private val connectionListener: (Boolean) -> Unit = { connected ->
        runOnUiThread {
            if (connected) {
                binding.tvDroneStatus.text = "Drone conectado ✓"
                binding.btnPlanFlight.isEnabled = true
            } else {
                binding.tvDroneStatus.text = "Drone desconectado — encender el drone"
                binding.btnPlanFlight.isEnabled = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkAndRequestPermissions()
        binding.btnPlanFlight.setOnClickListener {
            startActivity(Intent(this, FlightPlanActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        DJIMini3App.connectionListeners.add(connectionListener)

        // Mostrar error de SDK si existe (ayuda a diagnosticar crash en S25/Android 15)
        val err = DJIMini3App.sdkInitError
        binding.tvSdkStatus.text = when {
            err != null -> "⚠ SDK error: $err"
            DJIMini3App.isSDKRegistered -> "SDK V5 ✓ registrado"
            else -> "SDK registrando..."
        }

        // Mostrar log de diagnóstico en consola si existe (startup_log.txt)
        try {
            val logFile = java.io.File(
                getExternalFilesDir(null) ?: filesDir,
                "startup_log.txt"
            )
            if (logFile.exists()) {
                android.util.Log.d("DJIMini", "=== STARTUP LOG ===\n${logFile.readText()}")
            }
        } catch (_: Throwable) {}

        binding.tvDroneStatus.text = "Buscando drone..."
        binding.btnPlanFlight.isEnabled = false
    }

    override fun onPause() {
        super.onPause()
        DJIMini3App.connectionListeners.remove(connectionListener)
    }

    private fun checkAndRequestPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
    }
}
