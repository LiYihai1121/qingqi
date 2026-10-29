package com.qingqi.adskip.presentation.feature.logs

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingqi.adskip.R
import com.qingqi.adskip.data.repository.StatsRepository
import com.qingqi.adskip.presentation.common.Messenger
import com.qingqi.adskip.presentation.designsystem.component.ConfirmDialog
import com.qingqi.adskip.presentation.designsystem.component.EmptyState
import com.qingqi.adskip.presentation.designsystem.component.PageHeader
import com.qingqi.adskip.presentation.designsystem.component.SegmentedTabs
import com.qingqi.adskip.presentation.designsystem.component.rememberAppLabel
import com.qingqi.adskip.presentation.designsystem.icons.AdSkipIcons
import com.qingqi.adskip.presentation.designsystem.theme.Brand40
import com.qingqi.adskip.presentation.designsystem.theme.Brand70
import com.qingqi.adskip.presentation.designsystem.theme.Brand90
import com.qingqi.adskip.presentation.designsystem.theme.LogsDarkBackground
import com.qingqi.adskip.presentation.designsystem.theme.LogsDarkOnSurface
import com.qingqi.adskip.presentation.designsystem.theme.LogsDarkOnSurfaceVariant
import com.qingqi.adskip.presentation.designsystem.theme.LogsDarkOutline
import com.qingqi.adskip.presentation.designsystem.theme.LogsDarkSurface
import com.qingqi.adskip.presentation.designsystem.theme.LogsDarkSurfaceHigh
import com.qingqi.adskip.presentation.designsystem.theme.Spacing
import com.qingqi.adskip.presentation.designsystem.theme.StatusColors
import com.qingqi.adskip.presentation.designsystem.theme.UiSizes

/** 时间范围筛选（设计稿的「全部 / 今天 / 近 7 天 / 近 30 天」）。 */
private enum class LogsRange { ALL, TODAY, WEEK, MONTH }

/** 记录页遵循全局主题，保留筛选、分享、清空确认与撤销。 */
@Composable
fun LogsScreen(messenger: Messenger, viewModel: LogsViewModel = viewModel(factory = LogsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var range by remember { mutableStateOf(LogsRange.ALL) }
    var confirmClear by remember { mutableStateOf(false) }

    val filtered = remember(state.logs, range) {
        val now = System.currentTimeMillis()
        state.logs.filter { entry ->
            when (range) {
                LogsRange.ALL -> true
                LogsRange.TODAY -> now - entry.ts < ONE_DAY_MS
                LogsRange.WEEK -> now - entry.ts < SEVEN_DAYS_MS
                LogsRange.MONTH -> now - entry.ts < THIRTY_DAYS_MS
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PageHeader(
            title = stringResource(R.string.logs_title),
            subtitle = stringResource(R.string.logs_retention_note),
            onAction = {
                val text = viewModel.shareText()
                if (text == null) {
                    messenger.show(context.getString(R.string.logs_empty_share))
                } else {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            },
                            context.getString(R.string.logs_share_title),
                        ),
                    )
                }
            },
            actionLabel = stringResource(R.string.action_share),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                SegmentedTabs(
                    options = LogsRange.entries,
                    selected = range,
                    onSelect = { range = it },
                    labelOf = { option ->
                        when (option) {
                            LogsRange.ALL -> stringResource(R.string.logs_filter_all)
                            LogsRange.TODAY -> stringResource(R.string.logs_filter_today)
                            LogsRange.WEEK -> stringResource(R.string.logs_filter_week)
                            LogsRange.MONTH -> stringResource(R.string.logs_filter_month)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.logs_empty_title),
                        subtitle = stringResource(R.string.logs_empty_hint),
                        icon = AdSkipIcons.EmptyLogs,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            } else {
                filtered.forEach { entry ->
                    item(key = "${entry.ts}-${entry.pkg}") {
                        LogRow(
                            entry = entry,
                            time = viewModel.formatTimestamp(entry.ts),
                        )
                    }
                }

                item {
                    // 「清空」是页面级收尾动作，设计稿居中；TextButton 默认只占内容宽度，
                    // 直接放在 LazyColumn item 里会贴左，看起来像漏排了一行。
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        TextButton(onClick = { confirmClear = true }) {
                            Icon(
                                imageVector = AdSkipIcons.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(UiSizes.inlineIcon),
                                tint = StatusColors.off,
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                text = stringResource(R.string.logs_clear),
                                style = MaterialTheme.typography.labelLarge,
                                color = StatusColors.off,
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.logs_clear_confirm_title),
            message = stringResource(R.string.logs_clear_confirm_message),
            onConfirm = {
                viewModel.clear()
                confirmClear = false
                messenger.showUndoable(
                    message = context.getString(R.string.logs_cleared),
                    undoLabel = context.getString(R.string.action_undo),
                    onUndo = viewModel::undoClear,
                )
            },
            onDismiss = { confirmClear = false },
        )
    }
}

/**
 * 记录行：应用徽标 + 「应用名 · 命中词」+ 时间 + 结果。
 *
 * 结果文字用状态色而不是纯装饰色：这一行要回答的是「这次到底跳过了没有」，
 * 属于状态语义，因此走 [StatusColors]，与首页状态、权限清单同源。
 */
@Composable
private fun LogRow(entry: StatsRepository.LogEntry, time: String) {
    val appLabel = rememberAppLabel(entry.pkg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(UiSizes.appIcon)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = appLabel.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (entry.label.isBlank()) appLabel else "$appLabel · ${entry.label}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = AdSkipIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(UiSizes.inlineIcon),
                tint = StatusColors.on,
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = stringResource(R.string.logs_result_skipped),
                style = MaterialTheme.typography.labelMedium,
                color = StatusColors.on,
                maxLines = 1,
            )
        }
    }
}

private const val ONE_DAY_MS = 24L * 60 * 60 * 1000
private const val SEVEN_DAYS_MS = 7 * ONE_DAY_MS
private const val THIRTY_DAYS_MS = 30 * ONE_DAY_MS
