package lt.smworks.activityrecognition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            FileLogger.d("Boot completed event received.")
            val serviceIntent = Intent(context, InVehicleForegroundService::class.java).apply {
                action = InVehicleForegroundService.ACTION_INITIALIZE_SERVICE
            }
            context.startForegroundService(serviceIntent)
        }
    }
} 