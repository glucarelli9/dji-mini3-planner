package com.srk.mini3wp

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Guarda el stack trace del último crash en un archivo en almacenamiento externo.
 * El archivo se puede leer con adb pull o un explorador de archivos.
 *
 * Ubicación: /sdcard/Android/data/com.srk.mini3wp/files/crash_log.txt
 */
object CrashLogger {

    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)

                // Header
                pw.println("====== MINI3 PLANNER CRASH LOG ======")
                pw.println("Fecha: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                pw.println("Thread: ${thread.name}")
                pw.println("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                pw.println("======================================")
                pw.println()

                throwable.printStackTrace(pw)

                // Causas encadenadas
                var cause = throwable.cause
                while (cause != null) {
                    pw.println("\nCaused by:")
                    cause.printStackTrace(pw)
                    cause = cause.cause
                }

                pw.flush()
                val log = sw.toString()

                // Guardar en almacenamiento externo de la app
                val dir = appContext.getExternalFilesDir(null)
                    ?: appContext.filesDir  // fallback a interno
                val file = File(dir, "crash_log.txt")
                file.writeText(log)

                android.util.Log.e("CrashLogger", "Crash guardado en: ${file.absolutePath}")
                android.util.Log.e("CrashLogger", log)

            } catch (e: Exception) {
                android.util.Log.e("CrashLogger", "Error guardando crash: ${e.message}")
            }

            // Dejar que el handler default siga (muestra el diálogo de error del sistema)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
