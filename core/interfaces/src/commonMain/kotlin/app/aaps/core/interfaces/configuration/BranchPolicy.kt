package app.aaps.core.interfaces.configuration

/**
 * Fixed policies for this branch, separate from user preferences and imported settings.
 * Keep the upstream features in place so updates from dev can still be merged.
 */
object BranchPolicy {
    const val objectivesEnabled = false
    const val alarmSoundsEnabled = false
}
