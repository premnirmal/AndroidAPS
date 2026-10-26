package app.aaps

import app.aaps.core.interfaces.configuration.Config
import app.aaps.implementation.resources.IconsProviderImplementation
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import app.aaps.core.ui.R as CoreUiR
import app.aaps.implementation.R as ImplementationR

class IconsProviderTest {

    @Test
    fun `app and notification icons follow Trio mode without restarting the provider`() {
        val config = mock<Config>()
        val icons = IconsProviderImplementation(config)

        assertThat(icons.getIcon()).isEqualTo(CoreUiR.mipmap.ic_launcher_round)
        assertThat(icons.getNotificationIcon()).isEqualTo(CoreUiR.drawable.ic_notif_aaps)

        whenever(config.TRIO).thenReturn(true)
        assertThat(icons.getIcon()).isEqualTo(CoreUiR.mipmap.ic_trio_launcher_round)
        assertThat(icons.getNotificationIcon()).isEqualTo(CoreUiR.drawable.ic_notif_trio)

        whenever(config.TRIO).thenReturn(false)
        assertThat(icons.getIcon()).isEqualTo(CoreUiR.mipmap.ic_launcher_round)
        assertThat(icons.getNotificationIcon()).isEqualTo(CoreUiR.drawable.ic_notif_aaps)
    }

    @Test
    fun `pump control keeps its own icons`() {
        val config = mock<Config>()
        whenever(config.PUMPCONTROL).thenReturn(true)
        val icons = IconsProviderImplementation(config)

        assertThat(icons.getIcon()).isEqualTo(CoreUiR.mipmap.ic_pumpcontrol)
        assertThat(icons.getNotificationIcon()).isEqualTo(ImplementationR.drawable.ic_notif_pumpcontrol)
    }

    @Test
    fun `clients keep their own icons`() {
        val config = mock<Config>()
        whenever(config.AAPSCLIENT).thenReturn(true)
        val icons = IconsProviderImplementation(config)

        whenever(config.AAPSCLIENT1).thenReturn(true)
        assertThat(icons.getIcon()).isEqualTo(CoreUiR.mipmap.ic_yellowowl)
        assertThat(icons.getNotificationIcon()).isEqualTo(ImplementationR.drawable.ic_notif_nsclient)

        whenever(config.AAPSCLIENT1).thenReturn(false)
        whenever(config.AAPSCLIENT2).thenReturn(true)
        assertThat(icons.getIcon()).isEqualTo(CoreUiR.mipmap.ic_blueowl)
        assertThat(icons.getNotificationIcon()).isEqualTo(ImplementationR.drawable.ic_notif_nsclient)

        whenever(config.AAPSCLIENT2).thenReturn(false)
        whenever(config.AAPSCLIENT3).thenReturn(true)
        assertThat(icons.getIcon()).isEqualTo(CoreUiR.mipmap.ic_greenowl)
        assertThat(icons.getNotificationIcon()).isEqualTo(ImplementationR.drawable.ic_notif_nsclient)
    }
}
