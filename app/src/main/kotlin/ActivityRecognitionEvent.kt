import java.io.Serializable

data class ActivityRecognitionEvent(
    val activityType: Int,
    val transitionType: Int
) : Serializable
