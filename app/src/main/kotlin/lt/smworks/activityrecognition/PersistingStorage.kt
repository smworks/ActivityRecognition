package lt.smworks.activityrecognition

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PersistingStorage(private val context: Context) {

    private val database by lazy { AppDatabase.getDatabase(context) }

    val eventObservable = database.eventDao().getEvents()
    val activityObservable = database.eventDao().getLastActivity().map { it?.activity ?: "No activity detected" }

    @OptIn(DelicateCoroutinesApi::class)
    fun storeEvent(event: String, activity: String? = null) {
        GlobalScope.launch {
            database.eventDao().addEvent(Event(
                timestamp = System.currentTimeMillis(),
                value = event,
                activity = activity
            ))
        }
    }

    suspend fun getCurrentActivity(): String {
        return database.eventDao().getLastActivity().firstOrNull()?.activity ?: "No activity detected"
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun saveRouteToDatabase(routeLatLngs: List<LatLng>, activityName: String) {
        if (routeLatLngs.isEmpty()) {
            FileLogger.w("Attempted to save an empty route.")
            return
        }
        GlobalScope.launch {
            val route = Route(
                activityName = activityName,
                timestamp = System.currentTimeMillis()
            )
            val routeId = database.routeDao().insertRoute(route)

            val routePoints = routeLatLngs.map {
                RoutePoint(
                    routeId = routeId,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    pointTimestamp = System.currentTimeMillis() // Or a more specific timestamp if available
                )
            }
            database.routeDao().insertRoutePoints(routePoints)
            FileLogger.i("Route saved to database for activity: $activityName")
        }
    }

    fun getAllRoutesWithPoints(): Flow<List<RouteWithPoints>> {
        return database.routeDao().getAllRoutesWithPoints()
    }
}