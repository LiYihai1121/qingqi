package com.qingqi.adskip.presentation.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qingqi.adskip.R
import com.qingqi.adskip.presentation.designsystem.component.IconPlate
import com.qingqi.adskip.presentation.designsystem.icons.AdSkipIcons
import com.qingqi.adskip.presentation.designsystem.theme.Brand05
import com.qingqi.adskip.presentation.designsystem.theme.Brand10
import com.qingqi.adskip.presentation.designsystem.theme.Brand20
import com.qingqi.adskip.presentation.designsystem.theme.Brand50
import com.qingqi.adskip.presentation.designsystem.theme.Spacing
import com.qingqi.adskip.presentation.designsystem.theme.UiSizes

/** 单屏引导：解释用途后再进入首页授权；全部颜色跟随全局主题。 */
@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.huge))

        // 品牌标识：与首页头部同构（蓝底圆角方块 + 盾形），保证「同一款应用」的观感连续。
        Box(
            modifier = Modifier
                .size(UiSizes.statusShield)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AdSkipIcons.Shield,
                contentDescription = null,
                modifier = Modifier.size(UiSizes.statusShieldGlyph),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }

        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.xxl))
        OnboardingIllustration()
        Spacer(Modifier.height(Spacing.xxl))

        OnboardingPoint(stringResource(R.string.onboarding_point_1))
        OnboardingPoint(stringResource(R.string.onboarding_point_2))
        OnboardingPoint(stringResource(R.string.onboarding_point_3))
        OnboardingPoint(stringResource(R.string.onboarding_point_4))

        Spacer(Modifier.height(Spacing.xxl))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_start))
        }

        Spacer(Modifier.height(Spacing.xxl))
    }
}

/**
 * 插图：两台叠放的圆角设备 + 一枚「AD」徽标。
 *
 * 用纯几何图形表达「手机上的广告被拦下」，不需要位图资源——
 * 位图会因为密度、深色模式、不同宽高比各自需要一套，而几何图形自动适配全部。
 */
@Composable
private fun OnboardingIllustration() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(UiSizes.illustrationHeight),
        contentAlignment = Alignment.Center,
    ) {
        // 后面那台「设备」
        Box(
            modifier = Modifier
                .size(width = UiSizes.illustrationBackWidth, height = UiSizes.illustrationBackHeight)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(Spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface),
            )
        }
        // 前面那枚徽标：AD 被拦下
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconPlate(
                icon = AdSkipIcons.Shield,
                size = UiSizes.tileIcon,
                glyphSize = UiSizes.tileGlyph,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = stringResource(R.string.onboarding_badge),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 单条卖点：对勾 + 文案。 */
@Composable
private fun OnboardingPoint(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            imageVector = AdSkipIcons.Check,
            contentDescription = null,
            modifier = Modifier.size(UiSizes.inlineIcon),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
