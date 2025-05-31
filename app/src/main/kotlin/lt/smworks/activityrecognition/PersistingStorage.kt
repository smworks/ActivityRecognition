package lt.smworks.activityrecognition

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PersistingStorage(private val context: Context) {

    companion object {
        private const val ROUTES_DIRECTORY_NAME = "routes"
    }

    private val routeFileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
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

    fun saveRouteToFile(routePoints: List<Pair<Double, Double>>, activityName: String) {
        if (routePoints.isEmpty()) {
            FileLogger.w("Attempted to save an empty route.")
            return
        }
        val routesDir = File(context.filesDir, ROUTES_DIRECTORY_NAME)
        if (!routesDir.exists()) {
            routesDir.mkdirs()
        }
        val timestamp = routeFileDateFormat.format(Date())
        val fileName = "${activityName}_${timestamp}.route"
        val routeFile = File(routesDir, fileName)
        val routeString = routePoints.joinToString(";") { "${it.first},${it.second}" }
        try {
            routeFile.writeText(routeString)
            FileLogger.i("Route saved to: ${routeFile.absolutePath}")
        } catch (e: Exception) {
            FileLogger.e("Error saving route to file: $fileName", e)
        }
    }

    fun getRoutePaths(): List<String> {
        val routesDir = File(context.filesDir, ROUTES_DIRECTORY_NAME)
        if (!routesDir.exists() || !routesDir.isDirectory) {
            return emptyList()
        }
        return routesDir.listFiles { file -> file.isFile && file.name.endsWith(".route") }
            ?.map { it.absolutePath } ?: emptyList()
    }

    fun getRoute(path: String): List<LatLng> {
        val file = File(path)
        return if (file.exists() && file.isFile) {
            try {
                val routeString = file.readText()
                return routeString.split(";").mapNotNull {
                    val parts = it.split(",")
                    if (parts.size == 2) {
                        parts[0].toDoubleOrNull()?.let { lat ->
                            parts[1].toDoubleOrNull()?.let { lon ->
                                LatLng(lat, lon)
                            }
                        }
                    } else null
                }
            } catch (e: Exception) {
                FileLogger.e("Error reading route file: $path", e)
                emptyList()
            }
        } else {
            FileLogger.w("Route file not found or is not a file: $path")
            emptyList()
        }
    }


}