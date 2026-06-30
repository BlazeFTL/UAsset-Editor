package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "filter_files")
data class FilterFile(
    @PrimaryKey val path: String,
    val content: String,
    val sha: String,
    val lastSynced: Long,
    val isModified: Boolean = false,
    val localContent: String? = null
)
