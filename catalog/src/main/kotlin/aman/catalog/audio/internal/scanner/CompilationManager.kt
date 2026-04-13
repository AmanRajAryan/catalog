package aman.catalog.audio.internal.scanner

import aman.catalog.audio.internal.database.CatalogDatabase

internal class CompilationManager(private val db: CatalogDatabase) {

    /**
     * Checks if a folder should be marked as a Compilation (Various Artists).
     *
     * LOGIC: A folder is ONLY a compilation if:
     * 1. It contains more than 3 distinct Artists.
     * 2. AND it contains tracks from exactly ONE valid Album Name.
     * 3. AND that Album Name is NOT a generic placeholder (e.g., "Unknown Album").
     *
     * This safeguard prevents "Messy Folders" (like /sdcard/Music or Downloads) from being
     * incorrectly grouped as a single "Various Artists" album just because the files lack
     * proper metadata.
     */
    suspend fun handleFolder(folderPath: String) {
        val distinctArtists = db.trackDao().getDistinctArtistsInFolder(folderPath)
        val distinctAlbums = db.trackDao().getDistinctAlbumsInFolder(folderPath)

        val albumName = distinctAlbums.firstOrNull() ?: ""

        // A blank or generic album name means these are loose untagged files, not a real
        // compilation — guard against incorrectly grouping them under "Various Artists".
        val isGenericAlbum =
            albumName.isBlank() ||
                albumName.equals("unknown album", ignoreCase = true) ||
                albumName.equals("<unknown>", ignoreCase = true) ||
                albumName.equals("download", ignoreCase = true) ||
                albumName.equals("downloads", ignoreCase = true)

        if (distinctArtists.size > 3 && distinctAlbums.size == 1 && !isGenericAlbum) {
            relinkCompilationAlbums(folderPath)
        } else {
            restoreArtistAlbums(folderPath)
        }
    }

    /**
     * Moves all albums in this folder under the "Various Artists" umbrella.
     *
     * Compilations often span multiple discs/folders, so we use an empty folderGroup ("")
     * to allow them to merge globally rather than being split by folder.
     */
    private suspend fun relinkCompilationAlbums(folderPath: String) {
        val vaId = db.artistDao().insertOrGetId("Various Artists")
        val albumNames = db.trackDao().getDistinctAlbumsInFolder(folderPath)

        albumNames.forEach { albumName ->
            val vaAlbumId = db.albumDao().insertOrGetId(albumName, vaId, "")
            db.trackDao().updateAlbumIdForFolder(folderPath, albumName, vaAlbumId)
        }
    }

    /**
     * Restores albums in this folder to their original Album Artist links.
     *
     * We always call insertOrGetId() even when the Album Artist tag is blank, mirroring
     * TrackIngestor's logic exactly. Passing null would cause AlbumDao to create a separate
     * row matched on `albumArtistId IS NULL`, orphaning the original album and making it
     * invisible in the Album Artists tab.
     */
    private suspend fun restoreArtistAlbums(folderPath: String) {
        val tracks = db.trackDao().getTracksForRestore(folderPath)

        tracks.forEach { track ->
            // STRICT RESTORATION: We only use the raw Album Artist tag.
            // We deliberately do NOT fall back to track.artistDisplay, because that would
            // cause Track Artists to incorrectly appear in the "Album Artists" tab.
            val targetArtistName =
                if (track.rawAlbumArtistString.isNotBlank()) {
                    track.rawAlbumArtistString
                } else {
                    ""
                }

            // Always call insertOrGetId() even for a blank name. Passing null instead would
            // cause AlbumDao to match on `albumArtistId IS NULL` — a different row from the
            // one TrackIngestor created — orphaning the album and making it invisible in the UI.
            val artistId = db.artistDao().insertOrGetId(targetArtistName)

            // STRICT LOCATION GROUPING:
            // If the Album Artist name is real, merge globally ("") so multi-disc albums
            // across folders collapse into one entry.
            // If the name is blank, lock to the folder path to prevent unrelated albums
            // with the same generic title from merging across folders.
            val folderGroup = if (targetArtistName.isNotBlank()) "" else folderPath

            val originalAlbumId = db.albumDao().insertOrGetId(track.albumDisplay, artistId, folderGroup)
            db.trackDao().updateAlbumIdOnly(track.id, originalAlbumId)
        }
    }
}
