package dev.vibe.gallery.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val mediaId: Long)

@Entity(tableName = "resume")
data class ResumeEntity(@PrimaryKey val mediaId: Long, val positionMs: Long)

@Dao
interface GalleryDao {
    @Query("SELECT mediaId FROM favorites")
    fun observeFavoriteIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorites(items: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE mediaId IN (:ids)")
    suspend fun removeFavorites(ids: List<Long>)

    @Query("SELECT positionMs FROM resume WHERE mediaId = :id")
    suspend fun resumePosition(id: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveResume(entity: ResumeEntity)

    @Query("DELETE FROM resume WHERE mediaId = :id")
    suspend fun clearResume(id: Long)
}

@Database(entities = [FavoriteEntity::class, ResumeEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): GalleryDao
}
