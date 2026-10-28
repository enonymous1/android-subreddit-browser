# Subreddit Browser

A small Android app that shows the hot posts of a chosen subreddit and saves them on the device, so the last fetched list is still there when you're offline. Pick one of the five built-in subreddits, or add your own.

This started as a Full Sail AD2 assignment. Version 2.0.0 is a ground-up modernization of that app, keeping its behavior and fixing its bugs — see [CHANGELOG.md](CHANGELOG.md) for the full list.

## What it does

- One list screen and one post-details screen, in one activity with Navigation Compose.
- The list shows each post's upvotes, title, author, and subreddit, in the order Reddit returned them. Tapping a post opens its details, with a button to open it on Reddit in a browser.
- The five original subreddits (gameDevClassifieds, Coding, Android, Programming, androiddev) are built in. Added subreddits are saved, can be removed, and survive app restarts. Names are validated before anything is fetched.
- When you're online, picking a subreddit (or tapping Refresh) fetches its hot posts and replaces that subreddit's saved list. When you're offline, or Reddit isn't configured yet, the saved list stays on screen with a banner saying why — the original crashed or showed nothing in both cases.
- Works in portrait and landscape.

## Reddit setup (required to fetch new posts)

Reddit no longer serves hot listings without authentication — the original app's unauthenticated request now returns 403. This version uses Reddit's installed-app OAuth grant: no Reddit account sign-in in the app, no client secret, just a client id.

1. While signed in to Reddit in a browser, go to <https://www.reddit.com/prefs/apps> and click **create another app** (or **create app**).
2. Choose **installed app**, give it a name, and set the redirect URI to `http://localhost` (it is never actually used by this flow). Click **create app**.
3. Copy the client id — the string under the app's name, above the word "secret". It is not a secret for installed apps, but treat it like a credential anyway: it identifies your app to Reddit.
4. Put it in `local.properties` in the project root (that file is git-ignored and is never committed):

   ```properties
   redditClientId=YOUR_CLIENT_ID_HERE
   ```

   Or set the `REDDIT_CLIENT_ID` environment variable when building. The app's User-Agent identifies it as `android:com.enonymous1.subredditbrowser:2.0.0 (by /u/enonymous1)`; if you fork this, use your own username there (see `REDDIT_USER_AGENT` in `RedditClient.kt`).

Without a client id the app still builds and runs, but only shows saved posts and says Reddit isn't configured. CI builds without one on purpose, so no credential ever touches the repository.

Saved posts from version 1.0 are not carried over: version 2.0 caches posts in a Room database (`posts.db`) instead of Java-serialized files, so an upgrade starts with an empty cache for each subreddit until it fetches once.

## Tech stack

- Kotlin 2.2.0, Jetpack Compose Material 3, Navigation Compose
- ViewModel + StateFlow, Room for the offline cache, DataStore for the selected/added subreddits
- Retrofit + OkHttp + kotlinx.serialization for Reddit's OAuth API, with token caching and one retry on a 401
- `ConnectivityManager.NetworkCallback` for online/offline state — no broadcast receiver
- AGP 8.13.0, Gradle 8.14.3, Java 17
- Compile/target SDK 36, min SDK 24
- Kotlin DSL with a version catalog, R8 for release builds
- GitHub Actions CI: JVM unit tests + debug build on every push to `main`

## Project structure

```text
app/src/main/java/com/enonymous1/subredditbrowser/
├── MainActivity.kt            # Builds the Room database, DataStore, connectivity monitor, Reddit client, and ViewModel
├── PostsViewModel.kt          # Post list, selection, refresh, add/remove subreddit, status messages
├── model/Post.kt              # Post model, built-in subreddits, subreddit-name validation
├── data/
│   ├── PostsDatabase.kt       # Room entity, DAO, database, repository (atomic per-subreddit cache replace)
│   ├── SettingsRepository.kt  # DataStore selected subreddit and added subreddits
│   ├── ConnectivityMonitor.kt # NetworkCallback online/offline state
│   └── remote/
│       ├── RedditDto.kt       # kotlinx.serialization DTOs for Reddit's listing and token responses
│       └── RedditClient.kt    # Installed-app OAuth token handling, hot-posts fetch, error mapping
└── ui/
    ├── SubredditBrowserApp.kt # Navigation host: list / details
    ├── ListScreen.kt          # Subreddit picker, add-subreddit field, post list, offline/config banners
    └── DetailsScreen.kt       # Post details with an Open on Reddit button
```

## Build and test

Open the project in Android Studio and run the `app` configuration, or from a terminal:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The instrumented tests (including the Compose UI tests) need a device or emulator:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## License

MIT — see [LICENSE](LICENSE).
