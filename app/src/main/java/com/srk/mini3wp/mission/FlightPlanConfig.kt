package com.srk.mini3wp.mission

/**
 * Tipos de datos para configurar planes de vuelo.
 * Basados en el reverse engineering de libwpmz_jni.so:
 *   WaypointMission, WaylineMissionConfig, WaylineActionGroup
 */

data class WaypointConfig(
    val latitude: Double,
    val longitude: Double,
    val altitude: Float,                    // metros sobre punto de despegue
    val speed: Float = 5f,                  // m/s
    val heading: Float = 0f,                // grados
    val actions: List<WaypointAction> = listOf(WaypointAction.TakePhoto)
)

sealed class WaypointAction {
    object TakePhoto : WaypointAction()
    data class StartRecording(val durationSec: Int = 0) : WaypointAction()
    object StopRecording : WaypointAction()
    data class RotateGimbal(val pitchDeg: Float) : WaypointAction()
    data class HoverSeconds(val seconds: Float) : WaypointAction()
    data class RotateAircraft(val headingDeg: Float) : WaypointAction()
}

data class MissionConfig(
    val autoFlightSpeed: Float = 5f,
    val maxFlightSpeed: Float = 10f,
    val finishAction: FinishAction = FinishAction.GO_HOME,
    val rcLostAction: RCLostAction = RCLostAction.GO_HOME,
    val useAbsoluteAltitude: Boolean = false,
    val repeatTimes: Int = 1
)

enum class FinishAction { GO_HOME, HOVER, AUTO_LAND, GO_FIRST_WAYPOINT }
enum class RCLostAction { GO_HOME, HOVER, CONTINUE }
