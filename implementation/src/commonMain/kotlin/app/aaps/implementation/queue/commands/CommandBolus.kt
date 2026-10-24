package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings

class CommandBolus(
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    private val activePlugin: ActivePlugin,
    override val pumpEnactResultProvider: () -> PumpEnactResult,
    private val bolusProgressData: BolusProgressData,
    private val detailedBolusInfo: DetailedBolusInfo,
    type: Command.CommandType,
    private val bolusGeneration: Long,
) : Command() {

    override var commandType: Command.CommandType = type

    override suspend fun execute(): PumpEnactResult {
        val r = activePlugin.activePump.deliverTreatment(detailedBolusInfo)
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
        // Keep the stop flag for the executor without showing a stopped bolus as fully delivered.
        // Every path uses this command's generation so it cannot clear a newer bolus.
        when {
            bolusProgressData.isStopPressed -> bolusProgressData.clearAfterStop(bolusGeneration)
            r.success                      -> bolusProgressData.completeAndAutoClear(bolusGeneration)
            else                           -> bolusProgressData.clear(bolusGeneration)
        }
        return r
    }

    override fun status(): String {
        val bolus = if (detailedBolusInfo.insulin > 0) rh.gs(CoreUiStrings.bolus_u_min, detailedBolusInfo.insulin) else ""
        val carbs = if (detailedBolusInfo.carbs > 0) rh.gs(CoreUiStrings.carbs_g, detailedBolusInfo.carbs.toInt()) else ""
        return if (bolus.isNotEmpty() && carbs.isNotEmpty()) rh.gs(InterfacesStrings.values_with_comma, bolus, carbs) else bolus + carbs
    }

    override fun log(): String {
        return (if (detailedBolusInfo.insulin > 0) "BOLUS " + rh.gs(InterfacesStrings.format_insulin_units, detailedBolusInfo.insulin) else "") +
            if (detailedBolusInfo.carbs > 0) "CARBS " + rh.gs(InterfacesStrings.format_carbs, detailedBolusInfo.carbs.toInt()) else ""
    }

    override fun cancel(comment: TextRef, success: Boolean, cancelled: Boolean) {
        super.cancel(comment, success, cancelled)
        bolusProgressData.clear(bolusGeneration)
    }
}
