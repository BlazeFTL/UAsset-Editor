package com.example.data

import kotlinx.coroutines.flow.Flow

class FilterFileRepository(private val dao: FilterFileDao) {
    val allFiles: Flow<List<FilterFile>> = dao.getAllFiles()

    suspend fun getFileByPath(path: String): FilterFile? {
        return dao.getFileByPath(path)
    }

    fun getFileByPathFlow(path: String): Flow<FilterFile?> {
        return dao.getFileByPathFlow(path)
    }

    suspend fun insertFile(file: FilterFile) {
        dao.insertFile(file)
    }

    suspend fun deleteFileByPath(path: String) {
        dao.deleteFileByPath(path)
    }

    suspend fun clearAllFiles() {
        dao.clearAllFiles()
    }
}
