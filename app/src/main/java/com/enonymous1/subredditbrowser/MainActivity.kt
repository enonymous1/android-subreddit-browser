package com.enonymous1.subredditbrowser

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.enonymous1.subredditbrowser.data.ConnectivityMonitor
import com.enonymous1.subredditbrowser.data.DataStoreSettingsRepository
import com.enonymous1.subredditbrowser.data.PostsDatabase
import com.enonymous1.subredditbrowser.data.RoomPostsRepository
import com.enonymous1.subredditbrowser.data.remote.createRedditClient
import com.enonymous1.subredditbrowser.data.settingsDataStore
import com.enonymous1.subredditbrowser.ui.SubredditBrowserApp

class MainActivity : ComponentActivity() {

    private lateinit var connectivityMonitor: ConnectivityMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = Room.databaseBuilder(
            applicationContext,
            PostsDatabase::class.java,
            "posts.db",
        ).build()

        connectivityMonitor = ConnectivityMonitor(applicationContext)
        connectivityMonitor.start()

        // The device id for Reddit's installed-app grant. ANDROID_ID is
        // stable per device and signing key; no account, no secret, and
        // nothing identifying the user is sent to Reddit beyond it.
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"
        val remoteSource = createRedditClient(
            clientId = BuildConfig.REDDIT_CLIENT_ID,
            deviceId = deviceId,
        )

        val viewModel = ViewModelProvider(
            this,
            PostsViewModelFactory(
                RoomPostsRepository(database.postsDao()),
                DataStoreSettingsRepository(applicationContext.settingsDataStore),
                remoteSource,
                connectivityMonitor.isOnline,
            ),
        )[PostsViewModel::class.java]

        setContent {
            MaterialTheme {
                SubredditBrowserApp(viewModel)
            }
        }
    }

    override fun onDestroy() {
        connectivityMonitor.stop()
        super.onDestroy()
    }
}
