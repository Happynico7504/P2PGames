package net.nicochristmann.p2pgames.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * TCP server run by the session host (the Wi-Fi Direct group owner).
 *
 * Each joining device gets a connection id which doubles as its player id
 * (the host itself is player 0). Incoming messages are reported via
 * [onClientMessage]; both callbacks fire on IO threads, so consumers must
 * hop to the main thread themselves.
 */
class GameHost(
    private val scope: CoroutineScope,
    private val onClientMessage: (clientId: Int, message: JSONObject) -> Unit,
    private val onClientDisconnected: (clientId: Int) -> Unit,
) {
    companion object {
        const val PORT = 8988
    }

    private var serverSocket: ServerSocket? = null
    private val clients = ConcurrentHashMap<Int, ClientConnection>()
    private val nextClientId = AtomicInteger(1)

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(PORT)
                serverSocket = server
                while (isActive && running) {
                    val socket = server.accept()
                    socket.tcpNoDelay = true
                    val id = nextClientId.getAndIncrement()
                    val connection = ClientConnection(id, socket)
                    clients[id] = connection
                    startReadLoop(connection)
                }
            } catch (_: Exception) {
                // Server socket closed (stop()) or failed to bind; nothing to do.
            }
        }
    }

    private fun startReadLoop(connection: ClientConnection) {
        scope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(connection.socket.getInputStream()))
                while (true) {
                    val line = reader.readLine() ?: break
                    val message = runCatching { JSONObject(line) }.getOrNull() ?: continue
                    onClientMessage(connection.id, message)
                }
            } catch (_: Exception) {
                // Fall through to cleanup below.
            } finally {
                clients.remove(connection.id)
                connection.close()
                if (running) onClientDisconnected(connection.id)
            }
        }
    }

    fun send(clientId: Int, message: JSONObject) {
        val connection = clients[clientId] ?: return
        scope.launch(Dispatchers.IO) { connection.send(message) }
    }

    fun broadcast(message: JSONObject) {
        val snapshot = clients.values.toList()
        if (snapshot.isEmpty()) return
        scope.launch(Dispatchers.IO) { snapshot.forEach { it.send(message) } }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        clients.values.forEach { it.close() }
        clients.clear()
    }

    private class ClientConnection(val id: Int, val socket: Socket) {
        private val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()))

        @Synchronized
        fun send(message: JSONObject) {
            try {
                writer.print(message.toString())
                writer.print("\n")
                writer.flush()
            } catch (_: Exception) {
                // Read loop will notice the broken socket and clean up.
            }
        }

        fun close() {
            runCatching { socket.close() }
        }
    }
}
