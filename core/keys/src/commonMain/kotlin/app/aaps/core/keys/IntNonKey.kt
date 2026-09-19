package app.aaps.core.keys

import app.aaps.core.keys.interfaces.IntNonPreferenceKey

@Suppress("SpellCheckingInspection")
enum class IntNonKey(
    override val key: String,
    override val defaultValue: Int,
    override val exportable: Boolean = true
) : IntNonPreferenceKey {

    TddCycleOffset("tdd_cycle_offset", 0),
    LastOverviewProfilePercentage("last_overview_profile_percentage", 100, exportable = false)
}