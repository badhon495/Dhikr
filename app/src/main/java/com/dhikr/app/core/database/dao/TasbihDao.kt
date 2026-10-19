package com.dhikr.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.dhikr.app.core.database.entity.TasbihEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TasbihDao {

    @Query("SELECT * FROM tasbih ORDER BY isBuiltIn DESC, name ASC")
    fun observeAll(): Flow<List<TasbihEntity>>

    @Query("SELECT * FROM tasbih WHERE isFavorite = 1 ORDER BY name ASC")
    fun observeFavorites(): Flow<List<TasbihEntity>>

    @Query("SELECT * FROM tasbih WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TasbihEntity?

    @Query(
        """
        SELECT * FROM tasbih
        WHERE name LIKE '%' || :query || '%'
           OR nameBn LIKE '%' || :query || '%'
           OR arabic LIKE '%' || :query || '%'
           OR pronunciation LIKE '%' || :query || '%'
           OR pronunciationBn LIKE '%' || :query || '%'
           OR translation LIKE '%' || :query || '%'
           OR translationBn LIKE '%' || :query || '%'
        ORDER BY name ASC
        """
    )
    fun search(query: String): Flow<List<TasbihEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tasbih: TasbihEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tasbih: List<TasbihEntity>)

    /** Backup restore: an id already present is overwritten by the backup row
     *  (merge semantics, backup wins). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasbih: List<TasbihEntity>)

    /** Startup sync: push corrected seed text onto an existing built-in row
     *  (insertAll's IGNORE leaves old seed text in place). User state —
     *  favourite, counts, reminders, cached benefits — is left untouched. */
    @Query(
        "UPDATE tasbih SET name = :name, nameBn = :nameBn, arabic = :arabic, " +
            "pronunciation = :pronunciation, pronunciationBn = :pronunciationBn, " +
            "translation = :translation, translationBn = :translationBn, " +
            "note = :note, noteBn = :noteBn, source = :source " +
            "WHERE id = :id AND isBuiltIn = 1",
    )
    suspend fun refreshBuiltInText(
        id: String,
        name: String,
        nameBn: String?,
        arabic: String,
        pronunciation: String,
        pronunciationBn: String?,
        translation: String,
        translationBn: String?,
        note: String,
        noteBn: String?,
        source: String?,
    )

    @Transaction
    suspend fun refreshBuiltInTexts(seed: List<TasbihEntity>) {
        seed.forEach {
            refreshBuiltInText(
                it.id, it.name, it.nameBn, it.arabic, it.pronunciation, it.pronunciationBn,
                it.translation, it.translationBn, it.note, it.noteBn, it.source,
            )
        }
    }

    @Query("SELECT * FROM tasbih")
    suspend fun getAll(): List<TasbihEntity>

    @Query("SELECT * FROM tasbih WHERE isBuiltIn = 0")
    suspend fun getAllCustom(): List<TasbihEntity>

    /** Routine sharing: pick out the tasbih a shared routine's steps reference. */
    @Query("SELECT * FROM tasbih WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<TasbihEntity>

    @Query("SELECT id FROM tasbih WHERE isBuiltIn = 1 AND isFavorite = 1")
    suspend fun getBuiltInFavoriteIds(): List<String>

    @Query("SELECT id FROM tasbih WHERE isBuiltIn = 1")
    suspend fun getBuiltInIds(): List<String>

    @Query("SELECT id FROM tasbih")
    suspend fun getAllIds(): List<String>

    @Update
    suspend fun update(tasbih: TasbihEntity)

    @Query("UPDATE tasbih SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query(
        "UPDATE tasbih SET benefitsText = :text, benefitsGeneratedAt = :generatedAt, " +
            "updatedAt = :generatedAt WHERE id = :id",
    )
    suspend fun updateBenefits(id: String, text: String, generatedAt: Long)

    @Query(
        "UPDATE tasbih SET reminderEnabled = :enabled, reminderMinuteOfDay = :minuteOfDay, " +
            "reminderDays = :days, updatedAt = :now WHERE id = :id"
    )
    suspend fun setReminder(id: String, enabled: Boolean, minuteOfDay: Int, days: Int, now: Long)

    @Query("SELECT * FROM tasbih WHERE reminderEnabled = 1")
    suspend fun tasbihWithRemindersRaw(): List<TasbihEntity>

    @Delete
    suspend fun delete(tasbih: TasbihEntity)

    /** Startup cleanup: drop retired built-in situational dhikr by id. */
    @Query("DELETE FROM tasbih WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM tasbih")
    suspend fun count(): Int
}
