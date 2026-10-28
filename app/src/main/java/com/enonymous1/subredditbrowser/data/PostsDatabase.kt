package com.enonymous1.subredditbrowser.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.enonymous1.subredditbrowser.model.Post
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * One cached post. The original app wrote the whole post list to a file
 * with Java serialization (`ObjectOutputStream`) and read it back with
 * `ObjectInputStream` — unsafe deserialization of a file any other app
 * process could not verify, and unreadable once the class changed. Room
 * rows replace it: typed, queryable, and versioned.
 */
@Entity(tableName = "cached_posts")
data class CachedPostEntity(
    @PrimaryKey val id: String,
    val subreddit: String,
    val title: String,
    val author: String,
    val ups: Int,
    val permalink: String,
    @ColumnInfo(name = "created_utc") val createdUtcSeconds: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "fetched_at") val fetchedAtMillis: Long,
) {
    fun toDomain(): Post = Post(
        id = id,
        subreddit = subreddit,
        title = title,
        author = author,
        ups = ups,
        permalink = permalink,
        createdUtcSeconds = createdUtcSeconds,
        fetchedAtMillis = fetchedAtMillis,
    )

    companion object {
        fun fromDomain(post: Post, sortOrder: Int): CachedPostEntity = CachedPostEntity(
            id = post.id,
            subreddit = post.subreddit,
            title = post.title,
            author = post.author,
            ups = post.ups,
            permalink = post.permalink,
            createdUtcSeconds = post.createdUtcSeconds,
            sortOrder = sortOrder,
            fetchedAtMillis = post.fetchedAtMillis,
        )
    }
}

@Dao
abstract class PostsDao {
    @Query("SELECT * FROM cached_posts WHERE subreddit = :subreddit ORDER BY sort_order ASC")
    abstract fun observeBySubreddit(subreddit: String): Flow<List<CachedPostEntity>>

    @Query("SELECT * FROM cached_posts WHERE id = :id")
    abstract fun observeById(id: String): Flow<CachedPostEntity?>

    @Query("DELETE FROM cached_posts WHERE subreddit = :subreddit")
    abstract suspend fun deleteBySubreddit(subreddit: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(posts: List<CachedPostEntity>)

    /**
     * Replaces a subreddit's cache in one transaction, so a failed or
     * interrupted refresh can never leave half a list on screen. The
     * original overwrote its file in place with no such guarantee.
     */
    @Transaction
    open suspend fun replaceForSubreddit(subreddit: String, posts: List<CachedPostEntity>) {
        deleteBySubreddit(subreddit)
        insertAll(posts)
    }
}

@Database(entities = [CachedPostEntity::class], version = 1, exportSchema = false)
abstract class PostsDatabase : RoomDatabase() {
    abstract fun postsDao(): PostsDao
}

/** The data layer behind [com.enonymous1.subredditbrowser.PostsViewModel]. */
interface PostsRepository {
    fun observePosts(subreddit: String): Flow<List<Post>>
    fun observePost(id: String): Flow<Post?>
    suspend fun replacePosts(subreddit: String, posts: List<Post>)
}

class RoomPostsRepository(private val dao: PostsDao) : PostsRepository {
    override fun observePosts(subreddit: String): Flow<List<Post>> =
        dao.observeBySubreddit(subreddit).map { list -> list.map { it.toDomain() } }

    override fun observePost(id: String): Flow<Post?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun replacePosts(subreddit: String, posts: List<Post>) {
        dao.replaceForSubreddit(
            subreddit,
            posts.mapIndexed { index, post -> CachedPostEntity.fromDomain(post, index) },
        )
    }
}
