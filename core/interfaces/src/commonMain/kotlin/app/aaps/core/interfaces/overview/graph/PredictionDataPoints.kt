package app.aaps.core.interfaces.overview.graph

import app.aaps.core.data.model.SourceSensor
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.profile.ProfileUtil

fun APSResult.toPredictionDataPoints(
    profileUtil: ProfileUtil,
    lowMarkInUnits: Double,
    highMarkInUnits: Double
): List<BgDataPoint> =
    predictionsAsGv
        .filter { it.value >= 40 }
        .map { gv ->
            val valueInUnits = profileUtil.fromMgdlToUnits(gv.value)
            BgDataPoint(
                timestamp = gv.timestamp,
                value = valueInUnits,
                range = when {
                    valueInUnits > highMarkInUnits -> BgRange.HIGH
                    valueInUnits < lowMarkInUnits  -> BgRange.LOW
                    else                           -> BgRange.IN_RANGE
                },
                type = when (gv.sourceSensor) {
                    SourceSensor.IOB_PREDICTION   -> BgType.IOB_PREDICTION
                    SourceSensor.COB_PREDICTION   -> BgType.COB_PREDICTION
                    SourceSensor.A_COB_PREDICTION -> BgType.A_COB_PREDICTION
                    SourceSensor.UAM_PREDICTION   -> BgType.UAM_PREDICTION
                    SourceSensor.ZT_PREDICTION    -> BgType.ZT_PREDICTION
                    else                          -> BgType.IOB_PREDICTION
                }
            )
        }
        .sortedBy(BgDataPoint::timestamp)
