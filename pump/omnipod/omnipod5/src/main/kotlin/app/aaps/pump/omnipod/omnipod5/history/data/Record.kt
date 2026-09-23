package app.aaps.pump.omnipod.omnipod5.history.data

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.data.model.BS

sealed class Record

data class BolusRecord(val amount: Double, val bolusType: BolusType) : Record()

data class TempBasalRecord(val duration: Int, val rate: Double) : Record()

data class BasalValuesRecord(val segments: List<Profile.ProfileValue>) : Record()

enum class BolusType {
    DEFAULT, SMB;

    fun toBolusInfoBolusType(): BS.Type = when (this) {
        DEFAULT -> BS.Type.NORMAL
        SMB -> BS.Type.SMB
    }

    companion object {
        fun fromBolusInfoBolusType(type: BS.Type): BolusType = when (type) {
            BS.Type.SMB -> SMB
            else -> DEFAULT
        }
    }
}
