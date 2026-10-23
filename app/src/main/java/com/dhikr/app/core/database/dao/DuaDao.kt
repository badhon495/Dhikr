package com.dhikr.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dhikr.app.core.database.entity.DuaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DuaDao {
    @Query("SELECT * FROM dua ORDER BY categoryOrder ASC, `order` ASC")
    fun observeAll(): Flow<List<DuaEntity>>

    @Query("SELECT * FROM dua WHERE id = :id")
    suspend fun getById(id: String): DuaEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(dua: List<DuaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DuaEntity)

    @Query("DELETE FROM dua WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM dua")
    suspend fun count(): Int
}
