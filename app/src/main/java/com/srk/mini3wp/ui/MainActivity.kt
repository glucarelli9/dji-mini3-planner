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
                binding.tvDroneStatus.text = "Mini 3 conectado ✓"
                binding.btnPlanFlight.isEnabled = true
            } else {
                binding.tvDroneStatus.text = "Desconectado — encender el Mini 3"
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
        binding.tvSdkStatus.text =
            if (DJIMini3App.isSDKRegistered) "SDK V5 ✓ registrado" else "SDK registrando..."
        // Estado inicial — sin query al SDK (API no lo expone en V5.15 sin KeyManager completo)
        binding.tvDroneStatus.text = "Buscando Mini 3..."
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
