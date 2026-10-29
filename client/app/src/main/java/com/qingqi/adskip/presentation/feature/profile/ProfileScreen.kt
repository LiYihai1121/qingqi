package com.qingqi.adskip.presentation.feature.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingqi.adskip.R
import com.qingqi.adskip.platform.device.KeepAliveNavigator
import com.qingqi.adskip.platform.device.LanguageMode
import com.qingqi.adskip.platform.device.PermissionKeys
import com.qingqi.adskip.platform.device.Vendor
import com.qingqi.adskip.presentation.common.Messenger
import com.qingqi.adskip.presentation.common.labelRes
import com.qingqi.adskip.presentation.designsystem.component.FullDivider
import com.qingqi.adskip.presentation.designsystem.component.InfoRow
import com.qingqi.adskip.presentation.designsystem.component.PageHeader
import com.qingqi.adskip.presentation.designsystem.component.SectionCard
import com.qingqi.adskip.presentation.designsystem.component.SettingRow
import com.qingqi.adskip.presentation.designsystem.component.TagBadge
import com.qingqi.adskip.presentation.designsystem.icons.AdSkipIcons
import com.qingqi.adskip.presentation.designsystem.theme.Spacing
import com.qingqi.adskip.presentation.designsystem.theme.UiSizes
import com.qingqi.adskip.presentation.feature.settings.SettingsContent
import com.qingqi.adskip.presentation.feature.settings.SettingsViewModel

/** 可展开的设置分组。同一时刻只展开一个，避免页面被无限拉长。 */
private enum class SettingSection { SERVICE, CLOUD, PRIVACY, LANGUAGE, ABOUT }

/** 设置索引与详情共享 ViewModel；返回键先回索引，避免内容在长列表底部展开。 */
@Composable
fun ProfileScreen(
    messenger: Messenger,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf<SettingSection?>(null) }

    val scrollState = rememberLazyListState()
    LaunchedEffect(expanded) { scrollState.scrollToItem(0) }

    // 每次回到前台都重读：统计可能已变化，且用户可能刚跳去系统无障碍设置改了开关。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshAccessibilityStatus()
        viewModel.refreshStats()
    }

    // 通知权限的系统运行时授权（Android 13+）。
    //
    // 为什么不在 App 内自建「请求通知权限」按钮，而是复用权限清单里的「去设置」：
    // 授权弹窗只能由系统呈现，且用户拒绝后再请求会有一段冷却期；
    // 因此清单这一行先尝试弹窗，未授予时退到系统通知设置页，两条路都通。
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        // 结果可能仍是「未授予」：重读系统真值，让清单显示真实状态而非乐观值。
        viewModel.refreshAccessibilityStatus()
    }

    val onOpenPermission: (String) -> Unit = { key ->
        val vendorName = viewModel.vendorName(state.vendor)
        when (key) {
            PermissionKeys.ACCESSIBILITY -> {
                if (!KeepAliveNavigator.openAccessibilitySettings(context)) {
                    messenger.show(context.getString(R.string.settings_open_failed))
                }
            }

            PermissionKeys.BATTERY -> {
                if (!KeepAliveNavigator.openBatteryOptimizationSettings(context)) {
                    messenger.show(context.getString(R.string.settings_open_failed))
                }
            }

            PermissionKeys.VENDOR_KEEPALIVE -> {
                val opened = KeepAliveNavigator.openKeepAliveSettings(context) != null
                val res = if (opened) R.string.settings_keepalive_opened else R.string.settings_keepalive_failed
                messenger.show(context.getString(res, vendorName))
            }

            PermissionKeys.NOTIFICATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    // 尚未授予：先走系统运行时授权弹窗（一步到位），
                    // 而不是把用户丢到系统设置页自己找「通知」开关。
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (!KeepAliveNavigator.openNotificationSettings(context)) {
                    messenger.show(context.getString(R.string.settings_open_failed))
                }
            }
        }
    }

    BackHandler(enabled = expanded != null) { expanded = null }
    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(
                when (expanded) {
                    SettingSection.SERVICE -> R.string.profile_row_service
                    SettingSection.CLOUD -> R.string.profile_row_cloud
                    SettingSection.PRIVACY -> R.string.profile_row_privacy
                    SettingSection.LANGUAGE -> R.string.profile_row_language
                    SettingSection.ABOUT -> R.string.profile_row_about
                    null -> R.string.profile_title
                },
            ),
            onBack = if (expanded != null) ({ expanded = null }) else null,
            backLabel = stringResource(R.string.action_back),
        )

        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (expanded == null) {
                item { UserCard(totalSkips = state.totalSkips, activeApps = state.activeAppCount) }

                item {
                    SectionCard(contentPadding = PaddingValues(vertical = Spacing.xs)) {
                        SettingRow(
                            icon = AdSkipIcons.Settings,
                            title = stringResource(R.string.profile_row_service),
                            subtitle = stringResource(R.string.profile_row_service_desc),
                            onClick = { expanded = expanded.toggle(SettingSection.SERVICE) },
                        )
                        RowDividerThin()
                        SettingRow(
                            icon = AdSkipIcons.Server,
                            title = stringResource(R.string.profile_row_cloud),
                            subtitle = stringResource(R.string.profile_row_cloud_desc),
                            onClick = { expanded = expanded.toggle(SettingSection.CLOUD) },
                        )
                        RowDividerThin()
                        SettingRow(
                            icon = AdSkipIcons.Shield,
                            title = stringResource(R.string.profile_row_privacy),
                            subtitle = stringResource(R.string.profile_row_privacy_desc),
                            onClick = { expanded = expanded.toggle(SettingSection.PRIVACY) },
                        )
                        RowDividerThin()
                        SettingRow(
                            icon = AdSkipIcons.Globe,
                            title = stringResource(R.string.profile_row_language),
                            subtitle = stringResource(settings.language.labelRes()),
                            onClick = { expanded = expanded.toggle(SettingSection.LANGUAGE) },
                        )
                        RowDividerThin()
                        SettingRow(
                            icon = AdSkipIcons.Info,
                            title = stringResource(R.string.profile_row_about),
                            subtitle = state.versionDisplay.ifBlank { stringResource(R.string.profile_value_unknown) },
                            onClick = { expanded = expanded.toggle(SettingSection.ABOUT) },
                        )
                    }
                }
            }

            if (expanded == SettingSection.SERVICE) {
                item {
                    PermissionCard(
                        items = state.permissionItems,
                        readyCount = state.permissionReadyCount,
                        onOpen = onOpenPermission,
                    )
                }
            }

            if (expanded == SettingSection.CLOUD) {
                item {
                    SettingsContent(messenger, viewModel = settingsViewModel)
                }
            }

            if (expanded == SettingSection.PRIVACY) {
                item {
                    SectionCard {
                        Text(
                            text = stringResource(R.string.profile_privacy_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (expanded == SettingSection.LANGUAGE) {
                item {
                    SectionCard(contentPadding = PaddingValues(vertical = Spacing.xs)) {
                        LanguageMode.entries.forEach { mode ->
                            LanguageOptionRow(
                                mode = mode,
                                selected = settings.language == mode,
                                onSelect = { settingsViewModel.setLanguage(mode) },
                            )
                        }
                    }
                }
            }

            if (expanded == SettingSection.ABOUT) {
                item {
                    AboutCard(state = state, vendorName = viewModel::vendorName)
                }
            }
        }
    }
}

/** 展开/收起切换：点已展开的那一行则收起。 */
private fun SettingSection?.toggle(target: SettingSection): SettingSection? = if (this == target) null else target

/** 产品概览与真实使用统计；不展示虚构账号或会员信息。 */
@Composable
private fun UserCard(totalSkips: Int, activeApps: Int) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(UiSizes.avatar)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AdSkipIcons.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(UiSizes.rowIcon),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.profile_local_user),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.profile_local_only_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = totalSkips.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.profile_active_apps_label, activeApps),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 语言选项行：单选。整行可点，读屏念「简体中文，已选中，单选按钮」。 */
@Composable
private fun LanguageOptionRow(mode: LanguageMode, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = stringResource(mode.labelRes()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 关于卡：版本与设备信息 + 外链。 */
@Composable
private fun AboutCard(state: ProfileViewModel.UiState, vendorName: (Vendor) -> String) {
    SectionCard(contentPadding = PaddingValues(vertical = Spacing.xs)) {
        InfoRow(
            label = stringResource(R.string.profile_version_label),
            value = state.versionDisplay.ifBlank { stringResource(R.string.profile_value_unknown) },
        )
        FullDivider()
        InfoRow(
            label = stringResource(R.string.profile_android_label),
            value = state.androidVersion.ifBlank { stringResource(R.string.profile_value_unknown) },
        )
        FullDivider()
        InfoRow(
            label = stringResource(R.string.profile_device_label),
            value = state.deviceModel.ifBlank { stringResource(R.string.profile_value_unknown) },
        )
        FullDivider()
        InfoRow(
            label = stringResource(R.string.profile_vendor_label),
            value = vendorName(state.vendor),
        )
        FullDivider()
        LinkRow(
            icon = AdSkipIcons.Globe,
            label = stringResource(R.string.profile_link_project),
            url = PROJECT_URL,
        )
        FullDivider()
        LinkRow(
            icon = AdSkipIcons.Feedback,
            label = stringResource(R.string.profile_link_feedback),
            url = FEEDBACK_URL,
        )
    }
}

/** 开源项目主页与问题反馈入口。 */
private const val PROJECT_URL = "https://github.com/LiYihai1121/qingqi"
private const val FEEDBACK_URL = "https://github.com/LiYihai1121/qingqi/issues"

/**
 * 链接行：图标 + 标题，整行可点，跳系统浏览器。
 *
 * 为什么直接用 `ACTION_VIEW` 而不经 KeepAliveNavigator：那收口的是「系统设置页」
 * 跳转（厂商差异大、需要按 ROM 分发），网页链接没有厂商差异。`runCatching`
 * 兜住无浏览器应用极端情况下的 ActivityNotFoundException，避免崩溃。
 */
@Composable
private fun LinkRow(icon: ImageVector, label: String, url: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(UiSizes.rowIcon),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 卡片内的细分隔线（两侧各内缩一个卡片内边距）。 */
@Composable
private fun RowDividerThin() {
    FullDivider(modifier = Modifier.padding(horizontal = Spacing.lg))
}
