package app.aaps.pump.omnipod.omnipod5.ui.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.ui.compose.OmnipodComposeHost
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5RegistrationData
import app.aaps.pump.omnipod.omnipod5.ui.O5CredentialImportScreen
import app.aaps.pump.omnipod.omnipod5.ui.O5CredentialImportViewModel
import app.aaps.pump.omnipod.omnipod5.ui.wizard.compose.O5OmnipodWizardViewModel
import app.aaps.core.ui.R as CoreUiR

class OmnipodO5ComposeContent(
    private val pluginName: String,
    private val protectionCheck: ProtectionCheck,
    private val blePreCheck: BlePreCheck,
    private val rh: ResourceHelper
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val overviewViewModel: O5OverviewViewModel = metroViewModel()
        OmnipodComposeHost(
            pluginName = pluginName,
            blePreCheck = blePreCheck,
            setToolbarConfig = setToolbarConfig,
            onNavigateBack = onNavigateBack,
            onSettings = onSettings,
            overviewState = overviewViewModel.uiState,
            overviewEvents = overviewViewModel.events,
            wizardViewModel = {
                val viewModel: O5OmnipodWizardViewModel = metroViewModel()
                viewModel
            },
            onConfirmDiscardPod = overviewViewModel::confirmDiscardPod,
            activationNeedsExtraContent = { O5RegistrationData.pickControllerId == 0L },
            extraContent = { onBack ->
                val title = stringResource(R.string.omnipod_5_name)
                LaunchedEffect(title) {
                    setToolbarConfig(
                        ToolbarConfig(
                            title = title,
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiR.string.back))
                                }
                            },
                            actions = {}
                        )
                    )
                }
                val credentialViewModel: O5CredentialImportViewModel = metroViewModel()
                O5CredentialImportScreen(viewModel = credentialViewModel, rh = rh)
            }
        )
    }
}
