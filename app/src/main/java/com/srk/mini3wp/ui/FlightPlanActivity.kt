package com.srk.mini3wp.ui

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.srk.mini3wp.DJIMini3App
import com.srk.mini3wp.R
import com.srk.mini3wp.databinding.ActivityFlightPlanBinding
import com.srk.mini3wp.mission.*
import com.srk.mini3wp.utils.WPMZExporter
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.waypoint3.WaypointMissionExecuteStateListener
import dji.v5.manager.aircraft.waypoint3.WaypointMissionManager
import dji.v5.manager.aircraft.waypoint3.model.WaypointMissionExecuteState
import kotlinx.coroutines.launch
import java.io.File

class FlightPlanActivity : AppCompatActivity(), OnMapReadyCallback {

    private val TAG = "FlightPlanActivity"
    private lateinit var binding: ActivityFlightPlanBinding
    private lateinit var map: GoogleMap

    private val waypoints = mutableListOf<WaypointConfig>()
    private val markers = mutableListOf<Marker>()
    private var polyline: Polyline? = null

    private var currentAltitude = 30f
    private var currentSpeed = 5f

    /** ID de la misión activa (nombre del WPMZ sin extensión) */
    private var activeMissionId: String? = null

    private val missionManager by lazy { WaypointMissionManager.getInstance() }

    private val executeStateListener = WaypointMissionExecuteStateListener { state ->
        val label = when (state) {
            WaypointMissionExecuteState.IDLE        -> "En espera"
            WaypointMissionExecuteState.READY       -> "Listo para volar"
            WaypointMissionExecuteState.UPLOADING   -> "Subiendo misión..."
            WaypointMissionExecuteState.PREPARING   -> "Preparando..."
            WaypointMissionExecuteState.ENTER_WAYLINE -> "Entrando wayline"
            WaypointMissionExecuteState.EXECUTING   -> "Volando ✈"
            WaypointMissionExecuteState.INTERRUPTED -> "Interrumpido"
            WaypointMissionExecuteState.RECOVERING  -> "Recuperando..."
            WaypointMissionExecuteState.FINISHED    -> "Misión completada ✓"
            WaypointMissionExecuteState.RETURN_TO_START_POINT -> "Regresando al inicio"
            WaypointMissionExecuteState.DISCONNECTED -> "Desconectado"
            WaypointMissionExecuteState.NOT_SUPPORTED -> "No soportado"
            else -> state.name
        }
        runOnUiThread { binding.tvMissionStatus.text = "Estado: $label" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFlightPlanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupMap()
        setupControls()
    }

    override fun onResume() {
        super.onResume()
        missionManager.addWaypointMissionExecuteStateListener(executeStateListener)
    }

    override fun onPause() {
        super.onPause()
        missionManager.removeWaypointMissionExecuteStateListener(executeStateListener)
    }

    private fun setupMap() {
        (supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment)
            .getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap.apply {
            mapType = GoogleMap.MAP_TYPE_SATELLITE
            setOnMapClickListener { latLng -> addWaypoint(latLng.latitude, latLng.longitude) }
            setOnMarkerClickListener { marker -> removeWaypoint(marker); true }
        }
    }

    private fun addWaypoint(lat: Double, lng: Double) {
        val wp = WaypointConfig(
            latitude = lat, longitude = lng,
            altitude = currentAltitude, speed = currentSpeed,
            actions = listOf(WaypointAction.TakePhoto)
        )
        waypoints.add(wp)
        map.addMarker(
            MarkerOptions()
                .position(LatLng(lat, lng))
                .title("WP ${waypoints.size}")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
        )?.let { markers.add(it) }
        updatePolyline()
        binding.tvWaypointCount.text = "Waypoints: ${waypoints.size}"
    }

    private fun removeWaypoint(marker: Marker) {
        val i = markers.indexOf(marker)
        if (i >= 0) {
            waypoints.removeAt(i)
            markers.removeAt(i)
            marker.remove()
            updatePolyline()
        }
        binding.tvWaypointCount.text = "Waypoints: ${waypoints.size}"
    }

    private fun updatePolyline() {
        polyline?.remove()
        if (waypoints.size < 2) return
        polyline = map.addPolyline(
            PolylineOptions()
                .addAll(waypoints.map { LatLng(it.latitude, it.longitude) })
                .color(android.graphics.Color.parseColor("#FF6B35"))
                .width(4f)
        )
    }

    private fun setupControls() {
        binding.sliderAltitude.addOnChangeListener { _, v, _ ->
            currentAltitude = v
            binding.tvAltitude.text = "Altitud: ${v.toInt()}m"
        }
        binding.sliderSpeed.addOnChangeListener { _, v, _ ->
            currentSpeed = v
            binding.tvSpeed.text = "Vel: ${v.toInt()} m/s"
        }
        binding.btnFly.setOnClickListener {
            if (!DJIMini3App.isSDKRegistered) { toast("SDK no registrado"); return@setOnClickListener }
            if (waypoints.size < 2) { toast("Agregar al menos 2 waypoints"); return@setOnClickListener }
            uploadAndFly()
        }
        binding.btnExportWpmz.setOnClickListener { exportToWpmz() }
        binding.btnStop.setOnClickListener { stopActiveMission() }
        binding.btnClear.setOnClickListener {
            waypoints.clear()
            markers.forEach { it.remove() }
            markers.clear()
            polyline?.remove()
            binding.tvWaypointCount.text = "Waypoints: 0"
        }
    }

    private fun uploadAndFly() {
        val file = File(getExternalFilesDir(null), "mini3_${System.currentTimeMillis()}.wpmz")
        val config = MissionConfig(autoFlightSpeed = currentSpeed, maxFlightSpeed = 15f)

        binding.btnFly.isEnabled = false
        binding.tvMissionStatus.text = "Estado: Generando WPMZ..."
        toast("Subiendo misión al Mini 3...")

        lifecycleScope.launch {
            val result = uploadAndStartWaypointMission(file, waypoints, config)
            runOnUiThread {
                binding.btnFly.isEnabled = true
                result.fold(
                    onSuccess = { missionId ->
                        activeMissionId = missionId
                        Log.i(TAG, "Misión activa: $missionId")
                        toast("¡Misión iniciada! ${waypoints.size} waypoints")
                    },
                    onFailure = { e ->
                        toast("Error: ${e.message}")
                        binding.tvMissionStatus.text = "Estado: Error"
                        Log.e(TAG, "uploadAndFly fallido", e)
                    }
                )
            }
        }
    }

    private fun stopActiveMission() {
        val id = activeMissionId
        if (id == null) {
            toast("No hay misión activa")
            return
        }
        missionManager.stopMission(
            id,
            object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    activeMissionId = null
                    runOnUiThread { toast("Misión detenida") }
                }
                override fun onFailure(error: IDJIError) {
                    runOnUiThread { toast("Error al detener: ${error.description()}") }
                }
            }
        )
    }

    private fun exportToWpmz() {
        if (waypoints.size < 2) { toast("Agregar al menos 2 waypoints"); return }
        val file = File(getExternalFilesDir(null), "mini3_export_${System.currentTimeMillis()}.wpmz")
        try {
            WPMZExporter.export(file, waypoints, MissionConfig(autoFlightSpeed = currentSpeed))
            toast("Exportado: ${file.name}")
            Log.i(TAG, "WPMZ: ${file.absolutePath}")
        } catch (e: Exception) {
            toast("Error exportando: ${e.message}")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
