package lt.smworks.activityrecognition

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "routes")
data class Route(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityName: String,
    val timestamp: Long
)

@Entity(tableName = "route_points")
data class RoutePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: Long, // Foreign key to link to Route
    val latitude: Double,
    val longitude: Double,
    val pointTimestamp: Long // Optional: if you want to store timestamp for each point
)

// This class will be used to retrieve a Route with its points
data class RouteWithPoints(
    @Embedded val route: Route,
    @Relation(
        parentColumn = "id",
        entityColumn = "routeId"
    )
    val points: List<RoutePoint>
)

@Dao
interface RouteDao {
    @Insert
    suspend fun insertRoute(route: Route): Long // Return the id of the inserted route

    @Insert
    suspend fun insertRoutePoints(points: List<RoutePoint>)

    @Query("SELECT * FROM routes ORDER BY timestamp DESC")
    fun getAllRoutesWithPoints(): Flow<List<RouteWithPoints>> // Changed to get RouteWithPoints

    @Query("SELECT * FROM routes WHERE id = :routeId")
    suspend fun getRouteWithPointsById(routeId: Long): RouteWithPoints?
} 