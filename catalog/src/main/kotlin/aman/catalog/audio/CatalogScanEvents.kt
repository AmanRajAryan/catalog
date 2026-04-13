package aman.catalog.audio

/**
 * Result of a completed [Catalog.scan] operation.
 * Only emitted when at least one change was detected.
 */
data class ScanResult(
    val durationMs: Long,
    val newTracks: Int,
    val changedTracks: Int,
    val movedTracks: Int,
    val deletedTracks: Int
)

/**
 * Result of a completed [Catalog.applyConfigChanges] operation.
 * Always emitted after a config change that affects splitting logic.
 */
data class ConfigChangeResult(
    val durationMs: Long,
    val tracksProcessed: Int
)
