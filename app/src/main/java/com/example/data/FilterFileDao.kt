package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterFileDao {
    @Query("SELECT * FROM filter_files ORDER BY path ASC")
    fun getAllFiles(): Flow<List<FilterFile>>

    @Query("SELECT * FROM filter_files WHERE path = :path LIMIT 1")
    suspend fun getFileByPath(path: String): FilterFile?

    @Query("SELECT * FROM filter_files WHERE path = :path LIMIT 1")
    fun getFileByPathFlow(path: String): Flow<FilterFile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FilterFile)

    @Query("DELETE FROM filter_files WHERE path = :path")
    suspend fun deleteFileByPath(path: String)

    @Query("DELETE FROM filter_files")
    suspend fun clearAllFiles()
}
