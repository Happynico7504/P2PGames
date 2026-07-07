package net.nicochristmann.p2pgames.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * TCP client used by joining devices to talk to the session host (the Wi-Fi
 * Direct group owner). All callbacks fire on IO threads; consumers must hop
 * to the main thread themselves.
 */
class GameClient(
    private val scope: CoroutineScope,
    private val onConnected: () -> Unit,
    private val onMessage: (JSONObject) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onConnectFailed: (String) -> Unit,
) {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null

    @Volatile
    private var closed = false

    /**
     * Connects to the host with a few retries — the group can form a moment
     * before the host's server socket starts accepting.
     */
    fun connect(address: InetAddress, port: Int = GameHost.PORT) {
        scope.launch(Dispatchers.IO) {
            var lastError: Exception? = null
            repeat(5) {
                if (closed) return@launch
                try {
                    val s = Socket()
                    s.connect(InetSocketAddress(address, port), 4000)
                    s.tcpNoDelay = true
                    socket = s
                    writer = PrintWriter(OutputStreamWriter(s.getOutputStream()))
                    onConnected()
                    readLoop(s)
                    return@launch
                } catch (e: Exception) {
                    lastError = e
                }
                delay(1000)
            }
            if (!closed) onConnectFailed(lastError?.message ?: "could not reach host")
        }
    }

    private fun readLoop(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            while (true) {
                val line = reader.readLine() ?: break
                val message = runCatching { JSONObject(line) }.getOrNull() ?: continue
                onMessage(message)
            }
        } catch (_: Exception) {
            // Fall through to cleanup below.
        } finally {
            if (!closed) {
                closed = true
                runCatching { socket.close() }
                onDisconnected()
            }
        }
    }

    fun send(message: JSONObject) {
        val w = writer ?: return
        scope.launch(Dispatchers.IO) {
            try {
                synchronized(w) {
                    w.print(message.toString())
                    w.print("\n")
                    w.flush()
                }
            } catch (_: Exception) {
                // Read loop will notice the broken socket.
            }
        }
    }

    fun close() {
        closed = true
        runCatching { socket?.close() }
    }
}
