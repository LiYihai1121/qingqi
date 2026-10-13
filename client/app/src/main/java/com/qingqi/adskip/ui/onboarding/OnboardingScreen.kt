package com.qingqi.adskip.ui.onboarding

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.qingqi.adskip.R
import com.qingqi.adskip.ui.theme.Spacing
import com.qingqi.adskip.ui.theme.UiSizes
import com.qingqi.adskip.ui.theme.icons.AdSkipIcons

/** First-run introduction. It explains capabilities but never enables permissions without consent. */
@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(Spacing.sm))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(UiSizes.onboardingLogo).background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                    ),
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AdSkipIcons.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(UiSizes.onboardingShield),
                )
                Icon(
                    imageVector = AdSkipIcons.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(UiSizes.onboardingBolt),
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                FeatureRow(AdSkipIcons.Search, R.string.onboarding_feature_smart)
                FeatureRow(AdSkipIcons.Shield, R.string.onboarding_feature_secure)
                FeatureRow(AdSkipIcons.Globe, R.string.onboarding_feature_sync)
                FeatureRow(AdSkipIcons.Logs, R.string.onboarding_feature_local)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.onboarding_permission_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.md))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(UiSizes.onboardingButtonHeight),
            ) {
                Text(stringResource(R.string.onboarding_start))
            }
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, labelRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(UiSizes.rowIcon),
        )
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
    }
}
