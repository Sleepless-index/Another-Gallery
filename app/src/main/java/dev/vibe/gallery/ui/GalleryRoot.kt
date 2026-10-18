package dev.vibe.gallery.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.vibe.gallery.data.MediaItem
import dev.vibe.gallery.editor.EditorScreen
import dev.vibe.gallery.ui.theme.GIcons

private val TabRoutes = setOf("photos", "albums", "search")

@Composable
fun GalleryRoot(
    vm: GalleryViewModel,
    perms: PermState,
    onGrantAll: () -> Unit,
    onAuthenticate: (onSuccess: () -> Unit) -> Unit,
    onSecure: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    // Without access to all files the app can't do its job, so this is the only screen until it's granted.
    if (!perms.allFiles) {
        PermissionsScreen(state = perms, onGrantAll = onGrantAll)
        return
    }

    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val selectMode by vm.selectMode.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isTab = route in TabRoutes

    val vaultOpen by vm.vaultUnlocked.collectAsStateWithLifecycle()
    LaunchedEffect(vaultOpen, route) { onSecure(vaultOpen && (route == "vault" || route?.startsWith("viewer") == true || route?.startsWith("player") == true || route?.startsWith("editor") == true)) }
    // Leaving the vault locks it again.
    LaunchedEffect(route) {
        if (vaultOpen && route != null && route != "vault" && !route.startsWith("viewer") && !route.startsWith("player")) vm.lockVault()
    }

    BackHandler(enabled = selectMode) { vm.clearSelection() }
    LaunchedEffect(route) { vm.clearSelection() }

    val openViewer: (List<MediaItem>, Int) -> Unit = { list, index ->
        vm.setViewerList(list)
        nav.navigate("viewer/$index")
    }

    Box(Modifier.fillMaxSize().background(cs.background)) {
        NavHost(
            navController = nav,
            startDestination = "photos",
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(240)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(240)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable("photos") { PhotosScreen(vm, onOpen = openViewer, onOpenSettings = { nav.navigate("settings") }) }
            composable("albums") {
                AlbumsScreen(
                    vm = vm,
                    onOpenAlbum = { id -> nav.navigate("album/$id") },
                    onOpenSmart = { kind -> nav.navigate("smart/$kind") },
                    onOpenSettings = { nav.navigate("settings") },
                    onOpenVault = { nav.navigate("vault") },
                )
            }
            composable("search") { SearchScreen(vm, onOpen = openViewer) }
            composable(
                route = "settings",
                enterTransition = { slideInHorizontally(tween(300)) { it / 5 } + fadeIn(tween(300)) },
                popExitTransition = { slideOutHorizontally(tween(260)) { it / 5 } + fadeOut(tween(220)) },
            ) {
                SettingsScreen(vm, onBack = { nav.popBackStack() }, onOpenPermissions = { nav.navigate("permissions") })
            }
            composable(
                route = "permissions",
                enterTransition = { slideInHorizontally(tween(300)) { it / 5 } + fadeIn(tween(300)) },
                popExitTransition = { slideOutHorizontally(tween(260)) { it / 5 } + fadeOut(tween(220)) },
            ) {
                PermissionsScreen(
                    state = perms,
                    onGrantAll = onGrantAll,
                    onBack = { nav.popBackStack() },
                )
            }

            composable(
                route = "vault",
                enterTransition = { slideInHorizontally(tween(300)) { it / 5 } + fadeIn(tween(300)) },
                popExitTransition = { slideOutHorizontally(tween(260)) { it / 5 } + fadeOut(tween(220)) },
            ) {
                VaultScreen(
                    vm = vm,
                    onAuthenticate = onAuthenticate,
                    onBack = { nav.popBackStack() },
                    onOpen = openViewer,
                )
            }
            composable(
                route = "album/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
                enterTransition = { slideInHorizontally(tween(300)) { it / 5 } + fadeIn(tween(300)) },
                popExitTransition = { slideOutHorizontally(tween(260)) { it / 5 } + fadeOut(tween(220)) },
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: 0L
                val items by vm.allItems.collectAsStateWithLifecycle()
                val list = remember(items, id) { items.filter { it.bucketId == id } }
                val title = list.firstOrNull()?.bucketName?.takeIf { it.isNotBlank() } ?: "Album"
                CollectionScreen(
                    vm = vm,
                    title = title,
                    items = list,
                    trash = false,
                    onBack = { nav.popBackStack() },
                    onOpen = openViewer,
                )
            }

            composable(
                route = "smart/{kind}",
                arguments = listOf(navArgument("kind") { type = NavType.StringType }),
                enterTransition = { slideInHorizontally(tween(300)) { it / 5 } + fadeIn(tween(300)) },
                popExitTransition = { slideOutHorizontally(tween(260)) { it / 5 } + fadeOut(tween(220)) },
            ) { backStackEntry ->
                val kind = backStackEntry.arguments?.getString("kind") ?: "favorites"
                val favorites by vm.favoriteItems.collectAsStateWithLifecycle()
                val videos by vm.videos.collectAsStateWithLifecycle()
                val trashed by vm.trashed.collectAsStateWithLifecycle()
                val allItems by vm.items.collectAsStateWithLifecycle()
                LaunchedEffect(kind) { if (kind == "trash") vm.loadTrash() }
                val title = when (kind) {
                    "recent" -> "Recent"
                    "videos" -> "Videos"
                    "trash" -> "Trash"
                    else -> "Favorites"
                }
                val list = when (kind) {
                    "recent" -> allItems.take(RecentLimit)
                    "videos" -> videos
                    "trash" -> trashed
                    else -> favorites
                }
                CollectionScreen(
                    vm = vm,
                    title = title,
                    items = list,
                    trash = kind == "trash",
                    flat = kind == "recent",
                    onBack = { nav.popBackStack() },
                    onOpen = openViewer,
                )
            }

            composable(
                route = "viewer/{index}",
                arguments = listOf(navArgument("index") { type = NavType.IntType }),
                enterTransition = { fadeIn(tween(240)) + scaleIn(tween(300), initialScale = 0.92f) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(200)) + scaleOut(tween(220), targetScale = 0.94f) },
            ) { backStackEntry ->
                ViewerScreen(
                    vm = vm,
                    startIndex = backStackEntry.arguments?.getInt("index") ?: 0,
                    onBack = { nav.popBackStack() },
                    onPlay = { item -> nav.navigate("player/${item.id}") },
                    onEdit = { item -> nav.navigate("editor/${item.id}") },
                )
            }

            composable(
                route = "player/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
                enterTransition = { fadeIn(tween(260)) + scaleIn(tween(300), initialScale = 0.94f) },
                popExitTransition = { fadeOut(tween(200)) },
            ) { backStackEntry ->
                PlayerScreen(
                    vm = vm,
                    itemId = backStackEntry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onOpenVideo = { id ->
                        nav.navigate("player/$id") {
                            popUpTo(backStackEntry.destination.id) { inclusive = true }
                        }
                    },
                )
            }

            composable(
                route = "editor/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
                enterTransition = { slideInVertically(tween(320)) { it / 6 } + fadeIn(tween(280)) },
                popExitTransition = { slideOutVertically(tween(260)) { it / 6 } + fadeOut(tween(220)) },
            ) { backStackEntry ->
                EditorScreen(
                    vm = vm,
                    itemId = backStackEntry.arguments?.getLong("id") ?: 0L,
                    onClose = { nav.popBackStack() },
                )
            }
        }

        AnimatedVisibility(
            visible = isTab && !selectMode,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(280)) { it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
        ) {
            Box {
                BottomFade(Modifier.align(Alignment.BottomCenter), height = 130.dp)
                NavPill(
                    current = route,
                    onSelect = { target ->
                        if (target != route) {
                            nav.navigate(target) {
                                popUpTo("photos") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        AnimatedVisibility(
            visible = !loaded,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut(tween(300)),
        ) {
            CircularProgressIndicator(color = cs.primary, strokeWidth = 3.dp)
        }
    }
}
