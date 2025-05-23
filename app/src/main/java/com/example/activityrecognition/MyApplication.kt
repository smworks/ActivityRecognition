package com.example.activityrecognition

import android.app.Application

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        FileLogger.init(this)
        FileLogger.d("Application created and FileLogger initialized.")
    }
} 