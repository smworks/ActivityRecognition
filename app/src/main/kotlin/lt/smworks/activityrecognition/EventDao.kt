package lt.smworks.activityrecognition

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert
    suspend fun addEvent(event: Event)

    @Query("SELECT * FROM events ORDER BY timestamp DESC")
    fun getEvents(): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE activity IS NOT NULL ORDER BY timestamp DESC LIMIT 1")
    fun getLastActivity(): Flow<Event?>
}