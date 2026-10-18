package com.srk.mini3wp

import android.content.Context
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

        val connectionListeners = mutableListOf<(Boolean) -> Unit>()

        fun notifyConnection(connected: Boolean) {
            connectionListeners.forEach { it(connected) }
        }
    }

    /**
     * CRÍTICO: El DJI SDK V5 carga sus clases en memoria desde libSdkyclx_clx.so.
     * Helper.install() debe llamarse en attachBaseContext, ANTES de que el sistema
     * intente resolver cualquier clase del SDK. Sin esto, SDKManager y todas las
     * clases del SDK quedan sin resolver (ClassNotFoundException en runtime).
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        try {
            com.cySdkyc.clx.Helper.install(this)
        } catch (e: Throwable) {
            // En emuladores x86_64 falla (libSdkyclx_clx.so es arm64-only).
            // En dispositivos arm64 reales debe tener éxito.
            Log.e(TAG, "Helper.install falló (¿x86_64?): ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Instalar crash logger PRIMERO para capturar cualquier fallo
        CrashLogger.install(this)
        try {
            SDKManager.getInstance().init(this, object : SDKManagerCallback {
                override fun onRegisterSuccess() {
                    Log.i(TAG, "SDK registrado OK")
                    isSDKRegistered = true
                }

                override fun onRegisterFailure(error: IDJIError) {
                    Log.e(TAG, "Registro fallido: ${error.description()}")
                    isSDKRegistered = false
                }

                override fun onProductConnect(productId: Int) {
                    Log.i(TAG, "Drone conectado id=$productId")
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
                    if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                        SDKManager.getInstance().registerApp()
                    }
                }

                override fun onDatabaseDownloadProgress(current: Long, total: Long) {
                    Log.d(TAG, "DB: $current/$total bytes")
                }
            })
        } catch (e: Throwable) {
            // Captura TANTO Exception como Error (NoClassDefFoundError, UnsatisfiedLinkError, etc.)
            Log.e(TAG, "SDK init crash: ${e.javaClass.simpleName}: ${e.message}", e)
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                val f = java.io.File(getExternalFilesDir(null) ?: filesDir, "sdk_init_error.txt")
                f.writeText("SDK_INIT_ERROR: ${e.javaClass.name}\n${sw}")
            } catch (_: Throwable) {}
        }
    }
}
