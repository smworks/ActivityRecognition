@file:Suppress("DEPRECATION")

package lt.smworks.activityrecognition

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.DetectedActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


fun Intent.getInfo(): String {
    return toString() + " " + bundleToString(extras)
}

fun bundleToString(bundle: Bundle?): String {
    val out = StringBuilder("Bundle[")

    if (bundle == null) {
        out.append("null")
    } else {
        var first = true
        for (key in bundle.keySet()) {
            if (!first) {
                out.append(", ")
            }

            out.append(key).append('=')

            val value = bundle.get(key)

            if (value is IntArray) {
                out.append(value.contentToString())
            } else if (value is ByteArray) {
                out.append(value.contentToString())
            } else if (value is BooleanArray) {
                out.append(value.contentToString())
            } else if (value is ShortArray) {
                out.append(value.contentToString())
            } else if (value is LongArray) {
                out.append(value.contentToString())
            } else if (value is FloatArray) {
                out.append(value.contentToString())
            } else if (value is DoubleArray) {
                out.append(value.contentToString())
            } else if (value is Array<*> && value.isArrayOf<String>()) {
                out.append(value.contentToString())
            } else if (value is Array<*> && value.isArrayOf<CharSequence>()) {
                out.append(value.contentToString())
            } else if (value is Array<*> && value.isArrayOf<Parcelable>()) {
                out.append(value.contentToString())
            } else if (value is Bundle) {
                out.append(bundleToString(value))
            } else {
                out.append(value)
            }

            first = false
        }
    }

    out.append("]")
    return out.toString()
}

fun Int.getActivityName(): String {
    return when (this) {
        DetectedActivity.STILL -> "Still"
        DetectedActivity.WALKING -> "Walking"
        DetectedActivity.RUNNING -> "Running"
        DetectedActivity.ON_BICYCLE -> "Cycling"
        DetectedActivity.IN_VEHICLE -> "In Vehicle"
        DetectedActivity.UNKNOWN -> "Unknown"
        DetectedActivity.TILTING -> "Tilting"
        else -> "Unknown Activity Type: $this"
    }
}

fun Int.getTransitionName(): String {
    return when (this) {
        ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "Enter"
        ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "Exit"
        else -> "Unknown Transition Type: $this"
    }
}

fun getActivityRecognitionPermissionType(): String {
    val activityPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Manifest.permission.ACTIVITY_RECOGNITION
    } else {
        "com.google.android.gms.permission.ACTIVITY_RECOGNITION"
    }
    return activityPermission
}

@Composable
fun Modifier.lazyListScrollBar(
    state: LazyListState,
    width: Dp = 8.dp
): Modifier {
    val targetAlpha = if (state.isScrollInProgress) 1f else 0f
    val duration = if (state.isScrollInProgress) 150 else 500
    val color = MaterialTheme.colorScheme.primary

    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = duration)
    )

    return drawWithContent {
        drawContent()

        val firstVisibleElementIndex = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index
        val needDrawScrollbar = state.isScrollInProgress || alpha > 0.0f

        if (needDrawScrollbar && firstVisibleElementIndex != null) {
            val elementHeight = this.size.height / state.layoutInfo.totalItemsCount
            val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
            val scrollbarHeight = state.layoutInfo.visibleItemsInfo.size * elementHeight

            drawRect(
                color = color,
                topLeft = Offset(this.size.width - width.toPx(), scrollbarOffsetY),
                size = Size(width.toPx(), scrollbarHeight),
                alpha = alpha
            )
        }
    }
}

/**
 * Call before verticalScroll on modifier
 */
@Composable
fun Modifier.scrollBar(state: ScrollState, scrollbarWidth: Dp = 6.dp, color: Color = Color.LightGray): Modifier{
    val alpha by animateFloatAsState(targetValue = if(state.isScrollInProgress) 1f else 0f,
        animationSpec = tween(400, delayMillis = if(state.isScrollInProgress) 0 else 700)
    )

    return this then Modifier.drawWithContent {
        drawContent()


        val viewHeight = state.viewportSize.toFloat()
        val contentHeight = state.maxValue + viewHeight

        val scrollbarHeight = (viewHeight * (viewHeight / contentHeight )).coerceIn(10.dp.toPx() .. viewHeight)
        val variableZone = viewHeight - scrollbarHeight
        val scrollbarYoffset = (state.value.toFloat() / state.maxValue) * variableZone

        drawRoundRect(
            cornerRadius = CornerRadius(scrollbarWidth.toPx() / 2, scrollbarWidth.toPx() / 2),
            color = color,
            topLeft = Offset(this.size.width - scrollbarWidth.toPx(), scrollbarYoffset),
            size = Size(scrollbarWidth.toPx(), scrollbarHeight),
            alpha = alpha
        )
    }
}

fun Long.timestampToDate(): String {
    val date = Date(this)
    val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return format.format(date)
}