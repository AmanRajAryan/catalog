package aman.catalogtest.ui.main

import aman.catalog.audio.models.SortOption
import aman.catalogtest.CatalogViewModel
import aman.catalogtest.navigation.AppTab
import aman.catalogtest.ui.albums.AlbumListScreen
import aman.catalogtest.ui.artists.ArtistListScreen
import aman.catalogtest.ui.categories.ComposerListScreen
import aman.catalogtest.ui.categories.FolderListScreen
import aman.catalogtest.ui.categories.GenreListScreen
import aman.catalogtest.ui.categories.LyricistListScreen
import aman.catalogtest.ui.categories.YearListScreen
import aman.catalogtest.ui.home.HomeScreen
import aman.catalogtest.ui.playlists.PlaylistListScreen
import aman.catalogtest.ui.search.SearchResultsScreen
import aman.catalogtest.ui.tracks.TrackListScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: CatalogViewModel,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (Long) -> Unit,
    onNavigateToAlbumArtist: (Long) -> Unit,
    onNavigateToPlaylist: (Long) -> Unit,
    onNavigateToCategory: (type: String, id: String, title: String) -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToEditor: (Long) -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope       = rememberCoroutineScope()

    val trackCount       by viewModel.tracks.collectAsState()
    val albumCount       by viewModel.albums.collectAsState()
    val artistCount      by viewModel.artists.collectAsState()
    val albumArtistCount by viewModel.albumArtists.collectAsState()
    val genreCount       by viewModel.genres.collectAsState()
    val playlistCount    by viewModel.playlists.collectAsState()
    val composerCount    by viewModel.composers.collectAsState()
    val lyricistCount    by viewModel.lyricists.collectAsState()
    val folderCount      by viewModel.folders.collectAsState()
    val yearCount        by viewModel.years.collectAsState()

    // Current sort per tab — read here so the checkmark in the menu stays current
    val trackSort       by viewModel.trackSort.collectAsState()
    val albumSort       by viewModel.albumSort.collectAsState()
    val artistSort      by viewModel.artistSort.collectAsState()
    val albumArtistSort by viewModel.albumArtistSort.collectAsState()
    val genreSort       by viewModel.genreSort.collectAsState()
    val composerSort    by viewModel.composerSort.collectAsState()
    val lyricistSort    by viewModel.lyricistSort.collectAsState()
    val folderSort      by viewModel.folderSort.collectAsState()
    val yearSort        by viewModel.yearSort.collectAsState()
    val playlistSort    by viewModel.playlistSort.collectAsState()

    val searchQuery   by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    var selectedTab  by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var showSortMenu by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState   = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DebugDrawerContent(viewModel = viewModel)
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            TopAppBar(
                title          = { Text("Catalog") },
                navigationIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                        }
                    }) {
                        Icon(Icons.Default.Menu, contentDescription = "Open debug drawer")
                    }
                },
                actions        = {
                    if (selectedTab != AppTab.HOME) {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.List, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded         = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                when (selectedTab) {
                                    AppTab.TRACKS -> SortOption.Track.entries.forEach { option ->
                                        SortMenuItem(option.name, option == trackSort) {
                                            viewModel.setTrackSort(option); showSortMenu = false
                                        }
                                    }
                                    AppTab.ALBUMS -> SortOption.Album.entries.forEach { option ->
                                        SortMenuItem(option.name, option == albumSort) {
                                            viewModel.setAlbumSort(option); showSortMenu = false
                                        }
                                    }
                                    AppTab.ARTISTS -> SortOption.Artist.entries.forEach { option ->
                                        SortMenuItem(option.name, option == artistSort) {
                                            viewModel.setArtistSort(option); showSortMenu = false
                                        }
                                    }
                                    AppTab.ALBUM_ARTISTS -> SortOption.Artist.entries.forEach { option ->
                                        SortMenuItem(option.name, option == albumArtistSort) {
                                            viewModel.setAlbumArtistSort(option); showSortMenu = false
                                        }
                                    }
                                    AppTab.GENRES -> SortOption.Genre.entries.forEach { option ->
                                        SortMenuItem(option.name, option == genreSort) {
                                            viewModel.setGenreSort(option); showSortMenu = false
                                        }
                                    }
                                    AppTab.COMPOSERS -> SortOption.Composer.entries.forEach { option ->
                                        SortMenuItem(option.name, option == composerSort) {
                                            viewModel.setComposerSort(option); showSortMenu = false
                                        }
                                    }
                                    AppTab.LYRICISTS -> SortOption.Lyricist.entries.forEach { option ->
                                        SortMenuItem(option.name, option == lyricistSort) {
                                            viewModel.setLyricistSort(option); showSortMenu = false
                                        }
                                    }
                                    AppTab.FOLDERS -> SortOption.Folder.entries.forEach { option ->
                                        SortMenuItem(option.name, option == folderSort) {
                                            viewModel.setFolderSort(option); showSortMenu = false
                                        }
                                    }
                                    AppTab.YEARS -> SortOption.Year.entries.forEach { option ->
                                        SortMenuItem(option.name, option == yearSort) {
                                            viewModel.setYearSort(option); showSortMenu = false
                                        }
                                    }
                                    AppTab.PLAYLISTS -> SortOption.Playlist.entries.forEach { option ->
                                        SortMenuItem(option.name, option == playlistSort) {
                                            viewModel.setPlaylistSort(option); showSortMenu = false
                                        }
                                    }
                                    AppTab.HOME -> { /* unreachable — button hidden on HOME */ }
                                }
                            }
                        }
                    }
                }
            )

            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                label         = { Text("Search library…") },
                singleLine    = true,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                trailingIcon  = {
                    if (searchQuery.isNotEmpty()) {
                        TextButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Text("Clear")
                        }
                    }
                }
            )

            if (searchQuery.isNotBlank()) {
                SearchResultsScreen(
                    result                  = searchResults,
                    viewModel               = viewModel,
                    onNavigateToAlbum       = onNavigateToAlbum,
                    onNavigateToArtist      = onNavigateToArtist,
                    onNavigateToAlbumArtist = onNavigateToAlbumArtist,
                    onNavigateToCategory    = onNavigateToCategory,
                    onNavigateToPlaylist    = onNavigateToPlaylist
                )
                return@Column
            }

            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding      = 8.dp
            ) {
                AppTab.entries.forEach { tab ->
                    val count = when (tab) {
                        AppTab.HOME          -> null
                        AppTab.TRACKS        -> trackCount.size
                        AppTab.ALBUMS        -> albumCount.size
                        AppTab.ARTISTS       -> artistCount.size
                        AppTab.ALBUM_ARTISTS -> albumArtistCount.size
                        AppTab.GENRES        -> genreCount.size
                        AppTab.PLAYLISTS     -> playlistCount.size
                        AppTab.COMPOSERS     -> composerCount.size
                        AppTab.LYRICISTS     -> lyricistCount.size
                        AppTab.FOLDERS       -> folderCount.size
                        AppTab.YEARS         -> yearCount.size
                    }
                    Tab(
                        selected = selectedTab == tab,
                        onClick  = { selectedTab = tab; showSortMenu = false },
                        text     = {
                            Text(if (count != null) "${tab.label} ($count)" else tab.label)
                        }
                    )
                }
            }

            HorizontalDivider()

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    AppTab.HOME -> HomeScreen(
                        viewModel               = viewModel,
                        onNavigateToFavorites   = onNavigateToFavorites,
                        onNavigateToAlbum       = onNavigateToAlbum,
                        onNavigateToArtist      = onNavigateToArtist,
                        onNavigateToAlbumArtist = onNavigateToAlbumArtist,
                        onNavigateToCategory    = onNavigateToCategory,
                        onNavigateToEditor      = onNavigateToEditor
                    )
                    AppTab.TRACKS -> TrackListScreen(
                        tracks                  = trackCount,
                        viewModel               = viewModel,
                        emptyMessage            = "No tracks found. Open the drawer and tap Scan.",
                        onNavigateToEditor      = onNavigateToEditor,
                        onNavigateToArtist      = onNavigateToArtist,
                        onNavigateToAlbumArtist = onNavigateToAlbumArtist,
                        onNavigateToAlbum       = onNavigateToAlbum,
                        onNavigateToCategory    = onNavigateToCategory
                    )
                    AppTab.ALBUMS -> AlbumListScreen(
                        albums      = albumCount,
                        onItemClick = { id, _ -> onNavigateToAlbum(id) }
                    )
                                        AppTab.ARTISTS -> ArtistListScreen(
                        artists       = artistCount,
                        viewModel     = viewModel,
                        isAlbumArtist = false,
                        onItemClick   = { id, _ -> onNavigateToArtist(id) }
                    )
                    AppTab.ALBUM_ARTISTS -> ArtistListScreen(
                        artists       = albumArtistCount,
                        viewModel     = viewModel,
                        isAlbumArtist = true, // Pass true for album artists
                        onItemClick   = { id, _ -> onNavigateToAlbumArtist(id) }
                    )
                    AppTab.GENRES -> GenreListScreen(
                        genres      = genreCount,
                        viewModel   = viewModel,
                        onItemClick = { id, name -> onNavigateToCategory("genre", id.toString(), name) }
                    )
                    AppTab.PLAYLISTS -> PlaylistListScreen(
                        playlists        = playlistCount,
                        viewModel        = viewModel,
                        onItemClick      = { id, _ -> onNavigateToPlaylist(id) },
                        onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                        onDeletePlaylist = { id -> viewModel.deletePlaylist(id) },
                        onRenamePlaylist = { id, name -> viewModel.renamePlaylist(id, name) },
                        onImportPlaylist = { file -> viewModel.importPlaylist(file) }
                    )
                    AppTab.COMPOSERS -> ComposerListScreen(
                        composers   = composerCount,
                        viewModel   = viewModel,
                        onItemClick = { id, name -> onNavigateToCategory("composer", id.toString(), name) }
                    )
                    AppTab.LYRICISTS -> LyricistListScreen(
                        lyricists   = lyricistCount,
                        viewModel   = viewModel,
                        onItemClick = { id, name -> onNavigateToCategory("lyricist", id.toString(), name) }
                    )
                    AppTab.FOLDERS -> FolderListScreen(
                        folders     = folderCount,
                        viewModel   = viewModel,
                        onItemClick = { path, name -> onNavigateToCategory("folder", path, name) }
                    )
                    AppTab.YEARS -> YearListScreen(
                        years       = yearCount,
                        viewModel   = viewModel,
                        onItemClick = { year -> onNavigateToCategory("year", year.year.toString(), year.year.toString()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SortMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text         = { Text(label) },
        onClick      = onClick,
        trailingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null) }
        } else null
    )
}
