package com.srk.mini3wp.mission

import android.util.Log
import com.srk.mini3wp.utils.WPMZExporter
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.waypoint3.WaypointMissionManager
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

private const val TAG = "WaypointMissionHelper"

/**
 * Genera el archivo WPMZ, lo sube al drone y arranca la misión.
 * Devuelve el missionId (nombre del archivo sin extensión) en caso de éxito.
 *
 * API real V5.15.0:
 *   pushKMZFileToAircraft(localPath, CompletionCallbackWithProgress<Double>)
 *   startMission(missionId, CompletionCallback)
 */
suspend fun uploadAndStartWaypointMission(
    outputFile: File,
    waypoints: List<WaypointConfig>,
    config: MissionConfig = MissionConfig()
): Result<String> {
    // Paso 1: generar el WPMZ
    try {
        WPMZExporter.export(outputFile, waypoints, config)
    } catch (e: Exception) {
        return Result.failure(Exception("Error generando WPMZ: ${e.message}"))
    }

    val missionId = outputFile.nameWithoutExtension
    val manager = WaypointMissionManager.getInstance()

    // Paso 2: subir al drone
    val pushResult = suspendCancellableCoroutine { cont ->
        manager.pushKMZFileToAircraft(
            outputFile.absolutePath,
            object : CommonCallbacks.CompletionCallbackWithProgress<Double> {
                override fun onProgressUpdate(progress: Double) {
                    Log.d(TAG, "Upload: %.0f%%".format(progress * 100))
                }
                override fun onSuccess() {
                    Log.i(TAG, "WPMZ subido al Mini 3")
                    cont.resume(null as IDJIError?)
                }
                override fun onFailure(error: IDJIError) {
                    Log.e(TAG, "Error subiendo WPMZ: ${error.description()}")
                    cont.resume(error)
                }
            }
        )
    }
    if (pushResult != null) {
        return Result.failure(Exception("Upload fallido: ${pushResult.description()}"))
    }

    // Paso 3: iniciar misión
    val startResult = suspendCancellableCoroutine { cont ->
        manager.startMission(
            missionId,
            object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    Log.i(TAG, "Misión iniciada: $missionId")
                    cont.resume(null as IDJIError?)
                }
                override fun onFailure(error: IDJIError) {
                    Log.e(TAG, "Error iniciando misión: ${error.description()}")
                    cont.resume(error)
                }
            }
        )
    }
    return if (startResult == null) Result.success(missionId)
    else Result.failure(Exception("Start fallido: ${startResult.description()}"))
}
