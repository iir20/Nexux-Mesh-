package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mesh_nodes")
data class MeshNodeEntity(
    @PrimaryKey val deviceId: String,
    val name: String,
    val publicKeyHash: String,
    val sessionId: String,
    val discoveryToken: String,
    val lastSeen: Long = System.currentTimeMillis(),
    val rssi: Int = -60,
    val batteryLevel: Int = 100,
    val trustScore: Float = 1.0f,
    val relayCapability: Boolean = true,
    
    // Message Routing Table & Trust Graph Metrics
    val reliabilityScore: Float = 0.8f,
    val deliverySuccessRate: Float = 0.9f,
    val averageLatencyMs: Long = 150L,
    val batteryClass: String = "HIGH",
    val encounterFrequency: Int = 1,
    val interactionHistoryCount: Int = 1,
    val messageValidationCount: Int = 0,
    val communityEndorsements: Int = 0
)

@Entity(tableName = "mesh_messages")
data class MeshMessageEntity(
    @PrimaryKey val messageId: String,
    val senderId: String,
    val senderName: String,
    val recipientId: String, // "BROADCAST" or user id
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val lamportClock: Int = 0,
    val hops: Int = 0,
    val isEmergency: Boolean = false,
    val isSynced: Boolean = false,
    
    // NEXUS MESH v8.0 Rich Chat Extensions
    val replyToId: String? = null,
    val replyToSenderName: String? = null,
    val replyToContent: String? = null,
    val reactionsJson: String? = null, // JSON structure mapping users to reaction emojis
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val isStarred: Boolean = false,
    val isPinned: Boolean = false,
    val attachmentType: String? = null, // "IMAGE", "VIDEO", "FILE", "VOICE", "APK", "AUDIO", "ZIP"
    val attachmentPath: String? = null,
    val attachmentName: String? = null,
    val attachmentSize: String? = null,
    val voiceDurationSec: Int = 0
)

@Entity(tableName = "marketplace_listings")
data class MarketplaceListingEntity(
    @PrimaryKey val listingId: String,
    val sellerId: String,
    val sellerName: String,
    val title: String,
    val price: String,
    val timestamp: Long = System.currentTimeMillis(),
    val signature: String = ""
)

@Entity(tableName = "wiki_pages")
data class WikiPageEntity(
    @PrimaryKey val pageName: String,
    val content: String,
    val lastContributor: String,
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "file_chunks")
data class FileChunkEntity(
    @PrimaryKey val chunkId: String,
    val fileId: String,
    val fileName: String,
    val sha256Hash: String,
    val sequenceNumber: Int,
    val totalChunks: Int,
    val retryCount: Int = 0,
    val status: String = "PENDING"
)

@Entity(tableName = "social_posts")
data class SocialPostEntity(
    @PrimaryKey val postId: String,
    val authorId: String,
    val authorName: String,
    val content: String,
    val channelType: String, // "LOCAL_FEED", "COMMUNITY", "NEIGHBORHOOD"
    val timestamp: Long = System.currentTimeMillis(),
    val likesCount: Int = 0
)

@Entity(tableName = "failure_logs")
data class FailureLogEntity(
    @PrimaryKey(autoGenerate = true) val logId: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val device: String,
    val androidVersion: String,
    val module: String,
    val error: String,
    val recoveryAction: String,
    val outcome: String
)
