package lt.smworks.activityrecognition

import android.app.Application

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        FileLogger.init(this)
    }
} 