package com.ipodclassic.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.absoluteValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlin.math.atan2
import kotlin.math.hypot

private data class MenuEntry(val label: String, val subtitle: String = "", val artwork: String? = null, val action: () -> Unit)

@Composable
internal fun IpodApp(model: IpodViewModel, onSignIn: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val entries = menuEntries(state, model)
    LaunchedEffect(state.route) { selected = 0; listState.scrollToItem(0) }
    LaunchedEffect(entries.size) { selected = selected.coerceIn(0, entries.lastIndex.coerceAtLeast(0)) }
    LaunchedEffect(selected) { if (entries.isNotEmpty()) listState.animateScrollToItem(selected.coerceIn(0, entries.lastIndex)) }
    BackHandler(enabled = model.canGoBack) { model.back() }
    fun move(delta: Int) {
        if (entries.isNotEmpty()) {
            selected = (selected + delta).coerceIn(0, entries.lastIndex)
            if (state.haptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val shell = if (state.darkShell) listOf(Color(0xFF666666), Color(0xFF202020)) else listOf(Color(0xFFF0F0F0), Color(0xFFAEAEAE))
    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().imePadding()
        .background(Brush.verticalGradient(shell)).padding(horizontal = 18.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(Modifier.fillMaxWidth().weight(1.4f), shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFAFAFA), border = BorderStroke(5.dp, Color.Black)) {
            Column(Modifier.padding(10.dp)) {
                Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.White, Color(0xFFB6BEC3)))).padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.route.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (state.playback.connected) "●" else "○", color = if (state.playback.connected) Color(0xFF167638) else Color.DarkGray,
                        modifier = Modifier.semantics { contentDescription = if (state.playback.connected) "Spotify playback connected" else "Spotify playback disconnected" })
                }
                when (state.route.page) {
                    Page.SOURCES -> Sources(state, model, onSignIn)
                    Page.NOW_PLAYING -> NowPlaying(state, model)
                    Page.COVER_FLOW -> CoverFlow(state, selected, { selected = it }, model)
                    Page.SETTINGS -> Settings(state, model)
                    Page.ABOUT -> Text("iPod Classic for Android · 0.1.1\n\nNative migration of Tanner Villarete’s iPod.js (MIT).\n\n1.0 Spotify · 2.0 Local music · 3.0 Apple Music\nGames follow later.", Modifier.padding(10.dp))
                    Page.LYRICS -> Text("Lyrics aren’t connected yet. Online lyrics need a separate provider; embedded and .lrc lyrics are planned for 2.0.", Modifier.padding(10.dp))
                    else -> {
                        if (state.route.page == Page.SEARCH) {
                            OutlinedTextField(state.query, model::setQuery, Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text("Find Spotify tracks") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { model.load() }))
                            TextButton(onClick = { model.load() }, enabled = !state.loading) { Text("Search") }
                        }
                        if (state.route.page == Page.DETAIL && state.route.item?.kind != MediaKind.ARTIST) {
                            TextButton(onClick = { state.route.item?.let(model::playContext) }) { Text("Play in Spotify") }
                        }
                        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading Spotify content" })
                        if (entries.isEmpty() && !state.loading) {
                            Text(when {
                                !state.signedIn && state.route.page in listOf(Page.LIBRARY, Page.DETAIL, Page.SEARCH, Page.COVER_FLOW) -> "Connect Spotify in Music Sources."
                                state.route.page == Page.SEARCH -> "Search for a song to get started."
                                else -> "No items returned by Spotify."
                            }, Modifier.padding(12.dp))
                            if (state.route.page in listOf(Page.LIBRARY, Page.DETAIL, Page.COVER_FLOW)) TextButton(onClick = { model.load() }) { Text("Retry / refresh") }
                        }
                        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                            itemsIndexed(entries) { index, item ->
                                Row(Modifier.fillMaxWidth().background(if (selected == index) Color(0xFF087FC4) else Color.Transparent)
                                    .clickable(role = Role.Button) { selected = index; item.action() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    item.artwork?.let { url -> AsyncImage(url, contentDescription = null, modifier = Modifier.size(46.dp).padding(end = 8.dp), contentScale = ContentScale.Crop) }
                                    Column(Modifier.weight(1f)) {
                                        Text(item.label, color = if (selected == index) Color.White else Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (item.subtitle.isNotBlank()) Text(item.subtitle, color = if (selected == index) Color.White.copy(alpha = .85f) else Color.DarkGray,
                                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("›", color = if (selected == index) Color.White else Color.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ClickWheel(onMenu = model::back, onMove = ::move,
                onSelect = { entries.getOrNull(selected)?.action?.invoke() },
                onPrevious = { if (state.route.page == Page.NOW_PLAYING) model.previousTrack() else move(-1) },
                onNext = { if (state.route.page == Page.NOW_PLAYING) model.nextTrack() else move(1) },
                onPlayPause = model::togglePlayback)
        }
        Text("iPod Classic", Modifier.padding(top = 10.dp), color = if (state.darkShell) Color.LightGray else Color.DarkGray, style = MaterialTheme.typography.labelSmall)
    }
    state.error?.let { message ->
        AlertDialog(onDismissRequest = model::dismissError, title = { Text("Spotify") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = model::dismissError) { Text("OK") } })
    }
}

private fun menuEntries(state: AppState, model: IpodViewModel): List<MenuEntry> {
    fun route(page: Page, title: String) = MenuEntry(title) { model.navigate(Route(page, title)) }
    return when (state.route.page) {
        Page.HOME -> listOf(route(Page.MUSIC, "Music"), route(Page.NOW_PLAYING, "Now Playing"), route(Page.SOURCES, "Music Sources"), route(Page.SETTINGS, "Settings"), route(Page.ABOUT, "About"))
        Page.MUSIC -> listOf(
            MenuEntry("Playlists") { model.navigate(Route(Page.LIBRARY, "Playlists", MediaKind.PLAYLIST)) },
            MenuEntry("Albums") { model.navigate(Route(Page.LIBRARY, "Albums", MediaKind.ALBUM)) },
            MenuEntry("Artists") { model.navigate(Route(Page.LIBRARY, "Artists", MediaKind.ARTIST)) },
            MenuEntry("Liked Songs") { model.navigate(Route(Page.LIBRARY, "Liked Songs", MediaKind.TRACK)) },
            route(Page.SEARCH, "Search"), route(Page.COVER_FLOW, "Cover Flow"),
        )
        Page.COVER_FLOW -> state.items.map { item -> MenuEntry(item.title, item.subtitle, item.artwork) { model.select(item) } }
        Page.LIBRARY, Page.DETAIL, Page.SEARCH -> state.items.map { item ->
            MenuEntry(item.title, item.subtitle, item.artwork) { model.select(item) }
        } + listOfNotNull(
            state.next?.let { MenuEntry(if (state.loading) "Loading…" else "Load more") { model.load(more = true) } },
            if (state.items.isNotEmpty()) MenuEntry("Refresh") { model.load() } else null,
        )
        else -> emptyList()
    }
}

@Composable
private fun Sources(state: AppState, model: IpodViewModel, onSignIn: () -> Unit) {
    var clientId by rememberSaveable(state.clientId) { mutableStateOf(state.clientId) }
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Text("Spotify · 1.0", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
            Text("Use your Spotify developer app’s public client ID. No client secret is needed.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(clientId, { clientId = it }, Modifier.fillMaxWidth(), label = { Text("Spotify client ID") }, singleLine = true)
            TextButton(onClick = { model.configure(clientId) }) { Text("Save client ID") }
            Text("Register redirect: $REDIRECT_URI", style = MaterialTheme.typography.bodySmall)
            Text(if (state.signedIn) "Library: ${state.account ?: "signed in"}" else "Library: signed out", Modifier.padding(top = 8.dp))
            Button(onClick = onSignIn, enabled = state.clientId.isNotBlank() && !state.authBusy) { Text(if (state.authBusy) "Signing in…" else if (state.signedIn) "Switch Spotify account" else "Sign in to browse") }
            Button(onClick = model::connectPlayback, enabled = state.clientId.isNotBlank()) { Text(if (state.playback.connected) "Playback connected" else "Connect Spotify playback") }
            Text("Playback uses the installed Spotify app. Register this Android package and signing fingerprint in your developer app.", style = MaterialTheme.typography.bodySmall)
            if (state.signedIn) TextButton(onClick = model::signOut) { Text("Sign out and clear session") }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Local music · 2.0\nApple Music · 3.0", color = Color.DarkGray)
        }
    }
}

@Composable
private fun NowPlaying(state: AppState, model: IpodViewModel) {
    val playback = state.playback
    var seek by remember(playback.position) { mutableFloatStateOf(playback.position.toFloat()) }
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            playback.artwork?.let { Image(it.asImageBitmap(), "Current album artwork", Modifier.fillMaxWidth().height(110.dp), contentScale = ContentScale.Fit) }
            Text(playback.title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            Text(playback.artist, color = Color.DarkGray)
            if (playback.connected) {
                Slider(value = seek.coerceIn(0f, playback.duration.coerceAtLeast(1).toFloat()),
                    onValueChange = { seek = it }, onValueChangeFinished = { model.seek(seek.toLong()) },
                    valueRange = 0f..playback.duration.coerceAtLeast(1).toFloat(), enabled = playback.duration > 0,
                    modifier = Modifier.semantics { contentDescription = "Playback position" })
                Text("${formatTime(playback.position)} / ${formatTime(playback.duration)}", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = model::previousTrack) { Text("Previous") }
                    TextButton(onClick = model::togglePlayback) { Text(if (playback.paused) "Play" else "Pause") }
                    TextButton(onClick = model::nextTrack) { Text("Next") }
                }
            } else Button(onClick = model::connectPlayback) { Text("Connect playback") }
            TextButton(onClick = { model.navigate(Route(Page.LYRICS, "Lyrics")) }) { Text("Lyrics status") }
        }
    }
}

@Composable
private fun Settings(state: AppState, model: IpodViewModel) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dark shell", Modifier.weight(1f)); Switch(state.darkShell, { model.toggleDark() })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Wheel haptics", Modifier.weight(1f)); Switch(state.haptics, { model.toggleHaptics() })
        }
    }
}

@Composable
private fun ClickWheel(onMenu: () -> Unit, onMove: (Int) -> Unit, onSelect: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onPlayPause: () -> Unit) {
    val move by rememberUpdatedState(onMove)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val diameter = minOf(maxWidth, maxHeight, 280.dp)
        Box(Modifier.size(diameter).clip(CircleShape).background(Color(0xFF242424))
            .pointerInput(Unit) {
                var previous: Float? = null
                var accumulated = 0f
                detectDragGestures(onDragStart = { previous = null; accumulated = 0f }, onDragEnd = { previous = null }, onDragCancel = { previous = null }) { change, _ ->
                    val x = change.position.x - size.width / 2f
                    val y = change.position.y - size.height / 2f
                    val radius = hypot(x, y)
                    if (radius < size.width * .22f || radius > size.width * .5f) { previous = null; return@detectDragGestures }
                    val angle = Math.toDegrees(atan2(y, x).toDouble()).toFloat()
                    previous?.let { accumulated += angularDelta(it, angle) }
                    previous = angle
                    while (accumulated >= 18f) { move(1); accumulated -= 18f }
                    while (accumulated <= -18f) { move(-1); accumulated += 18f }
                    change.consume()
                }
            }) {
            TextButton(onClick = onMenu, Modifier.align(Alignment.TopCenter).padding(top = 4.dp)) { Text("MENU", color = Color.White) }
            TextButton(onClick = onPrevious, Modifier.align(Alignment.CenterStart).semantics { contentDescription = "Previous track or menu item" }) { Text("|◀", color = Color.White) }
            TextButton(onClick = onNext, Modifier.align(Alignment.CenterEnd).semantics { contentDescription = "Next track or menu item" }) { Text("▶|", color = Color.White) }
            FilledTonalButton(onClick = onSelect, modifier = Modifier.align(Alignment.Center).size(diameter * .36f).semantics { contentDescription = "Select menu item" }, shape = CircleShape) { Text("Select", style = MaterialTheme.typography.labelSmall) }
            TextButton(onClick = onPlayPause, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).semantics { contentDescription = "Play or pause" }) { Text("▶ Ⅱ", color = Color.White) }
        }
    }
}

@Composable
private fun CoverFlow(state: AppState, selected: Int, onSelected: (Int) -> Unit, model: IpodViewModel) {
    if (state.items.isEmpty()) {
        Column(Modifier.padding(12.dp)) {
            if (state.loading) CircularProgressIndicator()
            else {
                Text("No saved Spotify albums loaded.")
                TextButton(onClick = { model.load() }) { Text("Retry / refresh") }
            }
        }
        return
    }
    val pager = rememberPagerState(pageCount = { state.items.size })
    LaunchedEffect(selected) { pager.animateScrollToPage(selected.coerceIn(0, state.items.lastIndex)) }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { onSelected(it) } }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 45.dp), pageSpacing = 8.dp) { index ->
            val album = state.items[index]
            val offset = (pager.currentPage - index) + pager.currentPageOffsetFraction
            Column(Modifier.fillMaxWidth().graphicsLayer {
                rotationY = offset.coerceIn(-1f, 1f) * -45f
                cameraDistance = 12f * density
                alpha = 1f - offset.absoluteValue.coerceIn(0f, 1f) * .4f
            }.clickable(role = Role.Button) { model.select(album) }, horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(album.artwork, contentDescription = "${album.title} album artwork", modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Crop)
                Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Text(album.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row {
            TextButton(onClick = { model.load() }, enabled = !state.loading) { Text("Refresh") }
            if (state.next != null) TextButton(onClick = { model.load(more = true) }, enabled = !state.loading) { Text("More albums") }
        }
    }
}
