package com.turrini.music

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaSessionConnection
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.turrini.music.data.MusicRepository
import com.turrini.music.ui.theme.TurriniTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val repository by lazy { MusicRepository(this) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Handle permission result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        
        setContent {
            TurriniTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    NavHost(navController, startDestination = "home") {
                        composable("home") { HomeScreen(repository) }
                        // Add more destinations like "player", "playlists" here
                    }
                }
            }
        }
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

@Composable
fun HomeScreen(repository: MusicRepository) {
    val scope = rememberCoroutineScope()
    var songs by remember { mutableStateOf<List<com.turrini.music.data.Song>>(emptyList()) }

    LaunchedEffect(Unit) {
        songs = repository.getSongs()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("TURRINI MUSIC") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (songs.isEmpty()) {
                Text("No music found on device.", modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn {
                    items(songs) { song ->
                        ListItem(
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
