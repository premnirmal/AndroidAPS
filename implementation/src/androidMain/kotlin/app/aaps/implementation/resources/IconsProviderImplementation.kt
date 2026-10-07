package app.aaps.implementation.resources

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.ui.R as CoreUiR
import app.aaps.implementation.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IconsProviderImplementation(private val config: Config) : IconsProvider {

    override fun getIcon(): Int =
        when {
            config.AAPSCLIENT3 -> CoreUiR.mipmap.ic_greenowl
            config.AAPSCLIENT2 -> CoreUiR.mipmap.ic_blueowl
            config.AAPSCLIENT1 -> CoreUiR.mipmap.ic_yellowowl
            config.PUMPCONTROL -> CoreUiR.mipmap.ic_pumpcontrol
            config.TRIO        -> CoreUiR.mipmap.ic_trio_launcher_round
            else               -> CoreUiR.mipmap.ic_launcher_round
        }

    override fun getNotificationIcon(): Int =
        when {
            config.AAPSCLIENT  -> R.drawable.ic_notif_nsclient
            config.PUMPCONTROL -> R.drawable.ic_notif_pumpcontrol
            config.TRIO        -> CoreUiR.drawable.ic_notif_trio
            else               -> CoreUiR.drawable.ic_notif_aaps
        }
}
