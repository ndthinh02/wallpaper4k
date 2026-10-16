package com.example.wallpaper.presentation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.*
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.wallpaper.R
import com.example.wallpaper.repository.Settings
import com.example.wallpaper.ui.theme.Roboto
import kotlinx.coroutines.launch

private val mainTabs = listOf("home", "collections", "search", "settings")
private val tabLabels = listOf(R.string.home, R.string.collections, R.string.search, R.string.settings)
private val tabIcons =
    listOf(Icons.Outlined.Home, Icons.Outlined.GridView, Icons.Outlined.Search, Icons.Outlined.Settings)

@Composable
fun WalperApp(vm: WalperViewModel, settings: Settings, initialId: String?) {
    val view = LocalView.current
    val dark = MaterialTheme.colorScheme.background == WalperTokens.Night
    SideEffect {
        (view.context as? Activity)?.window?.let {
            WindowInsetsControllerCompat(
                it,
                view
            ).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!settings.onboarded) Onboarding { vm.launch { vm.repo.settings.flag("onboarded", true) } }
        else MainApp(vm, settings, initialId)
    }
    NetworkLossAlert()
}

@Composable
private fun Onboarding(done: () -> Unit) {
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val titles = listOf(R.string.onboard_title_1, R.string.onboard_title_2, R.string.onboard_title_3)
    val bodies = listOf(R.string.onboard_body_1, R.string.onboard_body_2, R.string.onboard_body_3)
    val images = listOf(R.drawable.onboard_1, R.drawable.onboard_2, R.drawable.onboard_3)
    Column(Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .padding(vertical = 24.dp)) {
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painterResource(images[page]),
                    null,
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .aspectRatio(1f)
                )
                Text(
                    stringResource(titles[page]),
                    Modifier.padding(top = 24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    stringResource(bodies[page]),
                    Modifier.padding(top = 24.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) { n ->
                Box(
                    Modifier
                        .width(if (pager.currentPage == n) 24.dp else 7.dp)
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(WalperTokens.Lavender.copy(alpha = if (pager.currentPage == n) 1f else .4f))
                )
            }
        }
        Button(
            { if (pager.currentPage == 2) done() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(58.dp), shape = WalperTokens.Card,
            colors = ButtonDefaults.buttonColors(containerColor = WalperTokens.Ink, contentColor = Color.White)
        ) {
            Text(
                stringResource(if (pager.currentPage == 2) R.string.get_started else R.string.next),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MainApp(vm: WalperViewModel, settings: Settings, initialId: String?) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    var drawer by rememberSaveable { mutableStateOf(false) }
    val scale by animateFloatAsState(if (drawer) .82f else 1f, label = "drawer-scale")
    val slide by animateFloatAsState(if (drawer) 260f else 0f, label = "drawer-slide")
    val snackbar = remember { SnackbarHostState() }
    val events by vm.events.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val open: (com.example.wallpaper.data.Wallpaper) -> Unit = { w ->
        val source = when(route) {
            "home" -> vm.home.value.items
            "search" -> vm.search.value.items
            "collection/{query}" -> vm.collection.value.items
            "personal/{collectionId}" -> vm.collectionWallpapers.value
                .filter { it.collectionId == entry?.arguments?.getString("collectionId") }
                .map { vm.repo.wallpaper(it.metadata) }
            "favorites", "downloads" -> vm.currentList.value.takeIf { items -> items.any { it.id == w.id } } ?: listOf(w)
            "notifications" -> vm.events.value.map { vm.repo.wallpaper(it.metadata) }.distinctBy { it.id }
            else -> vm.currentList.value.takeIf { items -> items.any { it.id == w.id } } ?: listOf(w)
        }
        vm.open(w, source); nav.navigate("detail/${w.id}")
    }
    val home: () -> Unit = {
        nav.navigate("home") {
            popUpTo(nav.graph.findStartDestination().id) { inclusive = false }; launchSingleTop = true
        }
    }
    BackHandler(drawer) { drawer = false }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    var initialHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialId) {
        if (!initialHandled && !initialId.isNullOrBlank()) {
            initialHandled = true; vm.openId(initialId); nav.navigate("detail/$initialId")
        }
    }
    Box(Modifier
        .fillMaxSize()
        .background(Brush.verticalGradient(listOf(WalperTokens.Lavender, WalperTokens.Purple)))) {
        if (drawer || scale < 1f) {
            Column(Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.guest),
                        Modifier.weight(1f),
                        color = Color.White,
                        fontFamily = Roboto,
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton({ drawer = false }) {
                        Icon(
                            Icons.Outlined.Close,
                            stringResource(R.string.close),
                            tint = Color.White
                        )
                    }
                }
                Spacer(Modifier.height(80.dp))
                listOf(
                    "home" to R.string.home,
                    "favorites" to R.string.favorites,
                    "downloads" to R.string.downloads,
                    "privacy" to R.string.privacy,
                    "report" to R.string.report
                ).forEachIndexed { n, (destination, label) ->
                    val icon = listOf(
                        Icons.Outlined.Home,
                        Icons.Outlined.FavoriteBorder,
                        Icons.Outlined.Download,
                        Icons.Outlined.Description,
                        Icons.Outlined.ReportProblem
                    )[n]
                    Row(Modifier
                        .width(230.dp)
                        .clip(WalperTokens.Card)
                        .clickable {
                            drawer = false; if (destination == "home") home() else nav.navigate(destination) {
                            launchSingleTop = true
                        }
                        }
                        .padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp))
                        Text(
                            stringResource(label),
                            Modifier.padding(start = 20.dp),
                            color = Color.White,
                            fontFamily = Roboto,
                            fontWeight = if (route == destination) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 19.sp
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = slide.dp.toPx(); shape = WalperTokens.Bar; clip =
                    drawer || scale < 1f
                }) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    if (route in mainTabs) Column(Modifier.statusBarsPadding()) {
                        Header(
                            { drawer = true },
                            { nav.navigate("notifications") },
                            events.any { !it.read })
                    }
                },
                bottomBar = {
                    if (route in mainTabs) {
                        val dark = MaterialTheme.colorScheme.background == WalperTokens.Night
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                                .height(64.dp)
                                .clip(WalperTokens.Bar)
                                .background(
                                    if (dark) Brush.verticalGradient(
                                        listOf(
                                            WalperTokens.Lavender,
                                            WalperTokens.Purple
                                        )
                                    ) else Brush.linearGradient(listOf(WalperTokens.Ink, WalperTokens.Ink))
                                ),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            mainTabs.forEachIndexed { n, destination ->
                                val selected = route == destination
                                val contentColor = Color.White.copy(alpha = if (selected) 1f else .65f)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .selectable(selected = selected, role = Role.Tab, onClick = {
                                            nav.navigate(destination) {
                                                popUpTo(nav.graph.findStartDestination().id) {
                                                    saveState = true
                                                }; launchSingleTop = true; restoreState = true
                                            }
                                        })
                                        .padding(horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                                ) {
                                    Icon(
                                        tabIcons[n],
                                        contentDescription = null,
                                        tint = contentColor,
                                        modifier = Modifier.size(25.dp)
                                    )
                                    Text(
                                        stringResource(tabLabels[n]),
                                        fontFamily = Roboto,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = contentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                // Bottom bar floats over content; grids reserve their own tail padding.
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = if (route in mainTabs) padding.calculateTopPadding() else 0.dp)
                ) {
                    NavHost(nav, "home") {
                        composable("home") { HomeScreen(vm, settings, open) }
                        composable("collections") {
                            CollectionsScreen(vm, { nav.navigate("collection/$it") }, { nav.navigate("personal/$it") })
                        }
                        composable("personal/{collectionId}") { e ->
                            PersonalCollectionScreen(vm, e.arguments?.getString("collectionId") ?: "",
                                { nav.popBackStack() }, open, home)
                        }
                        composable("search") { SearchScreen(vm, settings, open) { nav.navigate("collection/$it") } }
                        composable("settings") { SettingsScreen(vm, settings, snackbar) }
                        composable("collection/{query}") { e ->
                            CollectionScreen(
                                vm,
                                settings,
                                e.arguments?.getString("query") ?: "nature",
                                { nav.popBackStack() },
                                open
                            )
                        }
                        composable("detail/{id}") { e ->
                            DetailScreen(
                                vm,
                                e.arguments?.getString("id") ?: "",
                                { nav.popBackStack() },
                                home,
                                snackbar,
                                { w ->
                                    vm.open(w)
                                    nav.navigate("detail/${w.id}") {
                                        popUpTo("detail/{id}") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("favorites") { FavoritesScreen(vm, settings, { nav.popBackStack() }, open, home) }
                        composable("downloads") { DownloadsScreen(vm, { nav.popBackStack() }, open, home, snackbar) }
                        composable("notifications") { NotificationsScreen(vm, { nav.popBackStack() }, open) }
                        composable("privacy") { PrivacyScreen { nav.popBackStack() } }
                        composable("report") { ReportScreen { nav.popBackStack() } }
                    }
                }
            }
            if (drawer) Box(Modifier
                .fillMaxSize()
                .clickable { drawer = false })
        }
    }
}
