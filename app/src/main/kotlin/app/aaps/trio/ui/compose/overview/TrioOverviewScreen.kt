package app.aaps.trio.ui.compose.overview

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ArrowCircleRight
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.ui.UiMode
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcLoopClosed
import app.aaps.core.ui.compose.icons.IcLoopDisabled
import app.aaps.core.ui.compose.icons.IcLoopDisconnected
import app.aaps.core.ui.compose.icons.IcLoopLgs
import app.aaps.core.ui.compose.icons.IcLoopOpen
import app.aaps.core.ui.compose.icons.IcLoopPaused
import app.aaps.core.ui.compose.icons.IcLoopPausedDst
import app.aaps.core.ui.compose.icons.IcLoopPausedPump
import app.aaps.core.ui.compose.icons.IcLoopSuperbolus
import app.aaps.core.ui.compose.icons.IcPumpCartridge
import app.aaps.core.ui.compose.loopColor
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.ui.compose.notificationsSheet.toColor
import app.aaps.ui.R
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.notificationsSheet.NotificationBottomSheet
import app.aaps.ui.compose.overview.BgInfoSection
import app.aaps.ui.compose.overview.OverviewChipsColumn
import app.aaps.ui.compose.overview.TrioOverviewModel
import app.aaps.ui.compose.overview.chips.CobUiState
import app.aaps.ui.compose.overview.chips.IobUiState
import app.aaps.ui.compose.overview.chips.SceneBadge
import app.aaps.ui.compose.overview.chips.SensitivityUiState
import app.aaps.ui.compose.scenes.ActiveSceneBanner
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun TrioOverviewScreen(
    model: TrioOverviewModel,
    modifier: Modifier = Modifier
) = with(model) {
    val bgInfoState by graphViewModel.bgInfoState.collectAsStateWithLifecycle()
    val predictions by graphViewModel.predictionsFlow.collectAsStateWithLifecycle()
    val sensitivityUiState by chipsViewModel.sensitivityUiState.collectAsStateWithLifecycle()
    val iobUiState by chipsViewModel.iobUiState.collectAsStateWithLifecycle()
    val cobUiState by chipsViewModel.cobUiState.collectAsStateWithLifecycle()
    val now by graphViewModel.nowTimestamp.collectAsStateWithLifecycle()
    val notifications by notificationsFlow.collectAsStateWithLifecycle()
    val bolusState by bolusStateFlow.collectAsStateWithLifecycle()
    val timeInRangeTodayPercent by timeInRangeTodayPercentFlow.collectAsStateWithLifecycle()
    val calcProgress by calcProgressFlow.collectAsStateWithLifecycle()
    var dismissedNotifications by remember { mutableStateOf(emptySet<Pair<Int, Long>>()) }
    var showNotificationSheet by remember { mutableStateOf(false) }
    val visibleNotifications = notifications.filterNot {
        it.instanceKey to it.date in dismissedNotifications
    }

    LaunchedEffect(notifications) {
        val activeNotifications = notifications.mapTo(mutableSetOf()) { it.instanceKey to it.date }
        dismissedNotifications = dismissedNotifications.intersect(activeNotifications)
    }
    LaunchedEffect(autoShowNotificationSheet) {
        if (autoShowNotificationSheet) {
            showNotificationSheet = true
            onAutoShowConsumed()
        }
    }
    val pumpTimeRemainingText = pumpEndTimeMillis?.let { endTime ->
        val totalHours = ((endTime - now).coerceAtLeast(0L) / 3_600_000L).toInt()
        val days = totalHours / 24
        val hours = totalHours % 24
        if (days >= 1) stringResource(R.string.trio_pump_time_days_hours, days, hours)
        else stringResource(R.string.trio_pump_time_hours, hours)
    }
    val predictedText = predictions.lastOrNull()?.value?.let { value ->
        if (value >= 40.0) value.roundToInt().toString()
        else String.format(Locale.getDefault(), "%.1f", value)
    } ?: stringResource(app.aaps.core.ui.R.string.value_unavailable_short)

    TrioOverviewContent(
        profileName = profileName,
        isProfileModified = isProfileModified,
        profileProgress = profileProgress,
        profileSceneManaged = profileSceneManaged,
        profilePercentage = profilePercentage,
        profileTargetRangeText = profileTargetRangeText,
        tempTargetText = tempTargetText,
        tempTargetState = tempTargetState,
        tempTargetProgress = tempTargetProgress,
        tempTargetReason = tempTargetReason,
        tempTargetSceneManaged = tempTargetSceneManaged,
        runningMode = runningMode,
        runningModeText = runningModeText,
        runningModeRemaining = runningModeRemaining,
        runningModeProgress = runningModeProgress,
        runningModeSceneManaged = runningModeSceneManaged,
        lastLoopAgeMillis = lastLoopAgeMillis,
        smbEnabled = smbEnabled,
        tbrState = tbrState,
        calcProgress = calcProgress,
        bgInfo = bgInfoState.bgInfo,
        bgTimeAgoText = bgInfoState.timeAgoText,
        sensitivityUiState = sensitivityUiState,
        iobUiState = iobUiState,
        cobUiState = cobUiState,
        predictedText = predictedText,
        onNavigate = onNavigate,
        onTbrChipClick = onTbrChipClick,
        onIobChipClick = onIobChipClick,
        onBgSourceClick = onBgSourceClick,
        paddingValues = paddingValues,
        activeSceneState = activeSceneState,
        sceneExpired = sceneExpired,
        onEndScene = onEndScene,
        onDismissScene = onDismissScene,
        endSceneEnabled = endSceneEnabled,
        commandsAllowed = commandsAllowed,
        pumpNeedsSetup = pumpNeedsSetup,
        pumpTimeRemainingText = pumpTimeRemainingText,
        reservoirUnits = reservoirUnits,
        notificationCount = visibleNotifications.size,
        highestNotificationLevel = visibleNotifications.minByOrNull { it.level.ordinal }?.level,
        onNotificationClick = { showNotificationSheet = true },
        bolusState = bolusState,
        onStopBolus = onStopBolus,
        timeInRangeTodayPercent = timeInRangeTodayPercent,
        formatDuration = formatDuration,
        modifier = modifier,
        graphContent = { chartHeight ->
            TrioOverviewGraph(
                graphViewModel = graphViewModel,
                height = chartHeight,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )

    if (showNotificationSheet && visibleNotifications.isNotEmpty()) {
        NotificationBottomSheet(
            notifications = visibleNotifications,
            onDismissSheet = { showNotificationSheet = false },
            onDismissNotification = { notification ->
                dismissedNotifications = dismissedNotifications + (notification.instanceKey to notification.date)
                onDismissNotification(notification)
            },
            onNotificationActionClick = onNotificationActionClick
        )
    }
}

@Composable
private fun TrioOverviewContent(
    profileName: String,
    isProfileModified: Boolean,
    profileProgress: Float,
    profileSceneManaged: Boolean,
    profilePercentage: Int,
    profileTargetRangeText: String,
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    tempTargetSceneManaged: Boolean,
    runningMode: RM.Mode,
    runningModeText: String,
    runningModeRemaining: String,
    runningModeProgress: Float,
    runningModeSceneManaged: Boolean,
    lastLoopAgeMillis: Long?,
    smbEnabled: Boolean,
    tbrState: TbrState,
    calcProgress: Int,
    bgInfo: BgInfoData?,
    bgTimeAgoText: String,
    sensitivityUiState: SensitivityUiState,
    iobUiState: IobUiState,
    cobUiState: CobUiState,
    predictedText: String,
    onNavigate: (NavigationRequest) -> Unit,
    onTbrChipClick: () -> Unit,
    onIobChipClick: () -> Unit,
    onBgSourceClick: () -> Unit,
    paddingValues: PaddingValues,
    activeSceneState: ActiveSceneState?,
    sceneExpired: Boolean,
    onEndScene: () -> Unit,
    onDismissScene: () -> Unit,
    endSceneEnabled: Boolean,
    commandsAllowed: Boolean,
    pumpNeedsSetup: Boolean,
    pumpTimeRemainingText: String?,
    reservoirUnits: Double?,
    notificationCount: Int,
    highestNotificationLevel: NotificationLevel?,
    onNotificationClick: () -> Unit,
    bolusState: BolusProgressState?,
    onStopBolus: () -> Unit,
    timeInRangeTodayPercent: Int?,
    formatDuration: (Long) -> String,
    graphContent: @Composable (Dp) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPredictionInfo by remember { mutableStateOf(false) }
    var bgGlowCenter by remember { mutableStateOf<Offset?>(null) }
    val animatedCalcProgress = remember { Animatable(calcProgress.coerceIn(0, 100) / 100f) }

    LaunchedEffect(calcProgress) {
        val targetProgress = calcProgress.coerceIn(0, 100) / 100f
        if (targetProgress < animatedCalcProgress.value) {
            animatedCalcProgress.snapTo(targetProgress)
        } else {
            animatedCalcProgress.animateTo(
                targetValue = targetProgress,
                animationSpec = tween(durationMillis = 250)
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        val chartHeight = maxHeight * 0.48f

        TrioBgGlow(
            visible = bgInfo != null,
            center = bgGlowCenter,
            modifier = Modifier.size(width = AapsSpacing.bgCircleSize * 2, height = AapsSpacing.bgCircleSize * 2)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(AapsSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            ActiveSceneBanner(
                activeState = activeSceneState,
                expired = sceneExpired,
                onEndClick = onEndScene,
                onDismiss = onDismissScene,
                endEnabled = endSceneEnabled,
                formatDuration = formatDuration
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(AapsSpacing.chipProgressHeight)) {
                            if (calcProgress < 100) {
                                LinearProgressIndicator(
                                    progress = { animatedCalcProgress.value },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = AapsSpacing.small)
                                        .height(AapsSpacing.chipProgressHeight)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AapsSpacing.small),
                            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PumpEntryPoint(
                                needsSetup = pumpNeedsSetup,
                                timeRemainingText = pumpTimeRemainingText,
                                reservoirUnits = reservoirUnits,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onNavigate(NavigationRequest.Element(ElementType.PUMP))
                                    }
                            )
                            BgInfoSection(
                                bgInfo = bgInfo,
                                timeAgoText = bgTimeAgoText,
                                modifier = Modifier
                                    .onGloballyPositioned { coordinates ->
                                        val position = coordinates.positionInRoot()
                                        bgGlowCenter = Offset(
                                            x = position.x + coordinates.size.width / 2f,
                                            y = position.y + coordinates.size.height / 2f
                                        )
                                    }
                                    .clickable(onClick = onBgSourceClick),
                                useGradientRing = true
                            )
                            LoopStatusAndPrediction(
                                runningMode = runningMode,
                                runningModeText = runningModeText,
                                lastLoopAgeMillis = lastLoopAgeMillis,
                                predictedText = predictedText,
                                onClick = { showPredictionInfo = true },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AapsSpacing.chipHeight + AapsSpacing.large),
                        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MetricRow(
                            label = stringResource(app.aaps.core.ui.R.string.iob),
                            value = iobUiState.text,
                            onClick = onIobChipClick,
                            modifier = Modifier.weight(1f)
                        )
                        MetricRow(
                            label = stringResource(app.aaps.core.ui.R.string.cob),
                            value = cobUiState.baseText.ifEmpty { cobUiState.text },
                            modifier = Modifier.weight(1f),
                            trailingContent = {
                                if (cobUiState.carbsReq > 0) {
                                    Badge(
                                        modifier = Modifier.clickable {
                                            onNavigate(NavigationRequest.Element(ElementType.BOLUS_WIZARD))
                                        },
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ) {
                                        Text(
                                            text = stringResource(
                                                R.string.trio_carbs_required_badge,
                                                cobUiState.carbsReq
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = AapsSpacing.small)
                                        )
                                    }
                                }
                            }
                        )
                        Box(
                            contentAlignment = Alignment.CenterEnd,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = AapsSpacing.xxLarge + AapsSpacing.small * 2)
                        ) {
                            TrioNotificationButton(
                                notificationCount = notificationCount,
                                highestLevel = highestNotificationLevel,
                                onClick = onNotificationClick
                            )
                        }
                    }
                }
            }

            graphContent(chartHeight)

            bolusState?.let { state ->
                TrioBolusingCard(
                    state = state,
                    onStopBolus = onStopBolus
                )
            }

            TrioProfileCard(
                profileName = profileName,
                profilePercentage = profilePercentage,
                profileTargetRangeText = profileTargetRangeText,
                tempTargetText = tempTargetText,
                tempTargetState = tempTargetState,
                progress = profileProgress,
                sceneManaged = profileSceneManaged,
                onClick = { onNavigate(NavigationRequest.Element(ElementType.PROFILE_MANAGEMENT)) }
            )

            TimeInRangeTodayCard(
                timeInRangeTodayPercent = timeInRangeTodayPercent,
                onClick = { onNavigate(NavigationRequest.TrioStatistics) }
            )
        }
    }

    if (showPredictionInfo) {
        PredictionInfoBottomSheet(
            profileName = profileName,
            isProfileModified = isProfileModified,
            profileProgress = profileProgress,
            profileSceneManaged = profileSceneManaged,
            tempTargetText = tempTargetText,
            tempTargetState = tempTargetState,
            tempTargetProgress = tempTargetProgress,
            tempTargetReason = tempTargetReason,
            tempTargetSceneManaged = tempTargetSceneManaged,
            runningMode = runningMode,
            runningModeText = runningModeText,
            runningModeRemaining = runningModeRemaining,
            runningModeProgress = runningModeProgress,
            runningModeSceneManaged = runningModeSceneManaged,
            smbEnabled = smbEnabled,
            tbrState = tbrState,
            iobUiState = iobUiState,
            cobUiState = cobUiState,
            sensitivityUiState = sensitivityUiState,
            commandsAllowed = commandsAllowed,
            onNavigate = { request ->
                showPredictionInfo = false
                onNavigate(request)
            },
            onTbrChipClick = {
                showPredictionInfo = false
                onTbrChipClick()
            },
            onIobChipClick = {
                showPredictionInfo = false
                onIobChipClick()
            },
            onDismiss = { showPredictionInfo = false }
        )
    }
}

@Composable
private fun TrioBgGlow(
    visible: Boolean,
    center: Offset?,
    modifier: Modifier = Modifier
) {
    if (!visible || center == null) return

    val transition = rememberInfiniteTransition(label = "trioBgGlow")
    val glowScale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trioBgGlowScale"
    )
    val glowOffset by transition.animateFloat(
        initialValue = -0.1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trioBgGlowOffset"
    )
    val glowColor = AapsTheme.generalColors.trioBgGlow

    Canvas(modifier = modifier) {
        val animatedCenter = Offset(
            x = center.x + size.width * glowOffset,
            y = center.y
        )
        val radius = size.width * 0.72f * glowScale
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowColor,
                    glowColor.copy(alpha = glowColor.alpha * 0.55f),
                    Color.Transparent
                ),
                center = animatedCenter,
                radius = radius
            ),
            radius = radius,
            center = animatedCenter
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 900)
@Composable
private fun TrioOverviewScreenPreview() {
    AapsTheme(
        uiMode = UiMode.SYSTEM,
    ) {
        TrioOverviewContent(
            profileName = "Standard",
            isProfileModified = true,
            profileProgress = 0.65f,
            profileSceneManaged = false,
            profilePercentage = 100,
            profileTargetRangeText = "90–110 mg/dL",
            tempTargetText = "110 mg/dL",
            tempTargetState = TempTargetChipState.Active,
            tempTargetProgress = 0.5f,
            tempTargetReason = TT.Reason.ACTIVITY,
            tempTargetSceneManaged = false,
            runningMode = RM.Mode.CLOSED_LOOP,
            runningModeText = "Closed loop",
            runningModeRemaining = "",
            runningModeProgress = 0f,
            runningModeSceneManaged = false,
            lastLoopAgeMillis = 45_000L,
            smbEnabled = true,
            tbrState = TbrState.HIGH,
            calcProgress = 65,
            bgInfo = BgInfoData(
                bgValue = 118.0,
                bgText = "118",
                bgRange = BgRange.IN_RANGE,
                isOutdated = false,
                timestamp = 1_780_000_000_000L,
                trendArrow = TrendArrow.FLAT,
                trendDescription = "Flat",
                delta = 2.0,
                deltaText = "+2",
                shortAvgDelta = 1.5,
                shortAvgDeltaText = "+1.5",
                longAvgDelta = 1.0,
                longAvgDeltaText = "+1.0"
            ),
            bgTimeAgoText = "2 min",
            sensitivityUiState = SensitivityUiState(
                asText = "105%",
                isfFrom = "45",
                isfTo = "47",
                ratio = 1.05,
                hasData = true
            ),
            iobUiState = IobUiState(text = "1.25 U", iobTotal = 1.25),
            cobUiState = CobUiState(text = "18 g", cobValue = 18.0),
            predictedText = "132",
            onNavigate = {},
            onTbrChipClick = {},
            onIobChipClick = {},
            onBgSourceClick = {},
            paddingValues = PaddingValues(),
            activeSceneState = null,
            sceneExpired = false,
            onEndScene = {},
            onDismissScene = {},
            endSceneEnabled = true,
            commandsAllowed = true,
            pumpNeedsSetup = false,
            pumpTimeRemainingText = "2d 6h",
            reservoirUnits = 50.0,
            notificationCount = 2,
            highestNotificationLevel = NotificationLevel.NORMAL,
            onNotificationClick = {},
            bolusState = null,
            onStopBolus = {},
            timeInRangeTodayPercent = 82,
            formatDuration = { "30 min" },
            graphContent = { chartHeight ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Glucose graph",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                }
            }
        )
    }
}

@Composable
private fun TrioProfileCard(
    profileName: String,
    profilePercentage: Int,
    profileTargetRangeText: String,
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    progress: Float,
    sceneManaged: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (profileName.isEmpty()) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val subtitle = if (profileName.isEmpty()) {
        stringResource(app.aaps.core.ui.R.string.no_profile_set)
    } else {
        stringResource(
            R.string.trio_profile_summary,
            profilePercentage,
            profileTargetRangeText
        )
    }
    val title = if (tempTargetState == TempTargetChipState.Active && tempTargetText.isNotEmpty()) {
        stringResource(R.string.trio_profile_with_temp_target, profileName, tempTargetText)
    } else {
        profileName
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AapsSpacing.extraLarge),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        ),
        tonalElevation = AapsSpacing.extraSmall
    ) {
        Box {
            Row(
                modifier = Modifier.padding(
                    horizontal = AapsSpacing.extraLarge,
                    vertical = AapsSpacing.large
                ),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(AapsSpacing.chipHeight),
                    color = contentColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(AapsSpacing.chipHeight)
                ) {
                    Icon(
                        imageVector = ElementType.PROFILE_MANAGEMENT.icon(),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.padding(AapsSpacing.medium)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = contentColor
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                if (sceneManaged) {
                    SceneBadge()
                }
            }
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(AapsSpacing.chipProgressHeight),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun TrioBolusingCard(
    state: BolusProgressState,
    onStopBolus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(AapsSpacing.extraLarge),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        ),
        tonalElevation = AapsSpacing.extraSmall,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = AapsSpacing.extraLarge,
                vertical = AapsSpacing.large
            ),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Vaccines,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(AapsSpacing.xxLarge)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.trio_bolusing_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(
                            R.string.trio_bolusing_progress,
                            state.delivered.cU,
                            state.insulin
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                if (state.stopDeliveryEnabled && !state.stopPressed && state.percent < 100) {
                    IconButton(onClick = onStopBolus) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(app.aaps.core.ui.R.string.cancel),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { state.percent.coerceIn(0, 100) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AapsSpacing.small),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun TimeInRangeTodayCard(
    timeInRangeTodayPercent: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(AapsSpacing.extraLarge),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        ),
        tonalElevation = AapsSpacing.extraSmall,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = AapsSpacing.extraLarge,
                vertical = AapsSpacing.large
            ),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeInRangeTodayPercent?.let {
                        stringResource(R.string.trio_time_in_range_percent, it)
                    } ?: stringResource(app.aaps.core.ui.R.string.value_unavailable_short),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.trio_time_in_range_today),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            LinearProgressIndicator(
                progress = { (timeInRangeTodayPercent ?: 0).coerceIn(0, 100) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AapsSpacing.small),
                trackColor = MaterialTheme.colorScheme.surface,
            )
        }
    }
}

@Composable
private fun LoopStatusAndPrediction(
    runningMode: RM.Mode,
    runningModeText: String,
    lastLoopAgeMillis: Long?,
    predictedText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(
            onClick = onClick
        ),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
    ) {
        TrioLoopStatusPill(
            mode = runningMode,
            modeDescription = runningModeText,
            lastLoopAgeMillis = lastLoopAgeMillis,
        )
        PredictionText(
            predictedText = predictedText,
        )
    }
}

@Composable
private fun TrioLoopStatusPill(
    mode: RM.Mode,
    modeDescription: String,
    lastLoopAgeMillis: Long?,
    modifier: Modifier = Modifier
) {
    val colors = AapsTheme.generalColors
    val ageMinutes = lastLoopAgeMillis?.milliseconds?.inWholeMinutes
    val color = when {
        !mode.isClosedLoopOrLgs() && mode != RM.Mode.RESUME -> mode.loopColor(colors)
        ageMinutes == null                                    -> MaterialTheme.colorScheme.onSurfaceVariant
        ageMinutes < 5L                                      -> colors.statusNormal
        ageMinutes < 10L                                     -> colors.statusWarning
        else                                                 -> colors.statusCritical
    }
    val ageText = when {
        ageMinutes == null || ageMinutes > 1_440L -> "--"
        ageMinutes < 1L                           -> stringResource(R.string.trio_loop_less_than_one_minute)
        else                                      -> stringResource(R.string.trio_loop_minutes, ageMinutes)
    }

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Surface(
            modifier = modifier.height(AapsSpacing.chipHeight),
            shape = RoundedCornerShape(AapsSpacing.chipHeight),
            color = Color.Transparent,
            border = BorderStroke(
                width = AapsSpacing.extraSmall,
                color = color.copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = AapsSpacing.large),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = mode.toLoopStatusIcon(),
                    contentDescription = modeDescription,
                    tint = color,
                    modifier = Modifier.size(AapsSpacing.chipIconSize)
                )
                Text(
                    text = ageText,
                    color = color,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

private fun RM.Mode.toLoopStatusIcon() = when (this) {
    RM.Mode.CLOSED_LOOP       -> IcLoopClosed
    RM.Mode.CLOSED_LOOP_LGS   -> IcLoopLgs
    RM.Mode.OPEN_LOOP         -> IcLoopOpen
    RM.Mode.DISABLED_LOOP     -> IcLoopDisabled
    RM.Mode.SUPER_BOLUS       -> IcLoopSuperbolus
    RM.Mode.DISCONNECTED_PUMP -> IcLoopDisconnected
    RM.Mode.SUSPENDED_BY_PUMP -> IcLoopPausedPump
    RM.Mode.SUSPENDED_BY_DST  -> IcLoopPausedDst
    RM.Mode.SUSPENDED_BY_USER -> IcLoopPaused
    RM.Mode.RESUME            -> IcLoopClosed
}

@Composable
private fun PumpEntryPoint(
    needsSetup: Boolean,
    timeRemainingText: String?,
    reservoirUnits: Double?,
    modifier: Modifier = Modifier
) {
    val reservoirColor = when {
        reservoirUnits == null || needsSetup -> MaterialTheme.colorScheme.onSurfaceVariant
        reservoirUnits <= 10.0               -> AapsTheme.generalColors.statusCritical
        reservoirUnits <= 30.0               -> AapsTheme.generalColors.statusWarning
        else                                 -> AapsTheme.generalColors.activeInsulinText
    }
    val reservoirText = when {
        needsSetup || reservoirUnits == null -> "--"
        reservoirUnits >= 50.0               -> stringResource(R.string.trio_reservoir_over_50)
        else                                 -> stringResource(
            R.string.trio_reservoir_units,
            reservoirUnits.roundToInt()
        )
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
    ) {
        Surface(
            modifier = Modifier.height(AapsSpacing.chipHeight),
            shape = RoundedCornerShape(AapsSpacing.chipHeight),
            color = Color.Transparent,
            border = BorderStroke(
                width = AapsSpacing.extraSmall,
                color = reservoirColor.copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = AapsSpacing.large),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (needsSetup) Icons.Default.Warning else IcPumpCartridge,
                    contentDescription = null,
                    tint = if (needsSetup) MaterialTheme.colorScheme.error else reservoirColor,
                    modifier = Modifier.size(AapsSpacing.chipIconSize)
                )
                Text(
                    text = reservoirText,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (needsSetup) MaterialTheme.colorScheme.error else reservoirColor
                )
            }
        }
        Row(
            modifier = Modifier
                .height(AapsSpacing.chipHeight)
                .padding(horizontal = AapsSpacing.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    needsSetup                -> stringResource(R.string.trio_no_pump)
                    timeRemainingText != null -> timeRemainingText
                    else                      -> "--"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PredictionText(
    predictedText: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(AapsSpacing.chipHeight)
            .padding(horizontal = AapsSpacing.large),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.ArrowCircleRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(AapsSpacing.chipIconSize)
        )
        Text(
            text = predictedText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PredictionInfoBottomSheet(
    profileName: String,
    isProfileModified: Boolean,
    profileProgress: Float,
    profileSceneManaged: Boolean,
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    tempTargetSceneManaged: Boolean,
    runningMode: RM.Mode,
    runningModeText: String,
    runningModeRemaining: String,
    runningModeProgress: Float,
    runningModeSceneManaged: Boolean,
    smbEnabled: Boolean,
    tbrState: TbrState,
    iobUiState: IobUiState,
    cobUiState: CobUiState,
    sensitivityUiState: SensitivityUiState,
    commandsAllowed: Boolean,
    onNavigate: (NavigationRequest) -> Unit,
    onTbrChipClick: () -> Unit,
    onIobChipClick: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AapsSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            OverviewChipsColumn(
                runningMode = runningMode,
                runningModeText = runningModeText,
                runningModeRemaining = runningModeRemaining,
                runningModeProgress = runningModeProgress,
                runningModeSceneManaged = runningModeSceneManaged,
                smbEnabled = smbEnabled,
                profileName = profileName,
                isProfileModified = isProfileModified,
                profileProgress = profileProgress,
                profileSceneManaged = profileSceneManaged,
                tempTargetText = tempTargetText,
                tempTargetState = tempTargetState,
                tempTargetProgress = tempTargetProgress,
                tempTargetReason = tempTargetReason,
                tempTargetSceneManaged = tempTargetSceneManaged,
                tbrState = tbrState,
                iobUiState = iobUiState,
                cobUiState = cobUiState,
                sensitivityUiState = sensitivityUiState,
                onNavigate = onNavigate,
                onTbrChipClick = onTbrChipClick,
                onIobChipClick = onIobChipClick,
                commandsAllowed = commandsAllowed,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TrioNotificationButton(
    notificationCount: Int,
    highestLevel: NotificationLevel?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = notificationCount > 0
    Surface(
        onClick = onClick,
        enabled = visible,
        shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
        color = highestLevel?.toColor() ?: MaterialTheme.colorScheme.primary,
        modifier = modifier.alpha(if (visible) 1f else 0f)
    ) {
        BadgedBox(
            badge = {
                Badge {
                    Text(text = notificationCount.toString())
                }
            },
            modifier = Modifier.padding(AapsSpacing.small)
        ) {
            IconButton(
                onClick = onClick,
                enabled = visible,
                modifier = Modifier.size(AapsSpacing.xxLarge)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = AapsTheme.generalColors.onNotification
                )
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable RowScope.() -> Unit = {}
) {
    Box(
        modifier = modifier.clickable(enabled = onClick != null, onClick = onClick ?: {}),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AapsSpacing.medium, vertical = AapsSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            trailingContent()
        }
    }
}
