package com.dhikr.app.feature.dua

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.core.database.entity.DuaEntity
import com.dhikr.app.core.localization.AppLanguage
import com.dhikr.app.core.localization.LocalAppLanguage
import com.dhikr.app.core.localization.displayName
import com.dhikr.app.core.localization.displayPronunciation
import com.dhikr.app.core.localization.displayTranslation
import com.dhikr.app.ui.headingSemantics
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.DialogShape
import com.dhikr.app.ui.theme.ListRowShape
import com.dhikr.app.ui.theme.PillShape

@Composable
fun DuaScreen(
    viewModel: DuaViewModel,
    onBack: () -> Unit,
    onAddDua: () -> Unit,
    onEditDua: (id: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors
    val lang = LocalAppLanguage.current

    var deleteConfirmTarget by remember { mutableStateOf<DuaEntity?>(null) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val byCategoryFilter = if (categoryFilter == null) {
        state.categories
    } else {
        state.categories.filter { it.category == categoryFilter }
    }
    val visibleCategories = if (searchQuery.isBlank()) {
        byCategoryFilter
    } else {
        byCategoryFilter
            .map { categoryUi ->
                categoryUi.copy(
                    entries = categoryUi.entries.filter { entry ->
                        entry.displayName(lang).contains(searchQuery, ignoreCase = true) ||
                            entry.displayTranslation(lang).contains(searchQuery, ignoreCase = true)
                    },
                )
            }
            .filter { it.entries.isNotEmpty() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(horizontal = 16.dp),
    ) {
        // ---- Header: back arrow + title + add button ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        contentDescription = null,
                        tint = colors.text,
                    )
                }
                Text(
                    text = stringResource(R.string.dua_screen_title),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .headingSemantics(),
                )
            }
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(colors.sage)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.dua_screen_add)) { onAddDua() }
                    .minTapTarget()
                    .testTag("dua_add_button")
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.dua_screen_add),
                    tint = colors.onSage,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // ---- Search pill + category filter dropdown, same row ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(PillShape)
                    .background(colors.surface)
                    .padding(horizontal = 14.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.dua_search_content_description),
                    tint = colors.faint,
                    modifier = Modifier.size(16.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = searchQuery.isEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Text(
                            text = stringResource(R.string.dua_search_placeholder),
                            fontSize = 13.sp,
                            color = colors.faint,
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = colors.text),
                        cursorBrush = SolidColor(colors.text),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dua_search_field"),
                    )
                }
            }

            Box {
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(colors.surface)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.dua_filter_content_description),
                        ) { filterMenuOpen = true }
                        .minTapTarget()
                        .testTag("dua_filter_button")
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = categoryFilter?.let { categoryLabel(it) }
                                ?: stringResource(R.string.dua_filter_all),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.text,
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = colors.faint,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(16.dp),
                        )
                    }
                }
                DropdownMenu(expanded = filterMenuOpen, onDismissRequest = { filterMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.dua_filter_all)) },
                        onClick = {
                            categoryFilter = null
                            filterMenuOpen = false
                        },
                    )
                    state.categories.forEach { categoryUi ->
                        DropdownMenuItem(
                            text = { Text(categoryLabel(categoryUi.category)) },
                            onClick = {
                                categoryFilter = categoryUi.category
                                filterMenuOpen = false
                            },
                        )
                    }
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            visibleCategories.forEach { categoryUi ->
                item(key = "header_${categoryUi.category}") {
                    Text(
                        text = categoryLabel(categoryUi.category),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.faint,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(categoryUi.entries, key = { it.id }) { entry ->
                    DuaRow(
                        entry = entry,
                        lang = lang,
                        onEdit = { onEditDua(entry.id) },
                        onLongPress = { deleteConfirmTarget = entry },
                    )
                }
            }
            item { Box(modifier = Modifier.size(8.dp)) }
        }
    }

    deleteConfirmTarget?.let { dua ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            title = { Text(stringResource(R.string.dua_delete_confirm_title)) },
            text = { Text(stringResource(R.string.dua_delete_confirm_body)) },
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.dim,
            shape = DialogShape,
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDua(dua.id)
                    deleteConfirmTarget = null
                }) {
                    Text(
                        text = stringResource(R.string.dua_delete_confirm_action),
                        color = colors.terra,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTarget = null }) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DuaRow(
    entry: DuaEntity,
    lang: AppLanguage,
    onEdit: () -> Unit,
    onLongPress: () -> Unit,
) {
    val colors = DhikrTheme.colors

    Box(
        // The row's own tap target (opens the editor) is this outer
        // combinedClickable. The repeat-count pill below, when present, sits
        // in its own nested `clickable` Box — mirroring TasbihLibraryScreen's
        // favorite heart — so tapping it increments the local counter WITHOUT
        // also firing this row's onClick/opening the editor. Long-press opens
        // the delete confirmation.
        modifier = Modifier
            .fillMaxWidth()
            .clip(ListRowShape)
            .background(colors.card)
            .testTag("dua_row_${entry.id}")
            .combinedClickable(onClick = onEdit, onLongClick = onLongPress),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.displayName(lang),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                    )
                    entry.displayPronunciation(lang)?.let { pronunciation ->
                        Text(
                            text = pronunciation,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.dim,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Text(
                        text = entry.displayTranslation(lang),
                        fontSize = 12.5.sp,
                        color = colors.faint,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .testTag("dua_translation_${entry.id}"),
                    )
                    entry.source?.let { source ->
                        Text(
                            text = source,
                            fontSize = 11.sp,
                            color = colors.faint,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                if (entry.repeatCount > 1) {
                    var count by remember(entry.id) { mutableStateOf(0) }
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(colors.sageSoft)
                            .testTag("dua_counter_pill")
                            .clickable(role = Role.Button) {
                                count = (count + 1).coerceAtMost(entry.repeatCount)
                            }
                            .minTapTarget()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$count/${entry.repeatCount}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.sage,
                        )
                    }
                }
            }
            Text(
                text = entry.arabic,
                fontSize = 18.sp,
                color = colors.dim,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

/** Category label from a stringResource lookup keyed by [DuaCategoryUi.category]. */
@Composable
private fun categoryLabel(category: String): String {
    val resId = when (category) {
        "bathroom" -> R.string.dua_category_bathroom
        "eating" -> R.string.dua_category_eating
        "hardship" -> R.string.dua_category_hardship
        "house" -> R.string.dua_category_house
        "mosque" -> R.string.dua_category_mosque
        "nature" -> R.string.dua_category_nature
        "seeing" -> R.string.dua_category_seeing
        "travel" -> R.string.dua_category_travel
        else -> return category
    }
    return stringResource(resId)
}
