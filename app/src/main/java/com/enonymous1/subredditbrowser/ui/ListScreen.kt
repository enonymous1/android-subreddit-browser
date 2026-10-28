package com.enonymous1.subredditbrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.enonymous1.subredditbrowser.model.Post

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    posts: List<Post>,
    subreddits: List<String>,
    selectedSubreddit: String,
    customSubreddits: List<String>,
    isOnline: Boolean,
    isRefreshing: Boolean,
    statusMessage: String?,
    isConfigured: Boolean,
    onSelectSubreddit: (String) -> Unit,
    onAddSubreddit: (String) -> String?,
    onRemoveSubreddit: (String) -> Unit,
    onRefresh: () -> Unit,
    onPostClick: (Post) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newSubreddit by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Subreddit Browser") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            val banner = statusMessage
                ?: if (!isOnline) "You're offline — showing saved posts." else null
            if (banner != null) {
                Text(
                    text = banner,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            SubredditSelector(
                subreddits = subreddits,
                selectedSubreddit = selectedSubreddit,
                customSubreddits = customSubreddits,
                onSelect = onSelectSubreddit,
                onRemove = onRemoveSubreddit,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newSubreddit,
                    onValueChange = { newSubreddit = it; addError = null },
                    label = { Text("Add a subreddit") },
                    placeholder = { Text("Android") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("addSubredditField"),
                )
                Button(
                    modifier = Modifier.testTag("addSubredditButton"),
                    onClick = {
                        val error = onAddSubreddit(newSubreddit)
                        if (error == null) {
                            newSubreddit = ""
                        }
                        addError = error
                    },
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }
            if (addError != null) {
                Text(
                    text = addError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (posts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            !isConfigured -> "No saved posts for r/$selectedSubreddit yet. " +
                                "Add your Reddit client id (see README) and tap Refresh to fetch some."
                            !isOnline -> "No saved posts for r/$selectedSubreddit yet. " +
                                "Go online and tap Refresh, and posts will be saved for offline use."
                            else -> "No posts for r/$selectedSubreddit yet. Tap Refresh to fetch some."
                        },
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = posts, key = { it.id }) { post ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPostClick(post) },
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = post.ups.toString(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(64.dp),
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = post.title,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${post.authorDisplay} · ${post.subredditDisplay}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SubredditSelector(
    subreddits: List<String>,
    selectedSubreddit: String,
    customSubreddits: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Text("Subreddit: r/$selectedSubreddit")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            subreddits.forEach { subreddit ->
                DropdownMenuItem(
                    text = { Text("r/$subreddit") },
                    trailingIcon = if (subreddit in customSubreddits) {
                        {
                            IconButton(
                                onClick = { onRemove(subreddit) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Remove r/$subreddit",
                                )
                            }
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(subreddit)
                    },
                )
            }
        }
    }
}
