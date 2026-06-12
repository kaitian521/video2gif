package com.dodotechhk.video2gif.works

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * 一条导出作品记录(Creation 页)。产物本体在 MediaStore(相册),这里只存索引;
 * [uri] 为相册 `content://` Uri 字符串。外部删除后由 Creation 页加载时校验并清理。
 */
@Entity(tableName = "export_records")
data class ExportRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    /** ExportFormat.name(Gif/Mp4/WebP)。 */
    val format: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val createdAt: Long,
)

@Dao
interface ExportRecordDao {
    @Insert
    suspend fun insert(record: ExportRecord)

    @Query("SELECT * FROM export_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ExportRecord>>

    @Query("DELETE FROM export_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/** 作品库(单例)。 */
@Database(entities = [ExportRecord::class], version = 1, exportSchema = false)
abstract class WorksDatabase : RoomDatabase() {
    abstract fun dao(): ExportRecordDao

    companion object {
        @Volatile
        private var instance: WorksDatabase? = null

        fun get(context: Context): WorksDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WorksDatabase::class.java,
                    "works.db",
                ).build().also { instance = it }
            }
    }
}
