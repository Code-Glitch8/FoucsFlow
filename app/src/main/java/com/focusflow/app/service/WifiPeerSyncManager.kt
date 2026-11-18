package com.focusflow.app.service

import com.focusflow.app.data.repo.HistoryRepository
import com.focusflow.app.data.security.PairingCrypto
import com.focusflow.app.data.sync.LocalSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

/** LAN-only peer discovery plus encrypted TCP exchange. No remote server or database is involved. */
class WifiPeerSyncManager(
    private val sync: LocalSyncRepository,
    private val history: HistoryRepository,
) {
    private var job: Job? = null
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private val lastPeerSync = ConcurrentHashMap<String, Long>()
    private val _status = kotlinx.coroutines.flow.MutableStateFlow("Set the same sync code on both phones")
    val status: kotlinx.coroutines.flow.StateFlow<String> = _status

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) { runSyncLoop() }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun syncNow() {
        wakeups.trySend(Unit)
    }

    private suspend fun runSyncLoop() {
        val server = ServerSocket()
        val discovery = DatagramSocket(null)
        try {
            server.reuseAddress = true
            server.bind(InetSocketAddress("0.0.0.0", TCP_PORT))
            discovery.reuseAddress = true
            discovery.soTimeout = 1_000
            discovery.broadcast = true
            discovery.bind(InetSocketAddress("0.0.0.0", UDP_PORT))
            coroutineScope {
                val acceptJob = launch(Dispatchers.IO) {
                    while (isActive) {
                        val client = try { server.accept() } catch (_: Exception) { break }
                        launch(Dispatchers.IO) { handleIncoming(client) }
                    }
                }
                val discoveryJob = launch(Dispatchers.IO) {
                    val buffer = ByteArray(256)
                    while (isActive) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            discovery.receive(packet)
                            val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                            val parts = text.split('|')
                            if (parts.size == 3 && parts[0] == DISCOVERY_TAG) {
                                val peerId = parts[1]
                                val peerPort = parts[2].toIntOrNull() ?: continue
                                if (peerId.isNotBlank() && sync.deviceId < peerId) {
                                    val now = System.currentTimeMillis()
                                    val last = lastPeerSync[peerId] ?: 0L
                                    if (now - last > PEER_COOLDOWN_MS) {
                                        lastPeerSync[peerId] = now
                                        launch(Dispatchers.IO) { syncWithPeer(packet.address, peerPort) }
                                    }
                                }
                            }
                        } catch (_: SocketTimeoutException) {
                            // Timeout allows cancellation checks.
                        } catch (_: Exception) {
                            if (isActive) delay(250)
                        }
                    }
                }
                try {
                    while (isActive) {
                        if (sync.pairingCode.value.isNullOrBlank()) {
                            _status.value = "Set the same sync code on both phones"
                        } else {
                            announce(discovery)
                            _status.value = "Searching for your paired phone on this Wi-Fi"
                        }
                        withTimeoutOrNull(DISCOVERY_INTERVAL_MS) { wakeups.receive() }
                    }
                } finally {
                    acceptJob.cancelAndJoin()
                    discoveryJob.cancelAndJoin()
                }
            }
        } catch (e: Exception) {
            _status.value = "Local sync unavailable: ${e.message ?: "network or Wi-Fi issue"}"
        } finally {
            runCatching { discovery.close() }
            runCatching { server.close() }
        }
    }

    private fun announce(socket: DatagramSocket) {
        val bytes = "$DISCOVERY_TAG|${sync.deviceId}|$TCP_PORT".toByteArray(Charsets.UTF_8)
        val targets = linkedSetOf<InetAddress>()
        runCatching { targets.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val network = interfaces.nextElement()
                if (!network.isUp || network.isLoopback) continue
                network.interfaceAddresses.mapNotNullTo(targets) { it.broadcast }
            }
        }
        targets.forEach { address ->
            runCatching { socket.send(DatagramPacket(bytes, bytes.size, address, UDP_PORT)) }
        }
    }

    private suspend fun syncWithPeer(address: InetAddress, port: Int) = withContext(Dispatchers.IO) {
        val pairing = sync.pairingCode.value ?: return@withContext
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, port), 4_000)
                socket.soTimeout = 10_000
                val local = history.exportSyncRecords(sync.deviceId)
                writeFrame(socket, PairingCrypto.encrypt(sync.buildPayload(local), pairing))
                val reply = readFrame(socket) ?: return@use
                sync.mergePayload(PairingCrypto.decrypt(reply, pairing))
                _status.value = "Synced · ${sync.records.value.size} shared sessions"
            }
        } catch (e: Exception) {
            _status.value = "Sync attempt failed: ${e.message ?: "connection failed"}"
        }
    }

    private suspend fun handleIncoming(socket: Socket) = withContext(Dispatchers.IO) {
        socket.use { client ->
            client.soTimeout = 10_000
            val pairing = sync.pairingCode.value ?: return@withContext
            try {
                val request = readFrame(client) ?: return@withContext
                sync.mergePayload(PairingCrypto.decrypt(request, pairing))
                val local = history.exportSyncRecords(sync.deviceId)
                writeFrame(client, PairingCrypto.encrypt(sync.buildPayload(local), pairing))
                _status.value = "Synced · ${sync.records.value.size} shared sessions"
            } catch (_: Exception) {
                // Wrong pairing codes and invalid/truncated frames are ignored.
            }
        }
    }

    private fun writeFrame(socket: Socket, bytes: ByteArray) {
        require(bytes.size in 1..MAX_FRAME_BYTES)
        DataOutputStream(socket.getOutputStream()).apply {
            writeInt(bytes.size)
            write(bytes)
            flush()
        }
    }

    private fun readFrame(socket: Socket): ByteArray? {
        val input = DataInputStream(socket.getInputStream())
        val size = input.readInt()
        if (size !in 1..MAX_FRAME_BYTES) return null
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return bytes
    }

    companion object {
        private const val UDP_PORT = 43_171
        private const val TCP_PORT = 43_172
        private const val DISCOVERY_TAG = "FOCUSFLOWv1"
        private const val DISCOVERY_INTERVAL_MS = 10_000L
        private const val PEER_COOLDOWN_MS = 12_000L
        private const val MAX_FRAME_BYTES = 8 * 1024 * 1024
    }
}
