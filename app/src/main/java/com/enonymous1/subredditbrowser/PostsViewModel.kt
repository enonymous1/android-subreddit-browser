package com.enonymous1.subredditbrowser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.enonymous1.subredditbrowser.data.PostsRepository
import com.enonymous1.subredditbrowser.data.SettingsRepository
import com.enonymous1.subredditbrowser.data.remote.FetchResult
import com.enonymous1.subredditbrowser.data.remote.PostsRemoteSource
import com.enonymous1.subredditbrowser.model.DEFAULT_SUBREDDITS
import com.enonymous1.subredditbrowser.model.Post
import com.enonymous1.subredditbrowser.model.SubredditName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns the post list. Posts come from the Room cache as a live flow, so
 * the list on screen is always the cached list, a refresh replaces it
 * atomically, and nothing is lost on rotation. Fetching happens only
 * when the device is online and Reddit is configured; otherwise the
 * cached posts stay on screen with a banner saying why — the original
 * either crashed on a failed fetch or threw a `RuntimeException` from a
 * background thread that killed the app.
 *
 * All UI state changes happen on the main thread through StateFlow.
 * Nothing here touches a fragment transaction, a view, or a receiver.
 */
class PostsViewModel(
    private val repository: PostsRepository,
    private val settingsRepository: SettingsRepository,
    private val remoteSource: PostsRemoteSource,
    isOnline: Flow<Boolean>,
) : ViewModel() {

    val selectedSubreddit: StateFlow<String> = settingsRepository.selectedSubreddit
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_SUBREDDITS.first())

    val customSubreddits: StateFlow<List<String>> = settingsRepository.customSubreddits
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val subreddits: StateFlow<List<String>> = customSubreddits
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        .let { custom ->
            combine(custom, selectedSubreddit) { customs, selected ->
                (DEFAULT_SUBREDDITS + customs + selected).distinct()
            }.stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_SUBREDDITS)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val posts: StateFlow<List<Post>> = selectedSubreddit
        .distinctUntilChanged()
        .flatMapLatest { subreddit -> repository.observePosts(subreddit) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isOnline: StateFlow<Boolean> = isOnline
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isConfigured: Boolean
        get() = remoteSource.isConfigured

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        // Fetch when the selection changes, exactly as the original
        // spinner listener did — but from a coroutine, with the result
        // delivered through StateFlow instead of a fragment transaction
        // posted from a background thread.
        viewModelScope.launch {
            selectedSubreddit.distinctUntilChanged().collect { subreddit ->
                refresh(subreddit)
            }
        }
    }

    fun post(id: String): Flow<Post?> = repository.observePost(id)

    fun selectSubreddit(subreddit: String) {
        viewModelScope.launch { settingsRepository.setSelectedSubreddit(subreddit) }
    }

    fun refresh() {
        refresh(selectedSubreddit.value)
    }

    private fun refresh(subreddit: String) {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                if (!isOnline.value) {
                    _statusMessage.value = "You're offline — showing saved posts."
                    return@launch
                }
                if (!remoteSource.isConfigured) {
                    _statusMessage.value =
                        "Reddit isn't configured yet — showing saved posts. " +
                            "Add your client id (see README) to fetch new posts."
                    return@launch
                }
                when (val result = remoteSource.fetchHot(subreddit)) {
                    is FetchResult.Success -> {
                        repository.replacePosts(subreddit, result.posts)
                        _statusMessage.value = null
                    }
                    FetchResult.NotConfigured -> {
                        _statusMessage.value =
                            "Reddit isn't configured yet — showing saved posts. " +
                                "Add your client id (see README) to fetch new posts."
                    }
                    is FetchResult.Failure -> {
                        _statusMessage.value = result.message
                    }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Validates and adds a subreddit, selecting it on success. Returns an
     * error message for the input field, or null when the name was
     * accepted. Never throws on user input.
     */
    fun addSubreddit(raw: String): String? {
        val name = SubredditName.normalize(raw)
            ?: return "Enter a valid subreddit name (3–21 letters, digits, or underscores)."
        viewModelScope.launch {
            if (name !in DEFAULT_SUBREDDITS) {
                settingsRepository.addCustomSubreddit(name)
            }
            settingsRepository.setSelectedSubreddit(name)
        }
        return null
    }

    /** Removes a user-added subreddit. The five built-in ones can't be removed. */
    fun removeSubreddit(subreddit: String) {
        if (subreddit in DEFAULT_SUBREDDITS) return
        viewModelScope.launch {
            settingsRepository.removeCustomSubreddit(subreddit)
            if (selectedSubreddit.value == subreddit) {
                settingsRepository.setSelectedSubreddit(DEFAULT_SUBREDDITS.first())
            }
        }
    }
}

class PostsViewModelFactory(
    private val repository: PostsRepository,
    private val settingsRepository: SettingsRepository,
    private val remoteSource: PostsRemoteSource,
    private val isOnline: Flow<Boolean>,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PostsViewModel::class.java)) {
            return PostsViewModel(repository, settingsRepository, remoteSource, isOnline) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
