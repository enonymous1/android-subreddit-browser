package com.enonymous1.subredditbrowser.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.enonymous1.subredditbrowser.PostsViewModel

/**
 * One activity, two destinations. The original used one activity plus a
 * fragment that was replaced from a background thread; here both screens
 * read the same ViewModel, so the list on screen is always the cached
 * list and details always shows the post with the id in the route.
 */
@Composable
fun SubredditBrowserApp(viewModel: PostsViewModel, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "list",
        modifier = modifier,
    ) {
        composable("list") {
            val posts by viewModel.posts.collectAsState()
            val subreddits by viewModel.subreddits.collectAsState()
            val selected by viewModel.selectedSubreddit.collectAsState()
            val customSubreddits by viewModel.customSubreddits.collectAsState()
            val isOnline by viewModel.isOnline.collectAsState()
            val isRefreshing by viewModel.isRefreshing.collectAsState()
            val statusMessage by viewModel.statusMessage.collectAsState()
            ListScreen(
                posts = posts,
                subreddits = subreddits,
                selectedSubreddit = selected,
                customSubreddits = customSubreddits,
                isOnline = isOnline,
                isRefreshing = isRefreshing,
                statusMessage = statusMessage,
                isConfigured = viewModel.isConfigured,
                onSelectSubreddit = viewModel::selectSubreddit,
                onAddSubreddit = viewModel::addSubreddit,
                onRemoveSubreddit = viewModel::removeSubreddit,
                onRefresh = viewModel::refresh,
                onPostClick = { post -> navController.navigate("details/${post.id}") },
            )
        }
        composable(
            route = "details/{postId}",
            arguments = listOf(navArgument("postId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: ""
            DetailsScreen(
                viewModel = viewModel,
                postId = postId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
