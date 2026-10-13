package com.qingqi.adskip.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingqi.adskip.R
import com.qingqi.adskip.ui.Messenger
import com.qingqi.adskip.ui.UiEffect
import com.qingqi.adskip.ui.components.EmptyState
import com.qingqi.adskip.ui.components.PageHeader
import com.qingqi.adskip.ui.home.HomeViewModel
import com.qingqi.adskip.ui.theme.Spacing
import com.qingqi.adskip.ui.theme.UiSizes
import com.qingqi.adskip.ui.theme.icons.AdSkipIcons

/** Dedicated keyword rules page. Add/remove operations use the same repository as HomeScreen. */
@Composable
fun RulesScreen(messenger: Messenger, viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    var input by rememberSaveable { mutableStateOf("") }
    val visible = state.keywords.withIndex().filter { it.value.contains(query.trim(), ignoreCase = true) }

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

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.rules_title),
            subtitle = stringResource(R.string.rules_subtitle, state.keywords.size),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.padding(horizontal = Spacing.lg).fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            placeholder = { Text(stringResource(R.string.rules_search_hint)) },
            leadingIcon = { Icon(AdSkipIcons.Search, contentDescription = null) },
        )
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                placeholder = { Text(stringResource(R.string.keyword_input_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (input.isNotBlank()) {
                        viewModel.addKeyword(input)
                        input = ""
                        keyboard?.hide()
                    }
                }),
            )
            Button(
                onClick = {
                    viewModel.addKeyword(input)
                    if (input.isNotBlank()) {
                        input = ""
                        keyboard?.hide()
                    }
                },
                modifier = Modifier.size(width = UiSizes.ruleAddButtonWidth, height = UiSizes.touchTarget),
                contentPadding = PaddingValues(horizontal = Spacing.sm),
            ) {
                Icon(AdSkipIcons.Plus, contentDescription = null)
                Text(stringResource(R.string.btn_add))
            }
        }
        if (visible.isEmpty()) {
            EmptyState(
                icon = AdSkipIcons.Search,
                title = stringResource(if (state.keywords.isEmpty()) R.string.rules_empty else R.string.rules_no_match),
                subtitle = stringResource(R.string.rules_empty_hint),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(visible, key = { it.value }) { indexed ->
                    Surface(
                        shape = RoundedCornerShape(Spacing.lg),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = Spacing.lg,
                                    end = Spacing.xs,
                                    top = Spacing.xs,
                                    bottom = Spacing.xs,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(vertical = Spacing.sm)) {
                                Text(indexed.value, style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(Spacing.xs))
                                Text(
                                    stringResource(R.string.rules_system_tag),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = { viewModel.removeKeyword(indexed.index) }) {
                                Icon(
                                    AdSkipIcons.Delete,
                                    contentDescription = stringResource(R.string.keyword_remove_cd, indexed.value),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
