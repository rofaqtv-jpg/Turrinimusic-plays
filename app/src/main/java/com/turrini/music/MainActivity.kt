package com.turrini.music

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.common.util.concurrent.ListenableFuture
import com.turrini.music.data.MusicRepository
import com.turrini.music.data.Song
import com.turrini.music.playback.PlaybackService
import com.turrini.music.ui.theme.TurriniTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val repository by lazy { MusicRepository(this) }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller by mutableStateOf<MediaController?>(null)
    private var reloadTick by mutableStateOf(0)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { reloadTick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()

        setContent {
            TurriniTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(repository, controller, reloadTick)
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            { controller = try { future.get() } catch (e: Exception) { null } },
            ContextCompat.getMainExecutor(this)
        )
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        super.onStop()
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val needsPermission = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needsPermission) permissionLauncher.launch(permissions)
    }
}

private fun playSongs(player: Player, songs: List<Song>, index: Int) {
    val items = songs.map { s ->
        MediaItem.Builder()
            .setMediaId(s.id.toString())
            .setUri(s.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(s.title)
                    .setArtist(s.artist)
                    .setAlbumTitle(s.album)
                    .setArtworkUri(s.albumArtUri)
                    .build()
            )
            .build()
    }
    player.setMediaItems(items, index, 0L)
    player.prepare()
    player.play()
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(repository: MusicRepository, player: Player?, reloadTick: Int) {
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }

    LaunchedEffect(reloadTick) {
        songs = try {
            repository.getSongs()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("TURRINI MUSIC") }) },
        bottomBar = { if (player != null) MiniPlayer(player) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (songs.isEmpty()) {
                Text("No music found on device.", modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn {
                    itemsIndexed(songs) { index, song ->
                        ListItem(
                            modifier = Modifier.clickable {
                                player?.let { playSongs(it, songs, index) }
                            },
                            headlineContent = { Text(song.title) },
                            supportingContent = { Text(song.artist) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(player: Player) {
    var hasItem by remember { mutableStateOf(player.mediaItemCount > 0) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var title by remember { mutableStateOf(player.mediaMetadata.title?.toString() ?: "") }
    var artist by remember { mutableStateOf(player.mediaMetadata.artist?.toString() ?: "") }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                hasItem = p.mediaItemCount > 0
                isPlaying = p.isPlaying
                title = p.mediaMetadata.title?.toString() ?: ""
                artist = p.mediaMetadata.artist?.toString() ?: ""
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            if (!dragging) position = player.currentPosition
            duration = player.duration.let { if (it == C.TIME_UNSET) 0L else it }
            delay(500)
        }
    }

    if (!hasItem) return

    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)

            Slider(
                value = position.coerceIn(0L, maxOf(duration, 1L)).toFloat(),
                onValueChange = {
                    dragging = true
                    position = it.toLong()
                },
                onValueChangeFinished = {
                    player.seekTo(position)
                    dragging = false
                },
                valueRange = 0f..maxOf(duration, 1L).toFloat()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(position), style = MaterialTheme.typography.labelSmall)
                Text(formatTime(duration), style = MaterialTheme.typography.labelSmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { player.seekToPreviousMediaItem() }) {
                    Text("⏮", fontSize = 26.sp)
                }
                TextButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                    Text(if (isPlaying) "⏸" else "▶", fontSize = 30.sp)
                }
                TextButton(onClick = { player.seekToNextMediaItem() }) {
                    Text("⏭", fontSize = 26.sp)
                }
            }
        }
    }
}
