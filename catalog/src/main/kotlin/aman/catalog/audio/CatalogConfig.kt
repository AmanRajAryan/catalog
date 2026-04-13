package aman.catalog.audio

/**
 * Configuration for the Catalog library.
 *
 * @param splitExceptions List of artist names that should NOT be split.
 * @param scannerIgnores List of folder paths to ignore during scanning.
 * @param customSplitters List of additional delimiters to split artists/genres.
 * @param minDurationMs Minimum duration (in milliseconds) for a track to be indexed. Default: 10000ms (10s).
 */
data class CatalogConfig(
    val splitExceptions: List<String> = emptyList(),
    val scannerIgnores: List<String> = emptyList(),
    val customSplitters: List<String> = emptyList(),
    val minDurationMs: Long = 10000L
)
