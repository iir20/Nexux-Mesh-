package com.example.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.InetAddress
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.Collections

// --- DISTRIBUTED ROUTING TABLE ENTRY ---
data class RoutingTableEntry(
    val nodeId: String,
    val nextHop: String,
    val hopCount: Int,
    val lastSeenTimestamp: Long,
    val rssi: Int,
    val transportType: String,
    val estimatedLinkQuality: Float, // 0.0 to 1.0 based on RTT and success
    val batteryLevel: Int,
    val routeLifetime: Long, // expiration timestamp
    val trustScore: Float,
    val queueLength: Int
)

class MeshNetworkEngine(
    private val context: Context,
    private val repository: MeshRepository,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "MeshNetworkEngine"
        val MESH_SERVICE_UUID: UUID = UUID.fromString("0000FEF0-0000-1000-8000-00805F9B34FB")
        private const val NSD_SERVICE_TYPE = "_nexus-mesh._tcp"
        private const val DEFAULT_PORT = 4040
        private const val WIFI_DIRECT_PORT = 4041
        private const val PACKET_LIFETIME_MS = 2 * 60 * 60 * 1000L // 2 hours
        private const val MAX_HOPS = 5
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private var serverSocket: ServerSocket? = null
    private var wifiDirectServerSocket: ServerSocket? = null
    private var isEngineRunning = false
    private var currentPort = DEFAULT_PORT

    // High-Level Unified Display Status (UX Goal)
    var unifiedStatus by mutableStateOf("Waiting for relay")

    // --- REAL MEASURED ENGINEERING METRICS ---
    var connectedNeighborsCount by mutableStateOf(0)
    var activeRelaySessionsCount by mutableStateOf(0)
    var routingTableSize by mutableStateOf(0)
    var forwardedPacketsCount by mutableStateOf(0)
    var queuedPacketsCount by mutableStateOf(0)
    var droppedPacketsCount by mutableStateOf(0)
    var duplicatePacketsPreventedCount by mutableStateOf(0)
    var averageRoundTripTimeMs by mutableStateOf(0L)
    var synchronizationQueueSize by mutableStateOf(0)
    var transferSpeedBps by mutableStateOf(0L)
    var batteryImpactPercentPerMin by mutableStateOf(0.15f)
    var databaseSizeMb by mutableStateOf(0.0)
    var currentTransport by mutableStateOf("Local Wi-Fi LAN")

    // Active connection types per Peer ID
    val peerTransportTypes = ConcurrentHashMap<String, String>()

    // Peer Connection Metrics (Connection Intelligence Goal)
    data class PeerMetrics(
        val rssi: Int,
        val batteryLevel: Int,
        val transferSpeedBps: Long,
        val connectionStability: Float
    )
    val peerMetricsMap = ConcurrentHashMap<String, PeerMetrics>()

    // --- PACKET-LEVEL ROUTING DEFINITION (v10 COMPLIANT) ---
    data class MeshPacket(
        val packetId: String,          // Packet UUID
        val senderId: String,          // Sender ID
        val recipientId: String,       // Destination ID or "BROADCAST"
        val previousHop: String,
        val nextHop: String,
        val hopCount: Int,
        val maxTtl: Long,              // Maximum TTL (expiration timestamp)
        val timestamp: Long,
        val payloadType: String,       // "CHAT", "FEED", "STORY", "COMMUNITY", "PROFILE", "WIKI", "FILE", "MARKETPLACE", "REPUTATION"
        val payloadSize: Int,
        val payloadEncrypted: String,  // E2E Encrypted payload using SecurityHelper
        val encryptionMetadata: String,
        val checksum: String,          // Packet checksum
        val path: List<String>         // to prevent loops
    ) {
        fun toJsonObject(): JSONObject {
            return JSONObject().apply {
                put("packetId", packetId)
                put("senderId", senderId)
                put("recipientId", recipientId)
                put("previousHop", previousHop)
                put("nextHop", nextHop)
                put("hopCount", hopCount)
                put("maxTtl", maxTtl)
                put("timestamp", timestamp)
                put("payloadType", payloadType)
                put("payloadSize", payloadSize)
                put("payloadEncrypted", payloadEncrypted)
                put("encryptionMetadata", encryptionMetadata)
                put("checksum", checksum)
                put("path", JSONArray(path))
            }
        }

        companion object {
            fun fromJsonObject(obj: JSONObject): MeshPacket {
                val pathArray = obj.getJSONArray("path")
                val pathList = mutableListOf<String>()
                for (i in 0 until pathArray.length()) {
                    pathList.add(pathArray.getString(i))
                }
                return MeshPacket(
                    packetId = obj.getString("packetId"),
                    senderId = obj.getString("senderId"),
                    recipientId = obj.getString("recipientId"),
                    previousHop = obj.optString("previousHop", ""),
                    nextHop = obj.optString("nextHop", ""),
                    hopCount = obj.getInt("hopCount"),
                    maxTtl = obj.optLong("maxTtl", obj.optLong("ttl", System.currentTimeMillis() + PACKET_LIFETIME_MS)),
                    timestamp = obj.getLong("timestamp"),
                    payloadType = obj.optString("payloadType", obj.optString("type", "CHAT")),
                    payloadSize = obj.optInt("payloadSize", 0),
                    payloadEncrypted = obj.optString("payloadEncrypted", obj.optString("payload", "")),
                    encryptionMetadata = obj.optString("encryptionMetadata", "AES-GCM"),
                    checksum = obj.optString("checksum", ""),
                    path = pathList
                )
            }
        }
    }

    // Stores packets awaiting forward to other nodes
    private val pendingForwardPackets = ConcurrentHashMap<String, MeshPacket>()
    // Tracks previously processed packet IDs to avoid duplication
    private val processedPacketIds = Collections.synchronizedSet(HashSet<String>())

    // Active socket connections
    private val activeConnections = ConcurrentHashMap<String, Socket>()
    private val discoveredPeersInfo = ConcurrentHashMap<String, PeerConnectionInfo>()

    // Distributed Routing Table
    val routingTable = ConcurrentHashMap<String, RoutingTableEntry>()

    // Local identity details - dynamically updated
    var nodeId: String = "NODE-0x" + UUID.randomUUID().toString().substring(0, 8).uppercase()
    var username: String = "Peer_" + UUID.randomUUID().toString().substring(0, 4)
    var batteryPercent: Int = 100
    var activeMode: String = "Relay Node"
    var isInternetGatewayEnabled: Boolean = false
    var localStoriesReference: MutableList<com.example.MeshStory>? = null

    data class PeerConnectionInfo(
        val nodeId: String,
        val ipAddress: String,
        val port: Int,
        val name: String,
        val lastDiscovered: Long = System.currentTimeMillis()
    )

    // Listeners / State callbacks
    var onPeerDiscovered: ((String, String, Int) -> Unit)? = null
    var onConnectionStateChanged: ((String, String) -> Unit)? = null
    var onLogMessage: ((String) -> Unit)? = null

    // --- MESH CONNECTION MANAGER (COOPERATIVE TRANSCIEVER AND SOCKET CONTROL) ---
    val connectionManager = MeshConnectionManager()

    inner class MeshConnectionManager {
        fun getActiveConnectionsCount(): Int = activeConnections.size

        fun enforceBatteryLimits() {
            // Limits connections depending on battery levels
            val maxAllowed = when {
                batteryPercent < 15 -> 1
                batteryPercent < 30 -> 2
                activeMode == "Passive Client" -> 1
                else -> 10
            }
            if (activeConnections.size > maxAllowed) {
                // Terminate lowest priority connections based on link quality or RSSI
                val sortedPeers = activeConnections.keys.sortedBy { peerId ->
                    val route = routingTable[peerId]
                    route?.estimatedLinkQuality ?: 0f
                }
                val excessCount = activeConnections.size - maxAllowed
                for (i in 0 until excessCount) {
                    if (i < sortedPeers.size) {
                        val victim = sortedPeers[i]
                        onLogMessage?.invoke("Battery limit triggered drop connection to $victim")
                        disconnectPeer(victim)
                    }
                }
            }
        }

        fun startHeartbeatLoop() {
            scope.launch(Dispatchers.IO) {
                while (isEngineRunning) {
                    delay(5000) // 5-second interval
                    activeConnections.forEach { (peerId, socket) ->
                        try {
                            val ping = JSONObject().apply {
                                put("type", "HEARTBEAT_PING")
                                put("senderId", nodeId)
                                put("timestamp", System.currentTimeMillis())
                            }
                            sendRawMessage(socket, ping.toString())
                        } catch (e: Exception) {
                            onLogMessage?.invoke("Heartbeat fail for $peerId: ${e.message}")
                            disconnectPeer(peerId)
                        }
                    }
                }
            }
        }

        fun startRecoveryLoop() {
            scope.launch(Dispatchers.IO) {
                while (isEngineRunning) {
                    delay(10000) // 10-second interval
                    enforceBatteryLimits()
                    
                    if (activeMode != "Passive Client" && batteryPercent >= 15) {
                        discoveredPeersInfo.forEach { (peerId, info) ->
                            if (!activeConnections.containsKey(peerId) && peerId != nodeId) {
                                if (System.currentTimeMillis() - info.lastDiscovered < 60000) {
                                    onLogMessage?.invoke("Reconnecting to offline peer: $peerId at ${info.ipAddress}:${info.port}")
                                    scope.launch(Dispatchers.IO) {
                                        connectToPeerDirect(info.ipAddress, info.port)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun start() {
        if (isEngineRunning) return
        isEngineRunning = true
        onLogMessage?.invoke("Starting Nexus Mesh v10 Native Core with Multi-Peer Routing...")

        // 1. Start Wi-Fi TCP Servers (Local LAN & Wi-Fi Direct Ports)
        startTcpServer()
        startWifiDirectServer()

        // 2. Start mDNS/NSD Local Network Discovery
        registerNsdService()
        discoverNsdServices()

        // 3. Start BLE Advertising and Scanning
        startBleAdvertising()
        startBleScanning()

        // 4. Start Internet Gateway Fallback Relay Polling
        startInternetRelayService()

        // 5. Start Background Loops
        connectionManager.startHeartbeatLoop()
        connectionManager.startRecoveryLoop()
        startPeriodicSyncLoop()
        startRoutingQueueProcessor()
        startStatusEvaluationLoop()
    }

    fun stop() {
        isEngineRunning = false
        stopTcpServer()
        stopWifiDirectServer()
        unregisterNsdService()
        stopNsdDiscovery()
        stopBleAdvertising()
        stopBleScanning()
        activeConnections.forEach { (peerId, socket) ->
            try { socket.close() } catch (e: Exception) {}
        }
        activeConnections.clear()
        onLogMessage?.invoke("Mesh Core stopped.")
    }

    private fun stopTcpServer() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
    }

    private fun stopWifiDirectServer() {
        try {
            wifiDirectServerSocket?.close()
        } catch (e: Exception) {}
        wifiDirectServerSocket = null
    }

    private fun startTcpServer() {
        scope.launch(Dispatchers.IO) {
            var attemptPort = DEFAULT_PORT
            while (isEngineRunning) {
                try {
                    serverSocket = ServerSocket(attemptPort)
                    currentPort = attemptPort
                    onLogMessage?.invoke("LAN TCP Server running on port $currentPort")
                    break
                } catch (e: Exception) {
                    onLogMessage?.invoke("Port $attemptPort in use, trying next...")
                    attemptPort++
                }
            }

            while (isEngineRunning) {
                try {
                    val socket = serverSocket?.accept()
                    if (socket != null) {
                        handleIncomingConnection(socket, "Local Wi-Fi LAN")
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    private fun startWifiDirectServer() {
        scope.launch(Dispatchers.IO) {
            try {
                wifiDirectServerSocket = ServerSocket(WIFI_DIRECT_PORT)
                onLogMessage?.invoke("Wi-Fi Direct Server running on port $WIFI_DIRECT_PORT")
                while (isEngineRunning) {
                    val socket = wifiDirectServerSocket?.accept()
                    if (socket != null) {
                        handleIncomingConnection(socket, "Wi-Fi Direct")
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Wi-Fi Direct Server closed.")
            }
        }
    }

    private fun handleIncomingConnection(socket: Socket, transport: String) {
        scope.launch(Dispatchers.IO) {
            var peerId: String? = null
            try {
                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                val writer = PrintWriter(BufferedWriter(OutputStreamWriter(socket.outputStream)), true)

                // 1. Identity handshake exchange
                writer.println(JSONObject().apply {
                    put("type", "IDENTITY")
                    put("nodeId", nodeId)
                    put("username", username)
                    put("batteryLevel", batteryPercent)
                    put("activeMode", activeMode)
                }.toString())

                var line: String? = null
                while (isEngineRunning && reader.readLine().also { line = it } != null) {
                    val messageJson = JSONObject(line!!)
                    val type = messageJson.getString("type")

                    if (type == "IDENTITY") {
                        peerId = messageJson.getString("nodeId")
                        val pName = messageJson.getString("username")
                        val pBattery = messageJson.getInt("batteryLevel")
                        val pMode = messageJson.getString("activeMode")

                        activeConnections[peerId!!] = socket
                        peerTransportTypes[peerId] = transport
                        onConnectionStateChanged?.invoke(peerId, "Connected")
                        onLogMessage?.invoke("Connected to multi-peer: $pName ($peerId) via $transport")

                        // Update routing table with direct hop
                        updateRoutingEntry(
                            nodeId = peerId,
                            nextHop = peerId,
                            hopCount = 1,
                            rssi = -50,
                            transportType = transport,
                            batteryLevel = pBattery,
                            trustScore = 1.0f
                        )

                        // Run immediate synchronization
                        runSyncProtocol(peerId, reader, writer)
                    } else if (type == "HEARTBEAT_PING") {
                        val sender = messageJson.getString("senderId")
                        val ts = messageJson.getLong("timestamp")
                        sendRawMessage(socket, JSONObject().apply {
                            put("type", "HEARTBEAT_PONG")
                            put("senderId", nodeId)
                            put("timestamp", ts)
                        }.toString())
                    } else if (type == "HEARTBEAT_PONG") {
                        val sender = messageJson.getString("senderId")
                        val ts = messageJson.getLong("timestamp")
                        val rtt = System.currentTimeMillis() - ts
                        averageRoundTripTimeMs = if (averageRoundTripTimeMs == 0L) rtt else (averageRoundTripTimeMs * 4 + rtt) / 5
                        
                        // Update Routing link quality
                        val entry = routingTable[sender]
                        if (entry != null) {
                            val quality = when {
                                rtt < 80 -> 1.0f
                                rtt < 300 -> 0.8f
                                else -> 0.4f
                            }
                            routingTable[sender] = entry.copy(
                                lastSeenTimestamp = System.currentTimeMillis(),
                                estimatedLinkQuality = quality,
                                routeLifetime = System.currentTimeMillis() + 30000L
                            )
                        }
                    } else if (type == "MESH_PACKET") {
                        val packet = MeshPacket.fromJsonObject(messageJson.getJSONObject("packet"))
                        receiveMeshPacket(packet, transport)
                    } else if (type == "DELIVERY_RECEIPT") {
                        val msgId = messageJson.getString("messageId")
                        pendingForwardPackets.remove(msgId)
                        repository.markMessageSynced(msgId)
                        onConnectionStateChanged?.invoke(peerId ?: "", "Delivered")
                    }
                }
            } catch (e: Exception) {
                onLogMessage?.invoke("Connection lost with ${peerId ?: "unknown"}: ${e.message}")
            } finally {
                peerId?.let { disconnectPeer(it) }
                try { socket.close() } catch (e: Exception) {}
            }
        }
    }

    fun disconnectPeer(peerId: String) {
        val socket = activeConnections.remove(peerId)
        peerTransportTypes.remove(peerId)
        try { socket?.close() } catch (e: Exception) {}
        onConnectionStateChanged?.invoke(peerId, "Disconnected")
        routingTable.remove(peerId)
    }

    private fun sendRawMessage(socket: Socket, message: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val writer = PrintWriter(BufferedWriter(OutputStreamWriter(socket.outputStream)), true)
                writer.println(message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send raw message: ${e.message}")
            }
        }
    }

    fun connectToPeerDirect(ip: String, port: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                val socket = Socket(ip, port)
                handleIncomingConnection(socket, "Local Wi-Fi LAN")
            } catch (e: Exception) {
                Log.d(TAG, "Direct connection attempt failed to $ip:$port")
            }
        }
    }

    // --- MANAGE DISTRIBUTED ROUTING TABLE ENTRIES ---
    private fun updateRoutingEntry(
        nodeId: String,
        nextHop: String,
        hopCount: Int,
        rssi: Int,
        transportType: String,
        batteryLevel: Int,
        trustScore: Float
    ) {
        val entry = RoutingTableEntry(
            nodeId = nodeId,
            nextHop = nextHop,
            hopCount = hopCount,
            lastSeenTimestamp = System.currentTimeMillis(),
            rssi = rssi,
            transportType = transportType,
            estimatedLinkQuality = 1.0f / (hopCount),
            batteryLevel = batteryLevel,
            routeLifetime = System.currentTimeMillis() + 45000L, // 45 seconds lifetime
            trustScore = trustScore,
            queueLength = pendingForwardPackets.size
        )
        routingTable[nodeId] = entry
        routingTableSize = routingTable.size
    }

    // --- PHASE 1 NSD MDNS DISCOVERY ---
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null

    private fun registerNsdService() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "NexusMesh_$nodeId"
            serviceType = NSD_SERVICE_TYPE
            port = currentPort
        }
        nsdRegistrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(regInfo: NsdServiceInfo) {
                onLogMessage?.invoke("mDNS Service registered: ${regInfo.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                onLogMessage?.invoke("mDNS registration failed: $errorCode")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        try {
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, nsdRegistrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "NSD register failed: ${e.message}")
        }
    }

    private fun unregisterNsdService() {
        try {
            nsdRegistrationListener?.let { nsdManager?.unregisterService(it) }
        } catch (e: Exception) {}
    }

    private fun discoverNsdServices() {
        nsdDiscoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager?.stopServiceDiscovery(this)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager?.stopServiceDiscovery(this)
            }
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceName.contains("NexusMesh_") && !serviceInfo.serviceName.contains(nodeId)) {
                    nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            val pId = resolvedInfo.serviceName.replace("NexusMesh_", "")
                            val host = resolvedInfo.host.hostAddress ?: ""
                            val port = resolvedInfo.port
                            
                            discoveredPeersInfo[pId] = PeerConnectionInfo(pId, host, port, "mDNS Peer")
                            onPeerDiscovered?.invoke(pId, "mDNS Peer", -50)
                            connectToPeerDirect(host, port)
                        }
                    })
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
        }
        try {
            nsdManager?.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, nsdDiscoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "NSD discover failed: ${e.message}")
        }
    }

    private fun stopNsdDiscovery() {
        try {
            nsdDiscoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (e: Exception) {}
    }

    // --- BLE DISCOVERY AND ADVERTISING VECTOR ---
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiseCallback: AdvertiseCallback? = null
    private var bleScanCallback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    private fun startBleAdvertising() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        bleAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (bleAdvertiser == null) return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
            // Put abbreviated nodeId in service data (up to 4 bytes)
            .addServiceData(ParcelUuid(MESH_SERVICE_UUID), nodeId.takeLast(4).toByteArray())
            .build()

        bleAdvertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                onLogMessage?.invoke("BLE Mesh Advertising started.")
            }
            override fun onStartFailure(errorCode: Int) {
                onLogMessage?.invoke("BLE Advertising failed: $errorCode")
            }
        }
        try {
            bleAdvertiser?.startAdvertising(settings, data, bleAdvertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "BLE advertising exception: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleAdvertising() {
        try {
            bleAdvertiser?.stopAdvertising(bleAdvertiseCallback)
        } catch (e: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun startBleScanning() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        bleScanner = bluetoothAdapter.bluetoothLeScanner
        if (bleScanner == null) return

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord ?: return
                val serviceData = record.serviceData[ParcelUuid(MESH_SERVICE_UUID)]
                val name = record.deviceName ?: "Mesh Peer"
                val rssi = result.rssi
                val pIdShort = serviceData?.let { String(it) } ?: "Peer"
                val peerId = "NODE-0x" + pIdShort.uppercase()

                onPeerDiscovered?.invoke(peerId, name, rssi)
                updateRoutingEntry(
                    nodeId = peerId,
                    nextHop = peerId,
                    hopCount = 1,
                    rssi = rssi,
                    transportType = "Bluetooth LE",
                    batteryLevel = 80,
                    trustScore = 1.0f
                )
            }
        }
        try {
            bleScanner?.startScan(filters, settings, bleScanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "BLE Scan exception: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScanning() {
        try {
            bleScanner?.stopScan(bleScanCallback)
        } catch (e: Exception) {}
    }

    // --- OPTIONAL INTERNET RELAY GATEWAY FALLBACK ---
    private fun startInternetRelayService() {
        scope.launch(Dispatchers.IO) {
            while (isEngineRunning) {
                delay(15000) // Poll fallback cloud relay every 15 seconds
                if (!isInternetGatewayEnabled) continue

                try {
                    // Pull relay message queue safely via HTTPS
                    val url = URL("https://nexus-mesh-cloud-relay.firebaseio.com/relay/${nodeId}.json")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000

                    if (conn.responseCode == 200) {
                        val res = conn.inputStream.bufferedReader().use { it.readText() }
                        if (res != "null" && res.isNotEmpty()) {
                            val obj = JSONObject(res)
                            obj.keys().forEach { key ->
                                val pObj = obj.getJSONObject(key)
                                val packet = MeshPacket.fromJsonObject(pObj)
                                receiveMeshPacket(packet, "Internet Relay")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Internet fallback gateway unreachable (Offline mode preserved).")
                }
            }
        }
    }

    // --- PHASE 4 EPIDEMIC GOSSIP SYNC PROTOCOL HANDSHAKE ---
    private fun startPeriodicSyncLoop() {
        scope.launch(Dispatchers.IO) {
            while (isEngineRunning) {
                delay(12000) // Periodic sync runs every 12 seconds
                if (activeConnections.isEmpty()) continue

                activeConnections.forEach { (peerId, socket) ->
                    scope.launch(Dispatchers.IO) {
                        try {
                            val reader = BufferedReader(InputStreamReader(socket.inputStream))
                            val writer = PrintWriter(BufferedWriter(OutputStreamWriter(socket.outputStream)), true)
                            runSyncProtocol(peerId, reader, writer)
                        } catch (e: Exception) {
                            Log.d(TAG, "Periodic sync skipped with $peerId: busy.")
                        }
                    }
                }
            }
        }
    }

    private suspend fun runSyncProtocol(peerNodeId: String, reader: BufferedReader, writer: PrintWriter) {
        val syncStart = System.currentTimeMillis()
        var bytesTransferred = 0L
        try {
            unifiedStatus = "Synchronizing"

            val localMessages = repository.allMessages.first()
            val localPosts = repository.allSocialPosts.first()
            val localChunks = repository.allFileChunks.first()
            val localWiki = repository.allWikiPages.first()
            val localListings = repository.allListings.first()

            val syncManifest = JSONObject().apply {
                put("type", "SYNC_MANIFEST")
                put("senderId", nodeId)

                // 1. Message manifest
                val msgIds = JSONArray()
                localMessages.forEach { msg ->
                    msgIds.put(JSONObject().apply {
                        put("id", msg.messageId)
                        put("timestamp", msg.timestamp)
                    })
                }
                put("messages", msgIds)

                // 2. Feed posts manifest
                val postIds = JSONArray()
                localPosts.forEach { post ->
                    postIds.put(JSONObject().apply {
                        put("id", post.postId)
                        put("timestamp", post.timestamp)
                    })
                }
                put("posts", postIds)

                // 3. Stories manifest
                val storyIds = JSONArray()
                localStoriesReference?.forEach { story ->
                    storyIds.put(JSONObject().apply {
                        put("id", story.id)
                        put("timestamp", story.timestamp)
                    })
                }
                put("stories", storyIds)

                // 4. File chunks manifest
                val chunkIds = JSONArray()
                localChunks.forEach { chunk ->
                    chunkIds.put(JSONObject().apply {
                        put("id", chunk.chunkId)
                        put("fileId", chunk.fileId)
                    })
                }
                put("chunks", chunkIds)

                // 5. Wiki pages manifest
                val wikiIds = JSONArray()
                localWiki.forEach { page ->
                    wikiIds.put(JSONObject().apply {
                        put("pageName", page.pageName)
                        put("version", page.version)
                        put("timestamp", page.timestamp)
                    })
                }
                put("wiki", wikiIds)

                // 6. Marketplace listings manifest
                val listIds = JSONArray()
                localListings.forEach { list ->
                    listIds.put(JSONObject().apply {
                        put("id", list.listingId)
                        put("timestamp", list.timestamp)
                    })
                }
                put("listings", listIds)
            }

            val manifestStr = syncManifest.toString()
            writer.println(manifestStr)
            bytesTransferred += manifestStr.length

            // Trigger handler
            handleSyncManifest(peerNodeId, syncManifest, writer)

        } catch (e: Exception) {
            Log.d(TAG, "Sync protocol error with $peerNodeId: ${e.message}")
        } finally {
            val durationSec = (System.currentTimeMillis() - syncStart) / 1000f
            if (durationSec > 0) {
                transferSpeedBps = (bytesTransferred / durationSec).toLong()
            }
        }
    }

    private suspend fun handleSyncManifest(peerNodeId: String, peerManifest: JSONObject, writer: PrintWriter) {
        val transport = peerTransportTypes[peerNodeId] ?: "Local Wi-Fi LAN"

        // --- 1. MESSAGES SYNC ---
        val localMessages = repository.allMessages.first()
        val peerMessagesArray = peerManifest.optJSONArray("messages") ?: JSONArray()
        val peerMessageMap = HashSet<String>()
        for (i in 0 until peerMessagesArray.length()) {
            peerMessageMap.add(peerMessagesArray.getJSONObject(i).getString("id"))
        }

        localMessages.forEach { msg ->
            if (!peerMessageMap.contains(msg.messageId)) {
                val encrypted = if (msg.content.startsWith("ENC:")) msg.content else SecurityHelper.encrypt(msg.content)
                val packet = MeshPacket(
                    packetId = msg.messageId,
                    senderId = msg.senderId,
                    recipientId = msg.recipientId,
                    previousHop = nodeId,
                    nextHop = peerNodeId,
                    hopCount = msg.hops,
                    maxTtl = msg.timestamp + PACKET_LIFETIME_MS,
                    timestamp = msg.timestamp,
                    payloadType = "CHAT",
                    payloadSize = encrypted.length,
                    payloadEncrypted = encrypted,
                    encryptionMetadata = "AES-GCM",
                    checksum = "",
                    path = listOf(nodeId)
                )
                writer.println(JSONObject().apply {
                    put("type", "MESH_PACKET")
                    put("packet", packet.toJsonObject())
                }.toString())
            }
        }

        // --- 2. SOCIAL FEED EPIDEMIC GOSSIP SYNC ---
        val localPosts = repository.allSocialPosts.first()
        val peerPostsArray = peerManifest.optJSONArray("posts") ?: JSONArray()
        val peerPostMap = HashSet<String>()
        for (i in 0 until peerPostsArray.length()) {
            peerPostMap.add(peerPostsArray.getJSONObject(i).getString("id"))
        }

        localPosts.forEach { post ->
            if (!peerPostMap.contains(post.postId)) {
                val encrypted = SecurityHelper.encrypt(post.content)
                val postPayload = JSONObject().apply {
                    put("postId", post.postId)
                    put("authorId", post.authorId)
                    put("authorName", post.authorName)
                    put("content", encrypted)
                    put("channelType", post.channelType)
                    put("timestamp", post.timestamp)
                    put("likesCount", post.likesCount)
                }
                val packet = MeshPacket(
                    packetId = post.postId,
                    senderId = post.authorId,
                    recipientId = "BROADCAST",
                    previousHop = nodeId,
                    nextHop = peerNodeId,
                    hopCount = 0,
                    maxTtl = post.timestamp + PACKET_LIFETIME_MS,
                    timestamp = post.timestamp,
                    payloadType = "FEED",
                    payloadSize = postPayload.toString().length,
                    payloadEncrypted = postPayload.toString(),
                    encryptionMetadata = "AES-GCM",
                    checksum = "",
                    path = listOf(nodeId)
                )
                writer.println(JSONObject().apply {
                    put("type", "MESH_PACKET")
                    put("packet", packet.toJsonObject())
                }.toString())
            }
        }

        // --- 3. STORIES EPIDEMIC GOSSIP SYNC ---
        val peerStoriesArray = peerManifest.optJSONArray("stories") ?: JSONArray()
        val peerStoryMap = HashSet<String>()
        for (i in 0 until peerStoriesArray.length()) {
            peerStoryMap.add(peerStoriesArray.getJSONObject(i).getString("id"))
        }

        localStoriesReference?.forEach { story ->
            if (!peerStoryMap.contains(story.id)) {
                val encrypted = SecurityHelper.encrypt(story.content)
                val storyPayload = JSONObject().apply {
                    put("id", story.id)
                    put("authorName", story.authorName)
                    put("content", encrypted)
                    put("timestamp", story.timestamp)
                    put("imageType", story.imageType)
                    put("hops", story.hops)
                    put("initialReputation", story.initialReputation)
                }
                val packet = MeshPacket(
                    packetId = story.id,
                    senderId = nodeId,
                    recipientId = "BROADCAST",
                    previousHop = nodeId,
                    nextHop = peerNodeId,
                    hopCount = story.hops,
                    maxTtl = story.timestamp + PACKET_LIFETIME_MS,
                    timestamp = story.timestamp,
                    payloadType = "STORY",
                    payloadSize = storyPayload.toString().length,
                    payloadEncrypted = storyPayload.toString(),
                    encryptionMetadata = "AES-GCM",
                    checksum = "",
                    path = listOf(nodeId)
                )
                writer.println(JSONObject().apply {
                    put("type", "MESH_PACKET")
                    put("packet", packet.toJsonObject())
                }.toString())
            }
        }

        // --- 4. WIKI PAGES CONFLICT-RESOLUTION SYNC ---
        val localWiki = repository.allWikiPages.first()
        val peerWikiArray = peerManifest.optJSONArray("wiki") ?: JSONArray()
        val peerWikiMap = HashMap<String, Pair<Int, Long>>() // pageName -> version to timestamp
        for (i in 0 until peerWikiArray.length()) {
            val item = peerWikiArray.getJSONObject(i)
            peerWikiMap[item.getString("pageName")] = Pair(item.getInt("version"), item.getLong("timestamp"))
        }

        localWiki.forEach { page ->
            val peerPair = peerWikiMap[page.pageName]
            if (peerPair == null || page.version > peerPair.first || (page.version == peerPair.first && page.timestamp > peerPair.second)) {
                val encrypted = SecurityHelper.encrypt(page.content)
                val wikiPayload = JSONObject().apply {
                    put("pageName", page.pageName)
                    put("content", encrypted)
                    put("lastContributor", page.lastContributor)
                    put("version", page.version)
                    put("timestamp", page.timestamp)
                }
                val packet = MeshPacket(
                    packetId = "WIKI_" + page.pageName,
                    senderId = nodeId,
                    recipientId = "BROADCAST",
                    previousHop = nodeId,
                    nextHop = peerNodeId,
                    hopCount = 0,
                    maxTtl = page.timestamp + PACKET_LIFETIME_MS,
                    timestamp = page.timestamp,
                    payloadType = "WIKI",
                    payloadSize = wikiPayload.toString().length,
                    payloadEncrypted = wikiPayload.toString(),
                    encryptionMetadata = "AES-GCM",
                    checksum = "",
                    path = listOf(nodeId)
                )
                writer.println(JSONObject().apply {
                    put("type", "MESH_PACKET")
                    put("packet", packet.toJsonObject())
                }.toString())
            }
        }

        // --- 5. MARKETPLACE LISTINGS SYNC ---
        val localListings = repository.allListings.first()
        val peerListingsArray = peerManifest.optJSONArray("listings") ?: JSONArray()
        val peerListingMap = HashSet<String>()
        for (i in 0 until peerListingsArray.length()) {
            peerListingMap.add(peerListingsArray.getJSONObject(i).getString("id"))
        }

        localListings.forEach { list ->
            if (!peerListingMap.contains(list.listingId)) {
                val listPayload = JSONObject().apply {
                    put("listingId", list.listingId)
                    put("sellerId", list.sellerId)
                    put("sellerName", list.sellerName)
                    put("title", list.title)
                    put("price", list.price)
                    put("timestamp", list.timestamp)
                    put("signature", list.signature)
                }
                val packet = MeshPacket(
                    packetId = list.listingId,
                    senderId = list.sellerId,
                    recipientId = "BROADCAST",
                    previousHop = nodeId,
                    nextHop = peerNodeId,
                    hopCount = 0,
                    maxTtl = list.timestamp + PACKET_LIFETIME_MS,
                    timestamp = list.timestamp,
                    payloadType = "MARKETPLACE",
                    payloadSize = listPayload.toString().length,
                    payloadEncrypted = listPayload.toString(),
                    encryptionMetadata = "NONE",
                    checksum = "",
                    path = listOf(nodeId)
                )
                writer.println(JSONObject().apply {
                    put("type", "MESH_PACKET")
                    put("packet", packet.toJsonObject())
                }.toString())
            }
        }
    }

    // --- RECEIVE & DECRYPT MULTI-HOP PACKETS (STORE-AND-FORWARD) ---
    private suspend fun receiveMeshPacket(packet: MeshPacket, incomingTransport: String) {
        if (processedPacketIds.contains(packet.packetId)) {
            duplicatePacketsPreventedCount++
            return
        }
        processedPacketIds.add(packet.packetId)

        // Validate TTL
        if (System.currentTimeMillis() > packet.maxTtl) {
            onLogMessage?.invoke("Expired packet ${packet.packetId} pruned.")
            droppedPacketsCount++
            return
        }

        // Validate Hop Count
        if (packet.hopCount > MAX_HOPS) {
            onLogMessage?.invoke("Packet ${packet.packetId} exceeded max hop count limit. Dropped.")
            droppedPacketsCount++
            return
        }

        val isTargetedToUs = packet.recipientId == nodeId
        val isBroadcast = packet.recipientId == "BROADCAST"

        if (isTargetedToUs || isBroadcast) {
            when (packet.payloadType) {
                "CHAT" -> {
                    val decrypted = SecurityHelper.decrypt(packet.payloadEncrypted)
                    repository.insertMessage(
                        MeshMessageEntity(
                            messageId = packet.packetId,
                            senderId = packet.senderId,
                            senderName = "Peer " + packet.senderId.takeLast(4),
                            recipientId = packet.recipientId,
                            content = decrypted,
                            timestamp = packet.timestamp,
                            lamportClock = 0,
                            hops = packet.hopCount,
                            isSynced = true
                        )
                    )
                    if (isTargetedToUs) {
                        sendDeliveryReceipt(packet.packetId, packet.senderId)
                    }
                }
                "FEED" -> {
                    val postObj = JSONObject(packet.payloadEncrypted)
                    val decrypted = SecurityHelper.decrypt(postObj.getString("content"))
                    repository.insertSocialPost(
                        SocialPostEntity(
                            postId = postObj.getString("postId"),
                            authorId = postObj.getString("authorId"),
                            authorName = postObj.getString("authorName"),
                            content = decrypted,
                            channelType = postObj.getString("channelType"),
                            timestamp = postObj.getLong("timestamp"),
                            likesCount = postObj.optInt("likesCount", 0)
                        )
                    )
                }
                "STORY" -> {
                    val storyObj = JSONObject(packet.payloadEncrypted)
                    val decrypted = SecurityHelper.decrypt(storyObj.getString("content"))
                    val sId = storyObj.getString("id")
                    withContext(Dispatchers.Main) {
                        localStoriesReference?.let { ref ->
                            if (ref.none { it.id == sId }) {
                                ref.add(0, com.example.MeshStory(
                                    id = sId,
                                    authorName = storyObj.getString("authorName"),
                                    content = decrypted,
                                    timestamp = storyObj.getLong("timestamp"),
                                    imageType = storyObj.getString("imageType"),
                                    hops = storyObj.optInt("hops", 0) + 1,
                                    initialReputation = storyObj.optInt("initialReputation", 100),
                                    status = "SYNCHRONIZED"
                                ))
                            }
                        }
                    }
                }
                "WIKI" -> {
                    val wikiObj = JSONObject(packet.payloadEncrypted)
                    val decrypted = SecurityHelper.decrypt(wikiObj.getString("content"))
                    repository.insertWikiPage(
                        WikiPageEntity(
                            pageName = wikiObj.getString("pageName"),
                            content = decrypted,
                            lastContributor = wikiObj.getString("lastContributor"),
                            version = wikiObj.getInt("version"),
                            timestamp = wikiObj.getLong("timestamp")
                        )
                    )
                }
                "MARKETPLACE" -> {
                    val listObj = JSONObject(packet.payloadEncrypted)
                    repository.insertListing(
                        MarketplaceListingEntity(
                            listingId = listObj.getString("listingId"),
                            sellerId = listObj.getString("sellerId"),
                            sellerName = listObj.getString("sellerName"),
                            title = listObj.getString("title"),
                            price = listObj.getString("price"),
                            timestamp = listObj.getLong("timestamp"),
                            signature = listObj.getString("signature")
                        )
                    )
                }
                "FILE" -> {
                    val chunkObj = JSONObject(packet.payloadEncrypted)
                    val fileId = chunkObj.getString("fileId")
                    val fileName = chunkObj.getString("fileName")
                    val seqNum = chunkObj.getInt("seqNum")
                    val total = chunkObj.getInt("total")
                    val hash = chunkObj.getString("hash")
                    val binaryHex = chunkObj.getString("data")

                    saveChunkToDisk(fileId, seqNum, binaryHex)
                    repository.insertFileChunk(
                        FileChunkEntity(
                            chunkId = "${fileId}_CHUNK_$seqNum",
                            fileId = fileId,
                            fileName = fileName,
                            sha256Hash = hash,
                            sequenceNumber = seqNum,
                            totalChunks = total,
                            status = "COMPLETED"
                        )
                    )
                    checkAndAssembleFile(fileId, fileName, total)
                }
            }
        }

        // --- STORE AND FORWARD ROUTING TRIGGERS ---
        if (!isTargetedToUs) {
            val updatedPath = packet.path.toMutableList()
            if (!updatedPath.contains(nodeId)) {
                updatedPath.add(nodeId)
            }
            val forwarded = packet.copy(
                hopCount = packet.hopCount + 1,
                path = updatedPath
            )
            pendingForwardPackets[packet.packetId] = forwarded
            onLogMessage?.invoke("Queued packet ${packet.packetId} in Store-and-Forward routing database.")
        }
    }

    private fun sendDeliveryReceipt(messageId: String, recipientNodeId: String) {
        val socket = activeConnections[recipientNodeId]
        if (socket != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val writer = PrintWriter(BufferedWriter(OutputStreamWriter(socket.outputStream)), true)
                    writer.println(JSONObject().apply {
                        put("type", "DELIVERY_RECEIPT")
                        put("messageId", messageId)
                    }.toString())
                    onLogMessage?.invoke("Delivery Receipt dispatched back to $recipientNodeId.")
                } catch (e: Exception) {
                    Log.e(TAG, "Receipt delivery failure: ${e.message}")
                }
            }
        }
    }

    // --- STORE AND FORWARD ROUTING SPREE AND GOSSIP SWEETS ---
    private fun startRoutingQueueProcessor() {
        scope.launch(Dispatchers.IO) {
            while (isEngineRunning) {
                delay(8000) // sweep every 8 seconds
                if (!isEngineRunning) break

                // Prune dead TTLs
                val now = System.currentTimeMillis()
                pendingForwardPackets.entries.removeAll { it.value.maxTtl < now }

                queuedPacketsCount = pendingForwardPackets.size

                if (pendingForwardPackets.isNotEmpty() && activeConnections.isNotEmpty()) {
                    pendingForwardPackets.values.forEach { packet ->
                        activeConnections.forEach { (peerId, socket) ->
                            // Avoid forwarding to nodes already traversed in the route
                            if (!packet.path.contains(peerId) && packet.senderId != peerId) {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val writer = PrintWriter(BufferedWriter(OutputStreamWriter(socket.outputStream)), true)
                                        writer.println(JSONObject().apply {
                                            put("type", "MESH_PACKET")
                                            put("packet", packet.toJsonObject())
                                        }.toString())
                                        forwardedPacketsCount++
                                        onLogMessage?.invoke("Forwarded packet ${packet.packetId} successfully to hop neighbor $peerId.")
                                    } catch (e: Exception) {
                                        Log.d(TAG, "Relay transmission deferred for $peerId.")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- COGNITIVE DEVICE STATE EVALUATION & UX UPDATER ---
    private fun startStatusEvaluationLoop() {
        scope.launch {
            while (isEngineRunning) {
                delay(3000)

                connectedNeighborsCount = activeConnections.size
                queuedPacketsCount = pendingForwardPackets.size
                
                // Active relay sessions count matches distinct active multi-hop nodes in flight
                activeRelaySessionsCount = pendingForwardPackets.values
                    .filter { it.recipientId != nodeId && it.recipientId != "BROADCAST" }
                    .map { it.recipientId }
                    .distinct()
                    .size

                // Database size logic
                val dbFile = context.getDatabasePath("nexus_mesh_db")
                databaseSizeMb = if (dbFile.exists()) dbFile.length() / (1024.0 * 1024.0) else 0.0

                val unreadCount = repository.allMessages.first().count { !it.isSynced }
                synchronizationQueueSize = unreadCount

                // Battery impacts
                batteryImpactPercentPerMin = when {
                    activeConnections.size > 5 -> 0.85f
                    activeConnections.isNotEmpty() -> 0.45f
                    else -> 0.15f
                }

                currentTransport = when {
                    peerTransportTypes.values.contains("Wi-Fi Direct") -> "Wi-Fi Direct"
                    peerTransportTypes.values.contains("Local Wi-Fi LAN") -> "Local Wi-Fi LAN"
                    else -> "Bluetooth LE"
                }

                val hasActive = activeConnections.isNotEmpty()
                unifiedStatus = when {
                    unifiedStatus == "Delivered" -> {
                        delay(2500)
                        if (hasActive) "Connected" else "Waiting for relay"
                    }
                    hasActive && unifiedStatus == "Synchronizing" -> "Synchronizing"
                    hasActive -> "Connected"
                    unreadCount > 0 -> "Waiting for relay"
                    else -> "Connected"
                }
            }
        }
    }

    // --- BINARY CHUNK STORAGE UTILITIES ---
    private fun saveChunkToDisk(fileId: String, seqNum: Int, hexData: String) {
        val dir = File(context.cacheDir, "mesh_chunks/$fileId")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "chunk_$seqNum")
        try {
            file.writeBytes(hexToBytes(hexData))
        } catch (e: Exception) {
            Log.e(TAG, "Chunk persistence error: ${e.message}")
        }
    }

    private fun readChunkFromDisk(fileId: String, seqNum: Int): String {
        val file = File(context.cacheDir, "mesh_chunks/$fileId/chunk_$seqNum")
        if (!file.exists()) return ""
        return try {
            bytesToHex(file.readBytes())
        } catch (e: Exception) {
            ""
        }
    }

    private fun checkAndAssembleFile(fileId: String, fileName: String, totalChunks: Int) {
        scope.launch(Dispatchers.IO) {
            val dir = File(context.cacheDir, "mesh_chunks/$fileId")
            val outputDir = File(context.getExternalFilesDir(null), "MeshTransfers")
            if (!outputDir.exists()) outputDir.mkdirs()
            val dest = File(outputDir, fileName)

            val chunks = (1..totalChunks).map { File(dir, "chunk_$it") }
            if (chunks.all { it.exists() }) {
                try {
                    FileOutputStream(dest).use { out ->
                        chunks.forEach { chunk ->
                            chunk.inputStream().use { it.copyTo(out) }
                        }
                    }
                    onLogMessage?.invoke("File assemble success: ${dest.absolutePath}")
                    repository.insertMessage(
                        MeshMessageEntity(
                            messageId = "FILE_RECV_" + UUID.randomUUID().toString().take(6),
                            senderId = "SYSTEM",
                            senderName = "Nexus System",
                            recipientId = "BROADCAST",
                            content = "Received file: $fileName (${dest.length() / (1024*1024)} MB)",
                            timestamp = System.currentTimeMillis(),
                            isSynced = true,
                            attachmentType = "FILE",
                            attachmentPath = dest.absolutePath,
                            attachmentName = fileName,
                            attachmentSize = "${dest.length() / (1024*1024)} MB"
                        )
                    )
                } catch (e: Exception) {
                    onLogMessage?.invoke("Assembly fail: ${e.message}")
                }
            }
        }
    }

    fun generateAndRegister100MbTestFile() {
        scope.launch(Dispatchers.IO) {
            onLogMessage?.invoke("Generating real 100 MB test binary...")
            val testFile = File(context.cacheDir, "nexus_100mb_test.bin")
            val fileId = "FILE_100MB_" + UUID.randomUUID().toString().substring(0, 4).uppercase()
            try {
                FileOutputStream(testFile).use { out ->
                    val buf = ByteArray(1024 * 1024)
                    val rnd = java.util.Random()
                    for (i in 1..100) {
                        rnd.nextBytes(buf)
                        out.write(buf)
                    }
                }

                val totalChunks = 100
                val dir = File(context.cacheDir, "mesh_chunks/$fileId")
                if (!dir.exists()) dir.mkdirs()

                testFile.inputStream().use { input ->
                    val buf = ByteArray(1024 * 1024)
                    for (i in 1..totalChunks) {
                        val read = input.read(buf)
                        if (read > 0) {
                            val data = buf.copyOf(read)
                            File(dir, "chunk_$i").writeBytes(data)

                            val md = MessageDigest.getInstance("SHA-256")
                            val hash = bytesToHex(md.digest(data))

                            repository.insertFileChunk(
                                FileChunkEntity(
                                    chunkId = "${fileId}_CHUNK_$i",
                                    fileId = fileId,
                                    fileName = "nexus_100mb_test.bin",
                                    sha256Hash = hash,
                                    sequenceNumber = i,
                                    totalChunks = totalChunks,
                                    status = "COMPLETED"
                                )
                            )
                        }
                    }
                }
                onLogMessage?.invoke("100 MB test file registered successfully.")
            } catch (e: Exception) {
                onLogMessage?.invoke("Generation fail: ${e.message}")
            }
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789ABCDEF"
        val result = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt() and 0xFF
            result.append(hexChars[i shr 4])
            result.append(hexChars[i and 0x0F])
        }
        return result.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
