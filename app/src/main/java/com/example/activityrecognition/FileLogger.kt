package com.example.activityrecognition

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {

    private const val LOG_FILE_NAME = "log.txt"
    private const val LOGCAT_TAG = "ActivityRecognition" // Hardcoded tag for Logcat
    private var applicationContext: Context? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun init(context: Context) {
        applicationContext = context.applicationContext // Store application context
    }

    private fun logToFile(message: String) {
        applicationContext?.let { ctx ->
            try {
                val logFile = File(ctx.filesDir, LOG_FILE_NAME)
                val logMessage = "${dateFormat.format(Date())} - $message\n"
                logFile.appendText(logMessage)
            } catch (e: IOException) {
                Log.e(LOGCAT_TAG, "Error writing to log file", e)
            }
        } ?: run {
            Log.e(LOGCAT_TAG, "Context not initialized. Call FileLogger.init(context) first.")
        }
    }

    fun d(message: String) {
        Log.d(LOGCAT_TAG, message)
        logToFile(message)
    }

    fun e(message: String) {
        Log.e(LOGCAT_TAG, message)
        logToFile(message)
    }

    fun e(message: String, throwable: Throwable?) {
        val fullMessage = throwable?.let { "$message: ${it.localizedMessage}" } ?: message
        Log.e(LOGCAT_TAG, fullMessage, throwable)
        logToFile(fullMessage)
    }

    fun w(message: String) {
        Log.w(LOGCAT_TAG, message)
        logToFile(message)
    }

    fun w(message: String, throwable: Throwable?) {
        val fullMessage = throwable?.let { "$message: ${it.localizedMessage}" } ?: message
        Log.w(LOGCAT_TAG, fullMessage, throwable)
        logToFile(fullMessage)
    }

    fun i(message: String) {
        Log.i(LOGCAT_TAG, message)
        logToFile(message)
    }

    fun v(message: String) {
        Log.v(LOGCAT_TAG, message)
        logToFile(message)
    }

    fun getLog(): String? {
        return applicationContext?.let { ctx ->
            val logFile = File(ctx.filesDir, LOG_FILE_NAME)
            try {
                if (logFile.exists()) {
                    return@let logFile.readText()
                } else {
                    Log.w(LOGCAT_TAG, "Log file does not exist.")
                    return@let null
                }
            } catch (e: IOException) {
                Log.e(LOGCAT_TAG, "Error reading log file", e)
                return@let null
            }
        } ?: run {
            Log.e(LOGCAT_TAG, "Context not initialized. Call FileLogger.init(context) first.")
            null
        }
    }
}