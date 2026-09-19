package app.aaps.pump.omnipod.common.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.omnipod.common.R

enum class DashBooleanPreferenceKey(
    override val key: String,
    override val defaultValue: Boolean,
    private val titleResId: Int,
) : BooleanPreferenceKey {

    UseBonding("AAPS.Omnipod.Dash.use_bonding", false, titleResId = R.string.omnipod_dash_use_bonding),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
}
