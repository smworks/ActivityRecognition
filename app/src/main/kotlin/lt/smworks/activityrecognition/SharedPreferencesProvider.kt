package lt.smworks.activityrecognition

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SharedPreferencesProvider(context: Context) {

    companion object {
        private const val PREF_NAME = "activity_recognition_prefs"
        private const val KEY_ACTIVITY_TRACKING_ENABLED = "is_activity_tracking_enabled"
        private const val DEFAULT_ACTIVITY_TRACKING_ENABLED = false
    }

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREF_NAME,
        Context.MODE_PRIVATE
    )

    var isActivityTrackingEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_ACTIVITY_TRACKING_ENABLED, DEFAULT_ACTIVITY_TRACKING_ENABLED)
        set(value) = sharedPreferences.edit { putBoolean(KEY_ACTIVITY_TRACKING_ENABLED, value) }
}