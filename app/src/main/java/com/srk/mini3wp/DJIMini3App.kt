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

        // Listeners for product connect/disconnect — activities subscribe here
        val connectionListeners = mutableListOf<(Boolean) -> Unit>()

        fun notifyConnection(connected: Boolean) {
            connectionListeners.forEach { it(connected) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        SDKManager.getInstance().init(this, object : SDKManagerCallback {
            override fun onRegisterSuccess() {
                Log.i(TAG, "SDK registrado exitosamente")
                isSDKRegistered = true
                SDKManager.getInstance().registerApp()
            }

            override fun onRegisterFailure(error: IDJIError) {
                Log.e(TAG, "Error de registro: ${error.description()}")
                isSDKRegistered = false
            }

            override fun onProductConnect(productId: Int) {
                Log.i(TAG, "Drone conectado (id=$productId)")
                notifyConnection(true)
            }

            override fun onProductDisconnect(productId: Int) {
                Log.i(TAG, "Drone desconectado (id=$productId)")
                notifyConnection(false)
            }

            override fun onProductChanged(productId: Int) {
                Log.i(TAG, "Drone cambiado (id=$productId)")
                notifyConnection(true)
            }

            override fun onInitProcess(event: DJISDKInitEvent, totalProcess: Int) {
                Log.d(TAG, "Init: $event ($totalProcess%)")
                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    SDKManager.getInstance().registerApp()
                }
            }

            override fun onDatabaseDownloadProgress(current: Long, total: Long) {
                Log.d(TAG, "DB descarga: $current/$total")
            }
        })
    }
}
