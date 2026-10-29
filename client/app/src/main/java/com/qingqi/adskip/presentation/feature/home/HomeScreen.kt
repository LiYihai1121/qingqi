package com.qingqi.adskip.presentation.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingqi.adskip.R
import com.qingqi.adskip.platform.device.KeepAliveNavigator
import com.qingqi.adskip.presentation.common.Messenger
import com.qingqi.adskip.presentation.common.UiEffect
import com.qingqi.adskip.presentation.designsystem.component.AppHeader
import com.qingqi.adskip.presentation.designsystem.component.IconPlate
import com.qingqi.adskip.presentation.designsystem.component.SectionCard
import com.qingqi.adskip.presentation.designsystem.component.SectionHint
import com.qingqi.adskip.presentation.designsystem.component.SettingRow
import com.qingqi.adskip.presentation.designsystem.component.StatTile
import com.qingqi.adskip.presentation.designsystem.component.TagBadge
import com.qingqi.adskip.presentation.designsystem.component.rememberAppLabel
import com.qingqi.adskip.presentation.designsystem.icons.AdSkipIcons
import com.qingqi.adskip.presentation.designsystem.theme.Spacing
import com.qingqi.adskip.presentation.designsystem.theme.StatusColors
import com.qingqi.adskip.presentation.designsystem.theme.TileAmber
import com.qingqi.adskip.presentation.designsystem.theme.TileAmberContainer
import com.qingqi.adskip.presentation.designsystem.theme.TileBrand
import com.qingqi.adskip.presentation.designsystem.theme.TileBrandContainer
import com.qingqi.adskip.presentation.designsystem.theme.TilePurple
import com.qingqi.adskip.presentation.designsystem.theme.TilePurpleContainer
import com.qingqi.adskip.presentation.designsystem.theme.UiSizes

/** 首页：状态与权限 → 使用概览 → 快捷入口 → 模拟测试。状态由系统真值驱动。 */
@Composable
fun HomeScreen(
    messenger: Messenger,
    onOpenLogs: () -> Unit = {},
    onOpenRules: () -> Unit = {},
    onOpenApps: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lastAppLabel = rememberAppLabel(state.lastApp)

    // 每次回到前台都重查无障碍状态（理由见 v3 注释：Activity 不重建时进程信号会过期）。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onScreenResumed()
    }

    LaunchedEffect(messenger) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UiEffect.ShowMessage -> messenger.show(effect.message)

                is UiEffect.KeywordRemoved -> messenger.showUndoable(
                    message = context.getString(R.string.keyword_removed, effect.keyword),
                    undoLabel = context.getString(R.string.action_undo),
                    onUndo = viewModel::undoRemoveKeyword,
                )

                is UiEffect.RecreateActivity -> Unit
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                // 浮层盖住首页时把底层内容移出语义树（理由见 v3 注释，2026-10-11 真机实测）。
                .then(
                    if (state.fakeAdVisible) Modifier.clearAndSetSemantics {} else Modifier,
                ),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = 0.dp,
                bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                AppHeader(
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.home_subtitle),
                    modifier = Modifier.padding(bottom = Spacing.sm),
                    trailingIcon = AdSkipIcons.Settings,
                    trailingLabel = stringResource(R.string.home_open_settings),
                    onTrailingClick = onOpenSettings,
                )
            }

            item {
                StatusShieldCard(
                    running = state.serviceRunning,
                    unknown = state.accessibilityUnknown,
                    onOpenSettings = {
                        if (!KeepAliveNavigator.openAccessibilitySettings(context)) {
                            messenger.show(context.getString(R.string.settings_open_failed))
                        }
                    },
                )
            }

            item {
                SectionCard(contentPadding = PaddingValues(vertical = Spacing.xs)) {
                    SettingRow(
                        title = stringResource(R.string.home_accessibility_service),
                        subtitle = stringResource(
                            if (state.accessibilityUnknown) {
                                R.string.status_unknown
                            } else if (state.serviceRunning) {
                                R.string.home_accessibility_granted
                            } else {
                                R.string.home_accessibility_pending
                            },
                        ),
                        icon = AdSkipIcons.Shield,
                        onClick = {
                            // 系统授权使用导航入口，不模拟可以在应用内切换的权限开关。
                            if (!KeepAliveNavigator.openAccessibilitySettings(context)) {
                                messenger.show(context.getString(R.string.settings_open_failed))
                            }
                        },
                    )
                }
            }

            item {
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.home_stats_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.home_stats_more),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(role = Role.Button, onClick = onOpenLogs),
                        )
                    }
                    Spacer(Modifier.height(Spacing.lg))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        StatTile(
                            value = state.totalSkips.toString(),
                            label = stringResource(R.string.stats_total_label),
                            emphasis = true,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = state.keywords.size.toString(),
                            label = stringResource(R.string.stats_keywords_label),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = lastAppLabel.ifBlank { stringResource(R.string.value_not_available) },
                            label = stringResource(R.string.stats_recent_label),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    QuickTile(
                        icon = AdSkipIcons.Clock,
                        label = stringResource(R.string.quick_open_logs),
                        contentColor = TileBrand,
                        containerColor = TileBrandContainer,
                        onClick = onOpenLogs,
                        modifier = Modifier.weight(1f),
                    )
                    QuickTile(
                        icon = AdSkipIcons.Tag,
                        label = stringResource(R.string.quick_add_keyword),
                        contentColor = TileAmber,
                        containerColor = TileAmberContainer,
                        onClick = onOpenRules,
                        modifier = Modifier.weight(1f),
                    )
                    QuickTile(
                        icon = AdSkipIcons.Apps,
                        label = stringResource(R.string.quick_open_apps),
                        contentColor = TilePurple,
                        containerColor = TilePurpleContainer,
                        onClick = onOpenApps,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                ActionCard(
                    icon = AdSkipIcons.Rocket,
                    title = stringResource(R.string.home_test_title),
                    description = stringResource(R.string.home_test_desc),
                    actionLabel = stringResource(R.string.home_test_action),
                    onAction = viewModel::startFakeAdTest,
                )
            }

            item {
                SectionHint(
                    text = stringResource(R.string.how_it_works),
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
        }

        AnimatedVisibility(
            visible = state.fakeAdVisible,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
        ) {
            FakeAdOverlay(
                countdown = state.countdown,
                onSkipClicked = viewModel::onManualSkipClicked,
            )
        }
    }
}

/** 紧凑状态卡：图标与文案共同区分三态；未生效时给出明确的系统设置入口。 */
@Composable
private fun StatusShieldCard(running: Boolean, unknown: Boolean, onOpenSettings: () -> Unit) {
    val container = when {
        unknown -> StatusColors.unknownContainer
        running -> StatusColors.onContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val accent = when {
        unknown -> StatusColors.unknown
        running -> StatusColors.on
        else -> StatusColors.off
    }
    val title = stringResource(
        when {
            unknown -> R.string.status_unknown
            running -> R.string.status_on
            else -> R.string.status_off
        },
    )
    val hint = stringResource(
        when {
            unknown -> R.string.status_unknown_hint
            running -> R.string.home_status_protected
            else -> R.string.status_off_hint
        },
    )

    SectionCard(containerColor = container) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconPlate(
                icon = if (unknown) AdSkipIcons.Info else AdSkipIcons.Shield,
                size = UiSizes.stateIllustration,
                glyphSize = UiSizes.stateIllustrationIcon,
                containerColor = container,
                contentColor = accent,
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!running) {
                Spacer(Modifier.height(Spacing.lg))
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.btn_open_settings))
                }
            }
        }
    }
}

/** 动作卡：图标 + 标题 + 说明 + 右侧动作按钮。 */
@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 图标底衬用 36dp 的 IconPlate，而不是 48dp 的瓦片底衬：
            // 设计稿这一行的图标与「设置行」同级（36dp），48dp 会让卡片看起来像被图标压住。
            IconPlate(icon = icon)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            FilledTonalButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

/**
 * 快捷入口瓦片：图标底衬 + 标签。
 *
 * 整张瓦片是**一个** `Role.Button` 语义节点（图标 `contentDescription = null`，
 * 语义由标签承担），读屏一次念完「查看记录，按钮」。
 */
@Composable
private fun QuickTile(
    icon: ImageVector,
    label: String,
    contentColor: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 瓦片必须是**卡片**：设计稿里三块快捷入口是白底 + 圆角 + 投影的独立卡片。
    // 此前只画了图标与标签（无容器），三个入口看起来像浮在页面上的散装图标，
    // 与上下两张卡片的视觉层级断裂，点按区域也缺少边界提示。
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = UiSizes.cardElevation),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Spacing.xs, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(UiSizes.tileIcon)
                    .clip(MaterialTheme.shapes.small)
                    .background(containerColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(UiSizes.tileGlyph),
                    tint = contentColor,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 模拟开屏广告浮层：服务生效时应被自动点击「跳过」。
 *
 * 语义树注意：本浮层**不能**自己挂 `clearAndSetSemantics`——那会把「跳过」按钮
 * 一起清掉，无障碍引擎与读屏都找不到它，自检必然超时（2026-10-11 真机实测复现）。
 * 隐藏底层内容的职责在 [HomeScreen] 调用处。
 */
@Composable
private fun FakeAdOverlay(countdown: Int, onSkipClicked: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.fake_ad_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.fake_ad_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xxxl))
        Button(onClick = onSkipClicked) {
            Text(stringResource(R.string.fake_ad_skip))
        }
        Spacer(Modifier.height(Spacing.lg))
        TagBadge(
            text = pluralStringResource(R.plurals.fake_ad_countdown, countdown, countdown),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
