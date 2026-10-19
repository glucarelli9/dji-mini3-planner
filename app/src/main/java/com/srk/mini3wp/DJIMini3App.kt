package com.srk.mini3wp

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.multidex.MultiDexApplication
import dji.v5.manager.SDKManager
import dji.v5.manager.interfaces.SDKManagerCallback
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent

class DJIMini3App : MultiDexApplication() {

    companion object {
        private const val TAG = "DJIMini3App"
        @Volatile var isSDKRegistered = false
        @Volatile var sdkInitError: String? = null

        val connectionListeners = mutableListOf<(Boolean) -> Unit>()

        fun notifyConnection(connected: Boolean) {
            connectionListeners.forEach { it(connected) }
        }
    }

    /**
     * CRÍTICO: El DJI SDK V5 carga sus clases en memoria desde libSdkyclx_clx.so.
     * Helper.install() debe llamarse en attachBaseContext, ANTES de que el sistema
     * intente resolver cualquier clase del SDK.
     *
     * CrashLogger se instala PRIMERO para capturar crashes en threads del SDK
     * que puedan iniciarse durante o después de Helper.install().
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        // Instalar CrashLogger ANTES de Helper.install() para capturar
        // cualquier crash en threads de background del SDK desde el inicio.
        CrashLogger.install(this)

        // Log de diagnóstico muy temprano (para S25/Android 15 debugging)
        writeDiagnosticLog("attachBaseContext: Android ${Build.VERSION.RELEASE} " +
                "(SDK ${Build.VERSION.SDK_INT}) ${Build.MANUFACTURER} ${Build.MODEL}")

        try {
            writeDiagnosticLog("Helper.install: iniciando...")
            com.cySdkyc.clx.Helper.install(this)
            writeDiagnosticLog("Helper.install: OK")
        } catch (e: Throwable) {
            val msg = "Helper.install falló: ${e.javaClass.simpleName}: ${e.message}"
            writeDiagnosticLog(msg)
            Log.e(TAG, msg)
        }
    }

    override fun onCreate() {
        super.onCreate()
        writeDiagnosticLog("onCreate: iniciando SDKManager")

        try {
            SDKManager.getInstance().init(this, object : SDKManagerCallback {
                override fun onRegisterSuccess() {
                    Log.i(TAG, "SDK registrado OK")
                    isSDKRegistered = true
                    writeDiagnosticLog("SDK: registro exitoso")
                }

                override fun onRegisterFailure(error: IDJIError) {
                    val msg = "SDK registro fallido: ${error.description()}"
                    Log.e(TAG, msg)
                    sdkInitError = msg
                    writeDiagnosticLog(msg)
                }

                override fun onProductConnect(productId: Int) {
                    Log.i(TAG, "Drone conectado id=$productId")
                    writeDiagnosticLog("Drone conectado id=$productId")
                    notifyConnection(true)
                }

                override fun onProductDisconnect(productId: Int) {
                    Log.i(TAG, "Drone desconectado id=$productId")
                    notifyConnection(false)
                }

                override fun onProductChanged(productId: Int) {
                    Log.i(TAG, "Drone cambiado id=$productId")
                    notifyConnection(true)
                }

                override fun onInitProcess(event: DJISDKInitEvent, totalProcess: Int) {
                    Log.d(TAG, "Init evento=$event progreso=$totalProcess")
                    writeDiagnosticLog("SDK init: $event $totalProcess%")
                    if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                        writeDiagnosticLog("SDK: iniciando registerApp()")
                        SDKManager.getInstance().registerApp()
                    }
                }

                override fun onDatabaseDownloadProgress(current: Long, total: Long) {
                    Log.d(TAG, "DB: $current/$total bytes")
                }
            })
            writeDiagnosticLog("SDKManager.init(): llamado OK")
        } catch (e: Throwable) {
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            val msg = "SDK init crash: ${e.javaClass.name}: ${e.message}"
            Log.e(TAG, msg, e)
            sdkInitError = msg
            writeDiagnosticLog("$msg\n$sw")

            try {
                val f = java.io.File(getExternalFilesDir(null) ?: filesDir, "sdk_init_error.txt")
                f.writeText("SDK_INIT_ERROR: ${e.javaClass.name}\n$sw")
            } catch (_: Throwable) {}
        }
    }

    /**
     * Escribe un log de diagnóstico en /sdcard/.../files/startup_log.txt
     * sin depender del SDK para facilitar debugging en S25/Android 15.
     */
    private fun writeDiagnosticLog(msg: String) {
        try {
            val dir = getExternalFilesDir(null) ?: filesDir
            val f = java.io.File(dir, "startup_log.txt")
            val timestamp = System.currentTimeMillis()
            f.appendText("[$timestamp] $msg\n")
        } catch (_: Throwable) {}
    }
}
