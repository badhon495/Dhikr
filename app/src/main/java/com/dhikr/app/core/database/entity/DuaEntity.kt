package com.dhikr.app.core.database.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "dua")
data class DuaEntity(
    @PrimaryKey val id: String,
    val category: String,
    val categoryOrder: Int,
    val order: Int,
    val name: String,
    val nameBn: String? = null,
    val arabic: String,
    val pronunciation: String? = null,
    val pronunciationBn: String? = null,
    val translation: String,
    val translationBn: String? = null,
    val source: String? = null,
    val repeatCount: Int = 1,
)
