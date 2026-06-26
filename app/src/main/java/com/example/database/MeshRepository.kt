package com.example.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MeshRepository(private val meshDao: MeshDao) {
    val allNodes: Flow<List<MeshNodeEntity>> = meshDao.getAllNodes()
    val allMessages: Flow<List<MeshMessageEntity>> = meshDao.getAllMessages().map { list ->
        list.map { it.copy(content = SecurityHelper.decrypt(it.content)) }
    }
    val allListings: Flow<List<MarketplaceListingEntity>> = meshDao.getAllListings()
    val allWikiPages: Flow<List<WikiPageEntity>> = meshDao.getAllWikiPages().map { list ->
        list.map { it.copy(content = SecurityHelper.decrypt(it.content)) }
    }
    val allFileChunks: Flow<List<FileChunkEntity>> = meshDao.getAllFileChunks()
    val allSocialPosts: Flow<List<SocialPostEntity>> = meshDao.getAllSocialPosts().map { list ->
        list.map { it.copy(content = SecurityHelper.decrypt(it.content)) }
    }

    suspend fun insertOrUpdateNode(node: MeshNodeEntity) {
        meshDao.insertOrUpdateNode(node)
    }

    suspend fun pruneOldNodes(cutoffTime: Long) {
        meshDao.pruneOldNodes(cutoffTime)
    }

    suspend fun insertMessage(message: MeshMessageEntity) {
        val encryptedMessage = message.copy(content = SecurityHelper.encrypt(message.content))
        meshDao.insertMessage(encryptedMessage)
    }

    suspend fun markMessageSynced(messageId: String) {
        meshDao.markMessageSynced(messageId)
    }

    suspend fun insertListing(listing: MarketplaceListingEntity) {
        meshDao.insertListing(listing)
    }

    suspend fun getWikiPage(pageName: String): WikiPageEntity? {
        val page = meshDao.getWikiPage(pageName) ?: return null
        return page.copy(content = SecurityHelper.decrypt(page.content))
    }

    suspend fun insertWikiPage(page: WikiPageEntity) {
        val encryptedPage = page.copy(content = SecurityHelper.encrypt(page.content))
        meshDao.insertWikiPage(encryptedPage)
    }

    suspend fun insertFileChunk(chunk: FileChunkEntity) {
        meshDao.insertFileChunk(chunk)
    }

    suspend fun clearChunksForFile(fileId: String) {
        meshDao.clearChunksForFile(fileId)
    }

    suspend fun insertSocialPost(post: SocialPostEntity) {
        val encryptedPost = post.copy(content = SecurityHelper.encrypt(post.content))
        meshDao.insertSocialPost(encryptedPost)
    }

    suspend fun likeSocialPost(postId: String) {
        meshDao.likeSocialPost(postId)
    }

    suspend fun pruneChatMessages(cutoffTime: Long) {
        meshDao.pruneChatMessages(cutoffTime)
    }

    suspend fun pruneMarketplaceListings(cutoffTime: Long) {
        meshDao.pruneMarketplaceListings(cutoffTime)
    }

    suspend fun pruneEmergencyAlerts(cutoffTime: Long) {
        meshDao.pruneEmergencyAlerts(cutoffTime)
    }

    // Failure Logs (Rule 6)
    val allFailureLogs: Flow<List<FailureLogEntity>> = meshDao.getAllFailureLogs()

    suspend fun insertFailureLog(log: FailureLogEntity) {
        meshDao.insertFailureLog(log)
    }

    suspend fun clearFailureLogs() {
        meshDao.clearFailureLogs()
    }
}
