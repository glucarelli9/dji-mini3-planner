package com.srk.mini3wp.utils

import com.srk.mini3wp.mission.*
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exporta planes de vuelo al formato WPMZ de DJI.
 *
 * Reverse-engineered de libwpmz_jni.so:
 *   MarshalAllDataToKMZ(path, WaylineMission, WaylineMissionConfig,
 *                       vector<WaylineTemplate>, vector<Wayline>,
 *                       WaylineExecuteMissionConfig, vector<WaylineActionGroup>)
 *
 * Estructura del ZIP:
 *   wpmz/template.xml   → WaylineMissionConfig
 *   wpmz/waylines.wpml  → vector<Wayline> con coordenadas
 */
object WPMZExporter {

    fun export(
        outputFile: File,
        waypoints: List<WaypointConfig>,
        config: MissionConfig = MissionConfig(),
        missionName: String = "Mini3_Mission"
    ) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
            zip.putNextEntry(ZipEntry("wpmz/template.xml"))
            zip.write(buildTemplateXml(waypoints, config, missionName).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("wpmz/waylines.wpml"))
            zip.write(buildWaylinesXml(waypoints, config).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun buildTemplateXml(
        waypoints: List<WaypointConfig>,
        config: MissionConfig,
        missionName: String
    ): String {
        val finishActionStr = when (config.finishAction) {
            FinishAction.GO_HOME -> "goHome"
            FinishAction.HOVER -> "noAction"
            FinishAction.AUTO_LAND -> "autoLand"
            FinishAction.GO_FIRST_WAYPOINT -> "goFirstWaypoint"
        }
        val rcLostStr = when (config.rcLostAction) {
            RCLostAction.GO_HOME -> "goBack"
            RCLostAction.HOVER -> "hover"
            RCLostAction.CONTINUE -> "executeLostAction"
        }
        val altMode = if (config.useAbsoluteAltitude) "EGM96" else "relativeToStartPoint"

        return """<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2"
     xmlns:wpml="http://www.dji.com/wpmz/1.0.6">
  <Document>
    <wpml:author>com.srk.mini3wp</wpml:author>
    <wpml:createTime>${System.currentTimeMillis()}</wpml:createTime>
    <wpml:updateTime>${System.currentTimeMillis()}</wpml:updateTime>
    <wpml:missionConfig>
      <wpml:flyToWaylineMode>safely</wpml:flyToWaylineMode>
      <wpml:finishAction>$finishActionStr</wpml:finishAction>
      <wpml:exitOnRCLost>$rcLostStr</wpml:exitOnRCLost>
      <wpml:executeRCLostAction>$rcLostStr</wpml:executeRCLostAction>
      <wpml:globalTransitionalSpeed>${config.autoFlightSpeed}</wpml:globalTransitionalSpeed>
      <wpml:droneInfo>
        <!-- DJI Mini 3 / WM265 droneEnumValue=77 (RE de WaylineDroneType en WPMZ SDK)
             Mini 2 SE no tiene entrada propia en WaylineDroneType - usar WM265 como fallback.
             NOTA: Mini 2 SE no soporta misiones WPMZ nativamente según DJI MSDK V5. -->
        <wpml:droneEnumValue>77</wpml:droneEnumValue>
        <wpml:droneSubEnumValue>2</wpml:droneSubEnumValue>
      </wpml:droneInfo>
    </wpml:missionConfig>
    <Folder>
      <wpml:templateId>0</wpml:templateId>
      <wpml:executeHeightMode>$altMode</wpml:executeHeightMode>
      <wpml:waylineCoordinateSysParam>
        <wpml:coordinateMode>WGS84</wpml:coordinateMode>
        <wpml:heightMode>$altMode</wpml:heightMode>
      </wpml:waylineCoordinateSysParam>
      <wpml:autoFlightSpeed>${config.autoFlightSpeed}</wpml:autoFlightSpeed>
      ${waypoints.mapIndexed { i, wp -> buildPlacemark(i, wp) }.joinToString("\n      ")}
    </Folder>
  </Document>
</kml>"""
    }

    private fun buildPlacemark(index: Int, wp: WaypointConfig): String {
        val actionsXml = if (wp.actions.isNotEmpty()) """
        <wpml:actionGroup>
          <wpml:actionGroupId>$index</wpml:actionGroupId>
          <wpml:actionGroupStartIndex>$index</wpml:actionGroupStartIndex>
          <wpml:actionGroupEndIndex>$index</wpml:actionGroupEndIndex>
          <wpml:actionGroupMode>sequence</wpml:actionGroupMode>
          <wpml:actionTrigger>
            <wpml:actionTriggerType>reachPoint</wpml:actionTriggerType>
          </wpml:actionTrigger>
          ${wp.actions.mapIndexed { ai, a -> buildActionXml(ai, a) }.joinToString("\n          ")}
        </wpml:actionGroup>""" else ""

        return """<Placemark>
        <Point><coordinates>${wp.longitude},${wp.latitude},${wp.altitude}</coordinates></Point>
        <wpml:index>$index</wpml:index>
        <wpml:executeHeight>${wp.altitude}</wpml:executeHeight>
        <wpml:waypointSpeed>${wp.speed}</wpml:waypointSpeed>
        <wpml:waypointHeadingParam>
          <wpml:waypointHeadingMode>followWayline</wpml:waypointHeadingMode>
          <wpml:waypointHeadingAngle>${wp.heading.toInt()}</wpml:waypointHeadingAngle>
        </wpml:waypointHeadingParam>
        <wpml:waypointTurnParam>
          <wpml:waypointTurnMode>toPointAndStopWithDiscontinuityCurvature</wpml:waypointTurnMode>
          <wpml:waypointTurnDampingDist>0.2</wpml:waypointTurnDampingDist>
        </wpml:waypointTurnParam>
        $actionsXml
      </Placemark>"""
    }

    private fun buildActionXml(idx: Int, action: WaypointAction) = when (action) {
        is WaypointAction.TakePhoto -> """
          <wpml:action><wpml:actionId>$idx</wpml:actionId>
            <wpml:actionActuatorFunc>takePhoto</wpml:actionActuatorFunc>
            <wpml:actionActuatorFuncParam><wpml:payloadPositionIndex>0</wpml:payloadPositionIndex></wpml:actionActuatorFuncParam>
          </wpml:action>"""
        is WaypointAction.StartRecording -> """
          <wpml:action><wpml:actionId>$idx</wpml:actionId>
            <wpml:actionActuatorFunc>startRecord</wpml:actionActuatorFunc>
            <wpml:actionActuatorFuncParam><wpml:payloadPositionIndex>0</wpml:payloadPositionIndex></wpml:actionActuatorFuncParam>
          </wpml:action>"""
        is WaypointAction.StopRecording -> """
          <wpml:action><wpml:actionId>$idx</wpml:actionId>
            <wpml:actionActuatorFunc>stopRecord</wpml:actionActuatorFunc>
            <wpml:actionActuatorFuncParam><wpml:payloadPositionIndex>0</wpml:payloadPositionIndex></wpml:actionActuatorFuncParam>
          </wpml:action>"""
        is WaypointAction.RotateGimbal -> """
          <wpml:action><wpml:actionId>$idx</wpml:actionId>
            <wpml:actionActuatorFunc>gimbalRotate</wpml:actionActuatorFunc>
            <wpml:actionActuatorFuncParam>
              <wpml:gimbalRotateMode>absoluteAngle</wpml:gimbalRotateMode>
              <wpml:gimbalPitchRotateEnable>1</wpml:gimbalPitchRotateEnable>
              <wpml:gimbalPitchRotateAngle>${action.pitchDeg}</wpml:gimbalPitchRotateAngle>
              <wpml:payloadPositionIndex>0</wpml:payloadPositionIndex>
            </wpml:actionActuatorFuncParam>
          </wpml:action>"""
        is WaypointAction.HoverSeconds -> """
          <wpml:action><wpml:actionId>$idx</wpml:actionId>
            <wpml:actionActuatorFunc>hover</wpml:actionActuatorFunc>
            <wpml:actionActuatorFuncParam><wpml:hoverTime>${(action.seconds * 1000).toInt()}</wpml:hoverTime></wpml:actionActuatorFuncParam>
          </wpml:action>"""
        is WaypointAction.RotateAircraft -> """
          <wpml:action><wpml:actionId>$idx</wpml:actionId>
            <wpml:actionActuatorFunc>rotateYaw</wpml:actionActuatorFunc>
            <wpml:actionActuatorFuncParam>
              <wpml:aircraftHeading>${action.headingDeg.toInt()}</wpml:aircraftHeading>
              <wpml:aircraftPathMode>clockwise</wpml:aircraftPathMode>
            </wpml:actionActuatorFuncParam>
          </wpml:action>"""
    }

    private fun buildWaylinesXml(waypoints: List<WaypointConfig>, config: MissionConfig): String {
        val altMode = if (config.useAbsoluteAltitude) "EGM96" else "relativeToStartPoint"
        val placemarks = waypoints.mapIndexed { i, wp ->
            """    <Placemark>
      <Point><coordinates>${wp.longitude},${wp.latitude},${wp.altitude}</coordinates></Point>
      <wpml:index>$i</wpml:index>
      <wpml:executeHeight>${wp.altitude}</wpml:executeHeight>
      <wpml:waypointSpeed>${wp.speed}</wpml:waypointSpeed>
    </Placemark>"""
        }.joinToString("\n")

        return """<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2"
     xmlns:wpml="http://www.dji.com/wpmz/1.0.6">
  <Document>
    <Folder>
      <wpml:templateId>0</wpml:templateId>
      <wpml:waylineId>0</wpml:waylineId>
      <wpml:executeHeightMode>$altMode</wpml:executeHeightMode>
      <wpml:autoFlightSpeed>${config.autoFlightSpeed}</wpml:autoFlightSpeed>
$placemarks
    </Folder>
  </Document>
</kml>"""
    }
}
