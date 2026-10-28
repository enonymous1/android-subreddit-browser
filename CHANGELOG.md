# Changelog

> Public export note: this repository is a squashed single-commit export of the 2.0.0 modernization below. The original private history is not included.

## 2.0.0 — 2026-10-28

Modernization of the original AD2 assignment (LyonsJustin12).

- Rewritten in Kotlin with Jetpack Compose Material 3 and Navigation Compose; the XML layouts, ListView/BaseAdapter, and fragment are gone. One activity hosts list and details destinations.
- Reddit fetching rebuilt on the OAuth API: the original's unauthenticated `GET https://www.reddit.com/r/<sub>/hot.json` via raw `HttpURLConnection` now returns 403, so version 2.0 uses the installed-app grant (client id only, no secret) with a bearer token cached until shortly before expiry, one retry on a 401, a descriptive User-Agent, and 15-second timeouts. The client id comes from `local.properties` (`redditClientId`) or `REDDIT_CLIENT_ID` and is never committed; without one the app runs in cache-only mode and says so.
- Offline caching rebuilt on Room (`posts.db`): each subreddit's list is replaced atomically in one transaction, in API order, keyed by post id. The original wrote the list with Java serialization and read it back with `ObjectInputStream` (unsafe deserialization, unreadable after a class change). Version 1.0 cache files are not migrated: the schema differs, so an upgrade starts with an empty cache until each subreddit fetches once.
- The post model is now a real data class with an id, so two posts with the same title are distinct; the original was four public mutable `String` fields with no id. DTO fields all have defaults, so deleted/removed posts no longer crash parsing the way `getString` on every field did.
- Networking errors no longer crash the app: IO, HTTP, and parse failures become readable status messages, and the cached list stays on screen. The original threw `RuntimeException` from its background task on every failure, and committed a fragment transaction from that same background thread.
- Connectivity uses a `NetworkCallback` registered and unregistered with the activity lifecycle. The original registered a `BroadcastReceiver` on the deprecated `CONNECTIVITY_ACTION` broadcast, used the deprecated `NetworkInfo` API, never unregistered the receiver, and popped a toast on every connectivity flap.
- Subreddit selection is a dropdown over the five original subreddits plus any the user adds; added names are validated (3–21 letters, digits, or underscores, leading `r/` stripped) and persisted in DataStore with the selection. The original passed spinner text straight into a URL unchecked.
- A details screen is new: upvotes, author, subreddit, and an Open on Reddit button. The original had no details view.
- Toolchain: AGP 8.13.0, Gradle 8.14.3, Kotlin 2.2.0, Java 17, compile/target SDK 36, min SDK 24, Kotlin DSL with a version catalog, R8 for release builds.
- Package and application ID changed from `com.example.lyonsjustin12` to `com.enonymous1.subredditbrowser`; the app is now called Subreddit Browser. Internet and network-state permissions are declared; no other permissions.
- Added GitHub Actions CI (unit tests + debug build), an MIT LICENSE, and this changelog.
- Removed from tracking: the two committed release APKs (~8.8 MB) and the IDE configuration files. They were build output and editor state, not source. The stock launcher icon and the Material 3 color palette from the original are kept.
- Tests: 12 subreddit-name tests, 5 post-mapping tests, 7 Reddit client tests (token caching/expiry, 401 retry, deduplication, 404/IO error messages, unconfigured mode), 10 ViewModel tests with fake repository/settings/remote (selection fetch, offline and unconfigured behavior, failed-fetch cache retention, add/remove validation), and 6 instrumented tests (package name plus 5 Compose UI tests covering the list, details, invalid-name error, adding a subreddit, and the offline empty state — the UI tests need a device or emulator and are not run in CI).

## 1.0.0

Original AD2 assignment: Java, AppCompat, XML layouts and one fragment, a spinner over five subreddits, raw `HttpURLConnection` to Reddit's unauthenticated JSON endpoint, and Java-serialized files for offline use. The app was titled "LyonsJustin1.2".
