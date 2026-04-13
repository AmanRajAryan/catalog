package aman.catalog.audio.internal

import aman.catalog.audio.internal.database.CatalogDatabase
import aman.catalog.audio.models.*
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class SearchEngine(private val db: CatalogDatabase) {

  fun search(query: String, filters: Set<SearchFilter>): Flow<SearchResult> {

    // Fast path: return empty result immediately for blank queries
    if (query.isBlank()) {
      return flowOf(SearchResult(query = query))
    }

    // 1. Tracks (custom SQL with tokenized AND matching across fields)
    val trackFlow: Flow<List<Track>> =
            if (SearchFilter.TRACKS in filters) {

              val tokens = query.trim().split(Regex("\\s+"))

              val sql = StringBuilder("SELECT * FROM tracks WHERE ")
              val args = ArrayList<Any>()

              tokens.forEachIndexed { index, token ->
                if (index > 0) sql.append(" AND ")
                sql.append("(title LIKE ? OR artistDisplay LIKE ? OR albumDisplay LIKE ?)")
                args.add("%$token%")
                args.add("%$token%")
                args.add("%$token%")
              }

              sql.append(" ORDER BY title ASC")

              val simpleQuery = SimpleSQLiteQuery(sql.toString(), args.toTypedArray())

              db.trackDao().searchTracksRaw(simpleQuery).map { list ->
                list.map { ModelMapper.toTrack(it) }
              }
            } else flowOf(emptyList())

    // 2. Artists
    val artistFlow =
            if (SearchFilter.ARTISTS in filters) {
              db.artistDao().searchArtists(query).map { list ->
                list.map { ModelMapper.toArtist(it) }
              }
            } else flowOf(emptyList())

    // 3. Album Artists
    val albumArtistFlow =
            if (SearchFilter.ALBUM_ARTISTS in filters) {
              db.artistDao().searchAlbumArtists(query).map { list ->
                list.map { ModelMapper.toArtist(it) }
              }
            } else flowOf(emptyList())

    // 4. Albums
    val albumFlow =
            if (SearchFilter.ALBUMS in filters) {
              db.albumDao().searchAlbums(query).map { list -> list.map { ModelMapper.toAlbum(it) } }
            } else flowOf(emptyList())

    // 5. Genres
    val genreFlow =
            if (SearchFilter.GENRES in filters) {
              db.genreDao().searchGenres(query).map { list -> list.map { ModelMapper.toGenre(it) } }
            } else flowOf(emptyList())

    // 6. Composers
    val composerFlow =
            if (SearchFilter.COMPOSERS in filters) {
              db.composerDao().searchComposers(query).map { list ->
                list.map { ModelMapper.toComposer(it) }
              }
            } else flowOf(emptyList())

    // 7. Lyricists
    val lyricistFlow =
            if (SearchFilter.LYRICISTS in filters) {
              db.lyricistDao().searchLyricists(query).map { list ->
                list.map { ModelMapper.toLyricist(it) }
              }
            } else flowOf(emptyList())

    // 8. Playlists
    val playlistFlow =
            if (SearchFilter.PLAYLISTS in filters) {
              db.playlistDao().searchPlaylists(query).map { list ->
                list.map { item ->
                  ModelMapper.toPlaylist(
                          item.playlist,
                          item.trackCount,
                          item.totalDuration,
                          item.coverArtPath?.let { ArtPath(it, item.coverArtDateModified) }
                  )
                }
              }
            } else flowOf(emptyList())

    // 9. Folders
    val folderFlow =
            if (SearchFilter.FOLDERS in filters) {
              db.folderDao().searchFolders(query).map { list ->
                list.map { ModelMapper.toFolder(it) }
              }
            } else flowOf(emptyList())

    val yearFlow =
            if (SearchFilter.YEARS in filters) {
              db.yearDao().searchYears(query).map { list ->
                list.map { ModelMapper.toYear(it) }
              }
            } else flowOf(emptyList())

    // combine() maxes out at 5 flows, so we split into two groups and combine the results.
    val groupA =
            combine(trackFlow, artistFlow, albumArtistFlow, albumFlow, playlistFlow) {
                    tracks,
                    artists,
                    albumArtists,
                    albums,
                    playlists ->
              SearchResultPartialA(tracks, artists, albumArtists, albums, playlists)
            }

    val groupB =
            combine(genreFlow, composerFlow, lyricistFlow, folderFlow, yearFlow) {
                    genres,
                    composers,
                    lyricists,
                    folders,
                    years ->
              SearchResultPartialB(genres, composers, lyricists, folders, years)
            }

    return combine(groupA, groupB) { a, b ->
      SearchResult(
              query = query,
              tracks = a.tracks,
              artists = a.artists,
              albumArtists = a.albumArtists,
              albums = a.albums,
              genres = b.genres,
              composers = b.composers,
              lyricists = b.lyricists,
              playlists = a.playlists,
              folders = b.folders,
              years = b.years
      )
    }
  }

  private data class SearchResultPartialA(
          val tracks: List<Track>,
          val artists: List<Artist>,
          val albumArtists: List<Artist>,
          val albums: List<Album>,
          val playlists: List<Playlist>
  )

  private data class SearchResultPartialB(
          val genres: List<Genre>,
          val composers: List<Composer>,
          val lyricists: List<Lyricist>,
          val folders: List<Folder>,
          val years: List<Year>
  )
}
