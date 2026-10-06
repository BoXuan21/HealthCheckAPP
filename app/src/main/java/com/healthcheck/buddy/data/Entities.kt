package com.healthcheck.buddy.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "meals")
data class MealEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO date (yyyy-MM-dd) the meal counts toward. */
    val day: String,
    val timestamp: Long,
    val name: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val photoPath: String? = null,
    val buddyNote: String? = null,
)

@Entity(tableName = "weights")
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: String,
    val timestamp: Long,
    val weightKg: Double,
)

@Entity(tableName = "progress_photos")
data class ProgressPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: String,
    val timestamp: Long,
    val path: String,
    val weightKg: Double?,
    val aiFeedback: String? = null,
)

@Entity(tableName = "chat")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val fromUser: Boolean,
    val text: String,
)

@Dao
interface MealDao {
    @Query("SELECT * FROM meals WHERE day = :day ORDER BY timestamp")
    fun forDay(day: String): Flow<List<MealEntry>>

    @Query("SELECT * FROM meals WHERE day >= :fromDay ORDER BY timestamp")
    suspend fun since(fromDay: String): List<MealEntry>

    @Insert suspend fun insert(meal: MealEntry): Long
    @Update suspend fun update(meal: MealEntry)
    @Delete suspend fun delete(meal: MealEntry)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weights ORDER BY timestamp")
    fun all(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weights ORDER BY timestamp DESC LIMIT :n")
    suspend fun latest(n: Int): List<WeightEntry>

    @Insert suspend fun insert(entry: WeightEntry): Long
    @Delete suspend fun delete(entry: WeightEntry)
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM progress_photos ORDER BY timestamp DESC")
    fun all(): Flow<List<ProgressPhoto>>

    @Query("SELECT * FROM progress_photos ORDER BY timestamp ASC LIMIT 1")
    suspend fun first(): ProgressPhoto?

    @Insert suspend fun insert(photo: ProgressPhoto): Long
    @Update suspend fun update(photo: ProgressPhoto)
    @Delete suspend fun delete(photo: ProgressPhoto)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat ORDER BY timestamp")
    fun all(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM (SELECT * FROM chat ORDER BY timestamp DESC LIMIT :n) ORDER BY timestamp")
    suspend fun recent(n: Int): List<ChatMessage>

    @Insert suspend fun insert(msg: ChatMessage): Long

    @Query("DELETE FROM chat")
    suspend fun clear()
}

@Database(
    entities = [MealEntry::class, WeightEntry::class, ProgressPhoto::class, ChatMessage::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meals(): MealDao
    abstract fun weights(): WeightDao
    abstract fun photos(): PhotoDao
    abstract fun chat(): ChatDao
}
