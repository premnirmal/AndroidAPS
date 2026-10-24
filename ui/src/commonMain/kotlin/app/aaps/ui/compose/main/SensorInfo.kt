package app.aaps.ui.compose.main

import androidx.compose.runtime.Immutable

@Immutable
data class SensorInfo(
    val sourceName: String = "",
    val startedAt: Long? = null,
    val batteryLevel: Int = -1
)
