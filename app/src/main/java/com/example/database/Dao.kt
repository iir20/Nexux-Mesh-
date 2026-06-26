package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MeshDao {
    // Mesh Nodes
    @Query("SELECT * FROM mesh_nodes ORDER BY lastSeen DESC")
    fun getAllNodes(): Flow<List<MeshNodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateNode(node: MeshNodeEntity)

    @Query("DELETE FROM mesh_nodes WHERE lastSeen < :cutoffTime")
    suspend fun pruneOldNodes(cutoffTime: Long)

    // Mesh Messages
    @Query("SELECT * FROM mesh_messages ORDER BY lamportClock ASC, timestamp ASC")
    fun getAllMessages(): Flow<List<MeshMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MeshMessageEntity)

    @Query("UPDATE mesh_messages SET isSynced = 1 WHERE messageId = :messageId")
    suspend fun markMessageSynced(messageId: String)

    // Marketplace Listings
    @Query("SELECT * FROM marketplace_listings ORDER BY timestamp DESC")
    fun getAllListings(): Flow<List<MarketplaceListingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListing(listing: MarketplaceListingEntity)

    // Wiki Pages
    @Query("SELECT * FROM wiki_pages ORDER BY pageName ASC")
    fun getAllWikiPages(): Flow<List<WikiPageEntity>>

    @Query("SELECT * FROM wiki_pages WHERE pageName = :pageName LIMIT 1")
    suspend fun getWikiPage(pageName: String): WikiPageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWikiPage(page: WikiPageEntity)

    // File Chunks
    @Query("SELECT * FROM file_chunks ORDER BY sequenceNumber ASC")
    fun getAllFileChunks(): Flow<List<FileChunkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFileChunk(chunk: FileChunkEntity)

    @Query("DELETE FROM file_chunks WHERE fileId = :fileId")
    suspend fun clearChunksForFile(fileId: String)

    // Social Posts
    @Query("SELECT * FROM social_posts ORDER BY timestamp DESC")
    fun getAllSocialPosts(): Flow<List<SocialPostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSocialPost(post: SocialPostEntity)

    @Query("UPDATE social_posts SET likesCount = likesCount + 1 WHERE postId = :postId")
    suspend fun likeSocialPost(postId: String)

    // Storage Retention Policies (Prunes)
    @Query("DELETE FROM mesh_messages WHERE isEmergency = 0 AND timestamp < :cutoffTime")
    suspend fun pruneChatMessages(cutoffTime: Long)

    @Query("DELETE FROM marketplace_listings WHERE timestamp < :cutoffTime")
    suspend fun pruneMarketplaceListings(cutoffTime: Long)

    @Query("DELETE FROM mesh_messages WHERE isEmergency = 1 AND timestamp < :cutoffTime")
    suspend fun pruneEmergencyAlerts(cutoffTime: Long)

    // Failure Logs (Rule 6)
    @Query("SELECT * FROM failure_logs ORDER BY timestamp DESC")
    fun getAllFailureLogs(): Flow<List<FailureLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFailureLog(log: FailureLogEntity)

    @Query("DELETE FROM failure_logs")
    suspend fun clearFailureLogs()
}
