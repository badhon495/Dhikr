package com.dhikr.app.feature.dua

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.DialogShape
import com.dhikr.app.ui.theme.ListRowShape
import com.dhikr.app.ui.theme.NotoNaskhArabic
import com.dhikr.app.ui.theme.PillShape

@Composable
fun DuaEditorScreen(viewModel: DuaEditorViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors
    var showBangla by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // ---- Header: back chevron + title + delete (edit mode only) ----
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onBack() }
                        .minTapTarget()
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = colors.text,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = if (state.isEditMode) {
                        stringResource(R.string.dua_edit_title)
                    } else {
                        stringResource(R.string.dua_add_title)
                    },
                    fontSize = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                )
            }
            if (state.isEditMode) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { showDeleteConfirm = true }
                        .minTapTarget()
                        .testTag("dua_editor_delete")
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.dua_editor_delete),
                        tint = colors.terra,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        LabeledField(stringResource(R.string.dua_editor_category_label)) {
            PillTextField(
                value = state.category,
                onValueChange = viewModel::onCategoryChange,
                placeholder = stringResource(R.string.dua_editor_category_placeholder),
                testTag = "dua_editor_category",
            )
        }

        LabeledField(stringResource(R.string.dua_editor_name_label)) {
            PillTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                placeholder = stringResource(R.string.dua_editor_name_placeholder),
                testTag = "dua_editor_name",
            )
        }

        LabeledField(stringResource(R.string.dua_editor_arabic_label)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                PillTextField(
                    value = state.arabic,
                    onValueChange = viewModel::onArabicChange,
                    placeholder = stringResource(R.string.dua_editor_arabic_placeholder),
                    testTag = "dua_editor_arabic",
                    textStyle = TextStyle(
                        fontFamily = NotoNaskhArabic,
                        fontSize = 18.sp,
                        color = colors.text,
                    ),
                )
            }
        }

        LabeledField(stringResource(R.string.dua_editor_pronunciation_label)) {
            PillTextField(
                value = state.pronunciation,
                onValueChange = viewModel::onPronunciationChange,
                placeholder = stringResource(R.string.dua_editor_pronunciation_placeholder),
                testTag = "dua_editor_pronunciation",
            )
        }

        LabeledField(stringResource(R.string.dua_editor_translation_label)) {
            PillTextField(
                value = state.translation,
                onValueChange = viewModel::onTranslationChange,
                placeholder = stringResource(R.string.dua_editor_translation_placeholder),
                testTag = "dua_editor_translation",
            )
        }

        LabeledField(stringResource(R.string.dua_editor_source_label)) {
            PillTextField(
                value = state.source,
                onValueChange = viewModel::onSourceChange,
                placeholder = stringResource(R.string.dua_editor_source_placeholder),
                testTag = "dua_editor_source",
            )
        }

        LabeledField(stringResource(R.string.dua_editor_repeat_count_label)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                NumberField(
                    value = state.repeatCount,
                    onValueChange = viewModel::onRepeatCountChange,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepperButton(label = "−") {
                        viewModel.onRepeatCountChange(state.repeatCount - 1)
                    }
                    StepperButton(label = "+") {
                        viewModel.onRepeatCountChange(state.repeatCount + 1)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(ListRowShape)
                .background(colors.surface)
                .clickable(role = Role.Button) { showBangla = !showBangla }
                .testTag("dua_editor_toggle_bangla")
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = if (showBangla) {
                    stringResource(R.string.dua_editor_hide_bangla)
                } else {
                    stringResource(R.string.dua_editor_add_bangla_toggle)
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
            )
        }

        if (showBangla) {
            LabeledField(stringResource(R.string.dua_editor_name_bn_label)) {
                PillTextField(
                    value = state.nameBn,
                    onValueChange = { viewModel.onBanglaFieldChange(DuaBanglaField.NAME, it) },
                    placeholder = stringResource(R.string.dua_editor_name_bn_label),
                    testTag = "dua_editor_name_bn",
                )
            }
            LabeledField(stringResource(R.string.dua_editor_pronunciation_bn_label)) {
                PillTextField(
                    value = state.pronunciationBn,
                    onValueChange = { viewModel.onBanglaFieldChange(DuaBanglaField.PRONUNCIATION, it) },
                    placeholder = stringResource(R.string.dua_editor_pronunciation_bn_label),
                    testTag = "dua_editor_pronunciation_bn",
                )
            }
            LabeledField(stringResource(R.string.dua_editor_translation_bn_label)) {
                PillTextField(
                    value = state.translationBn,
                    onValueChange = { viewModel.onBanglaFieldChange(DuaBanglaField.TRANSLATION, it) },
                    placeholder = stringResource(R.string.dua_editor_translation_bn_label),
                    testTag = "dua_editor_translation_bn",
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 24.dp)
                .heightIn(min = 52.dp)
                .clip(PillShape)
                .background(if (state.isSaveEnabled) colors.terra else colors.track)
                .clickable(enabled = state.isSaveEnabled, role = Role.Button) { viewModel.save(onBack) }
                .testTag("dua_editor_save")
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.dua_editor_save),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.card,
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.dua_delete_confirm_title)) },
            text = { Text(stringResource(R.string.dua_delete_confirm_body)) },
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.dim,
            shape = DialogShape,
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete(onBack)
                }) {
                    Text(
                        text = stringResource(R.string.dua_delete_confirm_action),
                        color = colors.terra,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(
                        text = stringResource(R.string.dua_delete_cancel_action),
                        color = colors.dim,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )
    }
}

@Composable
private fun LabeledField(label: String, content: @Composable () -> Unit) {
    val colors = DhikrTheme.colors
    Column(modifier = Modifier.padding(bottom = 15.dp)) {
        Text(
            text = label.uppercase(),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.dim,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        content()
    }
}

/** 48dp pill input: `card` background, 1px `line` border, manual placeholder. */
@Composable
private fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    testTag: String,
    textStyle: TextStyle? = null,
) {
    val colors = DhikrTheme.colors
    val style = textStyle ?: TextStyle(fontSize = 14.sp, color = colors.text)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(ListRowShape)
            .background(colors.card)
            .border(1.dp, colors.line, ListRowShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, fontSize = 14.sp, color = colors.faint)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = false,
            textStyle = style,
            cursorBrush = SolidColor(colors.text),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
        )
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    val colors = DhikrTheme.colors
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(colors.surface)
            .clickable(role = Role.Button) { onClick() }
            .minTapTarget(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
    }
}

@Composable
private fun NumberField(value: Int, onValueChange: (Int) -> Unit) {
    val colors = DhikrTheme.colors
    var text by rememberSaveable(value) { mutableStateOf(value.toString()) }
    Box(
        modifier = Modifier
            .widthIn(min = 64.dp)
            .heightIn(min = 36.dp)
            .clip(PillShape)
            .background(colors.surface)
            .border(1.dp, colors.line, PillShape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(5)
                text = digits
                digits.toIntOrNull()?.let(onValueChange)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(colors.text),
            modifier = Modifier.testTag("dua_editor_repeat_count"),
        )
    }
}
