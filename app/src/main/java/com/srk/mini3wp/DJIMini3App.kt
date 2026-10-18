package com.srk.mini3wp

import android.app.Application
import android.util.Log
import dji.v5.manager.SDKManager
import dji.v5.manager.interfaces.SDKManagerCallback
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent

class DJIMini3App : Application() {

    companion object {
        private const val TAG = "DJIMini3App"
        @Volatile var isSDKRegistered = false

        val connectionListeners = mutableListOf<(Boolean) -> Unit>()

        fun notifyConnection(connected: Boolean) {
            connectionListeners.forEach { it(connected) }
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
                    // onRegisterSuccess se dispara DESPUÉS de registerApp() — no volver a llamarlo
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
                        // Registrar la app con la API Key (una sola vez)
                        SDKManager.getInstance().registerApp()
                    }
                }

                override fun onDatabaseDownloadProgress(current: Long, total: Long) {
                    Log.d(TAG, "DB: $current/$total bytes")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "SDK init crash: ${e.message}", e)
        }
    }
}
