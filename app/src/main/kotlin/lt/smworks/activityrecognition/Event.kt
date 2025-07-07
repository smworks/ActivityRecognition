package lt.smworks.activityrecognition

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val value: String,
    val activity: String?
) {
    override fun toString(): String {
        return "${timestamp.toTime()}: $value"
    }
}