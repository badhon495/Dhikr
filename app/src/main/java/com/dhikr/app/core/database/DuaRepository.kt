package com.dhikr.app.core.database

import com.dhikr.app.core.database.dao.DuaDao
import com.dhikr.app.core.database.entity.DuaEntity
import com.dhikr.app.core.database.seed.DuaSeedData
import kotlinx.coroutines.flow.Flow

class DuaRepository(private val dao: DuaDao) {
    fun observeAll(): Flow<List<DuaEntity>> = dao.observeAll()

    suspend fun seedIfEmpty() {
        if (dao.count() == 0) dao.insertAll(DuaSeedData.all)
    }

    suspend fun getById(id: String): DuaEntity? = dao.getById(id)

    suspend fun upsert(entity: DuaEntity) = dao.upsert(entity)

    suspend fun delete(id: String) = dao.delete(id)
}
