package aman.catalogtest.navigation

import android.net.Uri
import aman.catalogtest.CatalogViewModel
import aman.catalogtest.ui.albums.AlbumDetailScreen
import aman.catalogtest.ui.artists.ArtistDetailScreen
import aman.catalogtest.ui.categories.CategoryDetailScreen
import aman.catalogtest.ui.favorites.FavoritesScreen
import aman.catalogtest.ui.main.MainScreen
import aman.catalogtest.ui.playlists.PlaylistDetailScreen
import aman.catalogtest.ui.tracks.TrackEditorScreen
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun AppNavGraph(viewModel: CatalogViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            MainScreen(
                viewModel               = viewModel,
                onNavigateToAlbum       = { id -> navController.navigate(Routes.albumDetail(id)) },
                onNavigateToArtist      = { id -> navController.navigate(Routes.artistDetail(id)) },
                onNavigateToAlbumArtist = { id -> navController.navigate(Routes.albumArtistDetail(id)) },
                onNavigateToPlaylist    = { id -> navController.navigate(Routes.playlistDetail(id)) },
                onNavigateToCategory    = { type, id, title ->
                    navController.navigate(Routes.categoryDetail(type, id, title))
                },
                onNavigateToFavorites   = { navController.navigate(Routes.FAVORITES) },
                onNavigateToEditor      = { id -> navController.navigate(Routes.trackEditor(id)) }
            )
        }

        composable(Routes.FAVORITES) {
            FavoritesScreen(
                viewModel          = viewModel,
                onBack             = { navController.popBackStack() },
                onNavigateToEditor = { id -> navController.navigate(Routes.trackEditor(id)) }
            )
        }

        composable(
            route     = Routes.ALBUM_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("id") ?: return@composable
            AlbumDetailScreen(
                viewModel            = viewModel,
                albumId              = albumId,
                onBack               = { navController.popBackStack() },
                onNavigateToArtist   = { id, isAlbumArtist ->
                    if (isAlbumArtist) navController.navigate(Routes.albumArtistDetail(id))
                    else navController.navigate(Routes.artistDetail(id))
                },
                onNavigateToAlbum    = { id -> navController.navigate(Routes.albumDetail(id)) },
                onNavigateToCategory = { type, id, title ->
                    navController.navigate(Routes.categoryDetail(type, id, title))
                },
                onNavigateToEditor   = { id -> navController.navigate(Routes.trackEditor(id)) }
            )
        }

        composable(
            route     = Routes.ARTIST_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val artistId = backStackEntry.arguments?.getLong("id") ?: return@composable
            ArtistDetailScreen(
                viewModel               = viewModel,
                artistId                = artistId,
                isAlbumArtist           = false,
                onBack                  = { navController.popBackStack() },
                onNavigateToAlbum       = { id -> navController.navigate(Routes.albumDetail(id)) },
                onNavigateToArtist      = { id -> navController.navigate(Routes.artistDetail(id)) },
                onNavigateToAlbumArtist = { id -> navController.navigate(Routes.albumArtistDetail(id)) },
                onNavigateToCategory    = { type, id, title ->
                    navController.navigate(Routes.categoryDetail(type, id, title))
                },
                onNavigateToEditor      = { id -> navController.navigate(Routes.trackEditor(id)) }
            )
        }

        composable(
            route     = Routes.ALBUM_ARTIST_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val artistId = backStackEntry.arguments?.getLong("id") ?: return@composable
            ArtistDetailScreen(
                viewModel               = viewModel,
                artistId                = artistId,
                isAlbumArtist           = true,
                onBack                  = { navController.popBackStack() },
                onNavigateToAlbum       = { id -> navController.navigate(Routes.albumDetail(id)) },
                onNavigateToArtist      = { id -> navController.navigate(Routes.artistDetail(id)) },
                onNavigateToAlbumArtist = { id -> navController.navigate(Routes.albumArtistDetail(id)) },
                onNavigateToCategory    = { type, id, title ->
                    navController.navigate(Routes.categoryDetail(type, id, title))
                },
                onNavigateToEditor      = { id -> navController.navigate(Routes.trackEditor(id)) }
            )
        }

        composable(
            route     = Routes.PLAYLIST_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("id") ?: return@composable
            PlaylistDetailScreen(
                viewModel          = viewModel,
                playlistId         = playlistId,
                onBack             = { navController.popBackStack() },
                onNavigateToEditor = { id -> navController.navigate(Routes.trackEditor(id)) }
            )
        }

        composable(
            route     = Routes.CATEGORY_DETAIL,
            arguments = listOf(
                navArgument("type")  { type = NavType.StringType },
                navArgument("id")    { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val type      = backStackEntry.arguments?.getString("type")  ?: return@composable
            val id        = Uri.decode(backStackEntry.arguments?.getString("id")    ?: "0")
            val title     = Uri.decode(backStackEntry.arguments?.getString("title") ?: "")
            val numericId = id.toLongOrNull() ?: 0L

            CategoryDetailScreen(
                viewModel               = viewModel,
                category                = type,
                numericId               = numericId,
                stringId                = id,
                title                   = title,
                onBack                  = { navController.popBackStack() },
                onNavigateToAlbum       = { albumId -> navController.navigate(Routes.albumDetail(albumId)) },
                onNavigateToArtist      = { artistId -> navController.navigate(Routes.artistDetail(artistId)) },
                onNavigateToAlbumArtist = { artistId -> navController.navigate(Routes.albumArtistDetail(artistId)) },
                onNavigateToCategory    = { t, i, ti -> navController.navigate(Routes.categoryDetail(t, i, ti)) },
                onNavigateToEditor      = { trackId -> navController.navigate(Routes.trackEditor(trackId)) }
            )
        }

        composable(
            route     = Routes.TRACK_EDITOR,
            arguments = listOf(navArgument("trackId") { type = NavType.LongType })
        ) { backStackEntry ->
            val trackId = backStackEntry.arguments?.getLong("trackId") ?: return@composable
            TrackEditorScreen(
                viewModel = viewModel,
                trackId   = trackId,
                onBack    = { navController.popBackStack() }
            )
        }
    }
}
