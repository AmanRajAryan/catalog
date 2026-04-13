package aman.catalog.audio.internal.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ignored_folders")
data class IgnoredFolderEntity(
    @PrimaryKey val path: String, // e.g. "/storage/emulated/0/WhatsApp/Media"
    val dateAdded: Long
)
