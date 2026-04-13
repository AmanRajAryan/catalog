package aman.catalog.audio.internal.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "composers",
    indices = [Index(value = ["name"], unique = true)]
)
data class ComposerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val totalPlayTimeMs: Long = 0
)


