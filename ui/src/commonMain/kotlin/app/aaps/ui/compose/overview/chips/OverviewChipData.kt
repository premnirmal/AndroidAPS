package app.aaps.ui.compose.overview.chips

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.ui.UiStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

internal fun overviewChipRefresh(bolusProgressData: BolusProgressData, rxBus: RxBus): Flow<Unit> = merge(
    flow {
        while (true) {
            emit(Unit)
            delay(150_000L)
        }
    },
    bolusProgressData.state.map { Unit },
    rxBus.toFlow(EventAutosensCalculationFinished::class).map { Unit },
    rxBus.toFlow(EventLoopUpdateGui::class).map { Unit }
)

internal fun TextResolver.cobWithRequiredCarbs(cobText: String, carbsRequired: Int): String =
    gs(
        UiStrings.cob_with_carbs_required,
        cobText,
        gs(InterfacesStrings.format_carbs, carbsRequired)
    )
