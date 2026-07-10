package net.nicochristmann.p2pgames

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import net.nicochristmann.p2pgames.game.BattleshipGame
import net.nicochristmann.p2pgames.game.GameUi
import net.nicochristmann.p2pgames.game.GameUiParser
import net.nicochristmann.p2pgames.game.GooseGame
import net.nicochristmann.p2pgames.game.HangmanGame
import net.nicochristmann.p2pgames.game.HostGame
import net.nicochristmann.p2pgames.game.KniffelGame
import net.nicochristmann.p2pgames.game.LudoGame
import net.nicochristmann.p2pgames.game.TicTacToeGame
import net.nicochristmann.p2pgames.game.UnoGame
import net.nicochristmann.p2pgames.game.WordBank
import net.nicochristmann.p2pgames.net.GameClient
import net.nicochristmann.p2pgames.net.GameHost
import net.nicochristmann.p2pgames.net.Msg
import net.nicochristmann.p2pgames.net.Player
import net.nicochristmann.p2pgames.wifi.WifiDirectController
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Which screen the app is currently showing. */
sealed interface Screen {
    data object Home : Screen
    data object HostLobby : Screen
    data object Discover : Screen
    data object ClientLobby : Screen
    data object Game : Screen
}

/**
 * Coordinates the whole session: Wi-Fi Direct group/discovery, the TCP
 * host/client, the roster, and the currently running game.
 *
 * The host is authoritative: it owns the rules engine, applies every input
 * (its own directly, clients' via socket messages) and sends each device
 * its own view of the resulting state. Clients only render states and
 * send inputs.
 */
class GameSessionViewModel(app: Application) : AndroidViewModel(app) {

    val wifi = WifiDirectController(app)

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    var playerName by mutableStateOf("")
    var status by mutableStateOf<String?>(null)
        private set
    var players by mutableStateOf<List<Player>>(emptyList())
        private set
    var myPlayerId by mutableStateOf(0)
        private set
    var isHost by mutableStateOf(false)
        private set
    var gameUi by mutableStateOf<GameUi?>(null)
        private set

    private var host: GameHost? = null
    private var client: GameClient? = null
    private var activeGame: HostGame? = null
    private val haptics = GameHaptics(app)

    /** Updates the rendered state and plays the matching haptic feedback. */
    private fun setGameUi(newUi: GameUi?) {
        val old = gameUi
        gameUi = newUi
        if (newUi != null) haptics.onStateChange(old, newUi, myPlayerId)
    }

    init {
        wifi.register()
        viewModelScope.launch {
            wifi.connectionInfo.collect { info -> onConnectionChanged(info) }
        }
    }

    fun nameOf(playerId: Int): String =
        players.firstOrNull { it.id == playerId }?.name ?: "Player $playerId"

    fun onPermissionsDenied() {
        status = "Nearby-devices permission is required to discover and connect to players."
    }

    // ---------------------------------------------------------------- hosting

    fun hostGame() {
        val name = playerName.trim()
        if (name.isEmpty()) return
        if (!wifi.isSupported) {
            status = "Wi-Fi Direct is not supported on this device."
            return
        }
        isHost = true
        myPlayerId = 0
        players = listOf(Player(0, name))
        status = "Starting Wi-Fi Direct group…"
        screen = Screen.HostLobby

        host = GameHost(viewModelScope, ::onHostSocketMessage, ::onHostSocketDisconnect)
            .also { it.start() }

        wifi.createGroup(
            onSuccess = { status = "Session is live — waiting for players to join." },
            onFailure = {
                // A stale group from a previous run is the usual culprit;
                // remove it and try once more.
                wifi.removeGroup {
                    wifi.createGroup(
                        onSuccess = { status = "Session is live — waiting for players to join." },
                        onFailure = { reason -> status = reason },
                    )
                }
            },
        )
    }

    /** Raw socket callback (IO thread) — hop to the main thread. */
    private fun onHostSocketMessage(clientId: Int, message: JSONObject) {
        viewModelScope.launch { handleClientMessage(clientId, message) }
    }

    private fun onHostSocketDisconnect(clientId: Int) {
        viewModelScope.launch { handleClientLeft(clientId) }
    }

    private fun handleClientMessage(clientId: Int, message: JSONObject) {
        when (message.optString(Msg.TYPE)) {
            Msg.HELLO -> {
                val name = message.optString("name").ifBlank { "Player $clientId" }.take(24)
                players = players + Player(clientId, name)
                host?.send(clientId, Msg.welcome(clientId, players))
                host?.broadcast(Msg.roster(players))
                // Late joiners land in a running game as spectators.
                activeGame?.let { game ->
                    host?.send(clientId, Msg.start(game.gameId, game.toJsonFor(clientId)))
                }
                status = "$name joined."
            }
            Msg.INPUT -> {
                val game = activeGame ?: return
                val data = message.optJSONObject("data") ?: return
                if (game.input(clientId, data)) publishGame()
            }
        }
    }

    private fun handleClientLeft(clientId: Int) {
        val leaving = players.firstOrNull { it.id == clientId } ?: return
        players = players.filterNot { it.id == clientId }
        host?.broadcast(Msg.roster(players))
        status = "${leaving.name} left."

        activeGame?.let { game ->
            if (!game.isFinished && !game.playerLeft(clientId)) {
                returnToLobby("${leaving.name} left the game.")
            } else {
                // The departure may have changed whose turn it is etc.
                publishGame()
            }
        }
    }

    /** Host: creates and starts the selected game with the current roster. */
    fun startSelectedGame(gameId: String) {
        if (!isHost || players.size < 2) return
        val ids = players.map { it.id }
        val game: HostGame = when (gameId) {
            Msg.GAME_TTT -> TicTacToeGame(xPlayerId = ids[0], oPlayerId = ids[1])
            Msg.GAME_LUDO -> LudoGame(ids.take(4))
            Msg.GAME_UNO -> UnoGame(ids.take(8))
            Msg.GAME_GOOSE -> GooseGame(ids.take(8))
            Msg.GAME_KNIFFEL -> KniffelGame(ids.take(8))
            Msg.GAME_BATTLESHIP -> BattleshipGame(ids[0], ids[1])
            else -> return
        }
        launchGame(game)
    }

    /** [customWord] null means "pick a random word that even the host doesn't know". */
    fun startHangman(customWord: String?) {
        if (!isHost || players.size < 2) return
        val word: String
        val setterId: Int
        if (customWord != null) {
            word = customWord.trim().lowercase()
            if (word.length < 3 || !word.all { it in 'a'..'z' }) {
                status = "The word must be 3+ letters, A–Z only."
                return
            }
            setterId = 0
        } else {
            word = WordBank.random()
            setterId = HangmanGame.NO_SETTER
        }
        val guessers = players.map { it.id }.filter { it != setterId }
        launchGame(HangmanGame(word, setterId, guessers))
    }

    private fun launchGame(game: HostGame) {
        activeGame = game
        players.forEach { p ->
            if (p.id != 0) host?.send(p.id, Msg.start(game.gameId, game.toJsonFor(p.id)))
        }
        setGameUi(GameUiParser.parse(game.gameId, game.toJsonFor(0)))
        screen = Screen.Game
        status = null
    }

    /** Sends every device (including this one) its own view of the state. */
    private fun publishGame() {
        val game = activeGame ?: return
        players.forEach { p ->
            if (p.id != 0) host?.send(p.id, Msg.state(game.gameId, game.toJsonFor(p.id)))
        }
        setGameUi(GameUiParser.parse(game.gameId, game.toJsonFor(0)))
    }

    /** Host: ends the current game for everyone and returns to the lobby. */
    fun returnToLobby(message: String? = null) {
        if (!isHost) return
        activeGame = null
        gameUi = null
        host?.broadcast(Msg.lobby(message))
        screen = Screen.HostLobby
        if (message != null) status = message
    }

    // ------------------------------------------------------------------ input

    /** Routes a game input: applied directly when hosting, sent otherwise. */
    fun sendInput(data: JSONObject) {
        if (isHost) {
            val game = activeGame ?: return
            if (game.input(myPlayerId, data)) publishGame()
        } else {
            client?.send(Msg.input(data))
        }
    }

    // ---------------------------------------------------------------- joining

    fun joinGame() {
        val name = playerName.trim()
        if (name.isEmpty()) return
        if (!wifi.isSupported) {
            status = "Wi-Fi Direct is not supported on this device."
            return
        }
        isHost = false
        status = "Searching for nearby sessions…"
        screen = Screen.Discover
        wifi.clearPeers()
        wifi.startDiscovery(onFailure = { reason -> status = reason })
    }

    fun refreshDiscovery() {
        wifi.clearPeers()
        wifi.startDiscovery(onFailure = { reason -> status = reason })
    }

    fun connectToPeer(device: WifiP2pDevice) {
        status = "Connecting to ${device.deviceName.ifBlank { device.deviceAddress }}…"
        wifi.connect(device, onFailure = { reason -> status = reason })
    }

    private fun onConnectionChanged(info: WifiP2pInfo?) {
        if (info != null && info.groupFormed) {
            if (!isHost && !info.isGroupOwner && client == null &&
                info.groupOwnerAddress != null && screen == Screen.Discover
            ) {
                wifi.stopDiscovery()
                status = "Connected — joining session…"
                startClient(info)
            }
        } else {
            // Group dissolved. For a client mid-session that means the host
            // is gone; the socket read loop will also notice, but this is
            // often faster.
            if (!isHost && client != null) {
                onSessionLost("The host closed the session.")
            }
        }
    }

    private fun startClient(info: WifiP2pInfo) {
        val newClient = GameClient(
            scope = viewModelScope,
            onConnected = {
                viewModelScope.launch { client?.send(Msg.hello(playerName.trim())) }
            },
            onMessage = { message -> viewModelScope.launch { handleServerMessage(message) } },
            onDisconnected = { viewModelScope.launch { onSessionLost("Lost connection to the host.") } },
            onConnectFailed = { reason ->
                viewModelScope.launch { onSessionLost("Could not reach the host ($reason).") }
            },
        )
        client = newClient
        newClient.connect(info.groupOwnerAddress)
    }

    private fun handleServerMessage(message: JSONObject) {
        when (message.optString(Msg.TYPE)) {
            Msg.WELCOME -> {
                myPlayerId = message.getInt("playerId")
                players = Msg.parsePlayers(message.getJSONArray("players"))
                if (screen == Screen.Discover) screen = Screen.ClientLobby
                status = "Joined! Waiting for the host to start a game."
            }
            Msg.ROSTER -> players = Msg.parsePlayers(message.getJSONArray("players"))
            Msg.START -> {
                val ui = GameUiParser.parse(
                    message.optString("game"),
                    message.getJSONObject("state"),
                )
                if (ui != null) {
                    setGameUi(ui)
                    screen = Screen.Game
                    status = null
                }
            }
            Msg.STATE -> {
                val ui = GameUiParser.parse(
                    message.optString("game"),
                    message.getJSONObject("state"),
                )
                if (ui != null) setGameUi(ui)
            }
            Msg.LOBBY -> {
                gameUi = null
                screen = Screen.ClientLobby
                status = message.optString("message").ifBlank { null }
            }
            Msg.ERROR -> status = message.optString("message").ifBlank { "Unknown error" }
        }
    }

    private fun onSessionLost(message: String) {
        if (client == null) return
        cleanupSession()
        status = message
    }

    // ---------------------------------------------------------------- cleanup

    /** Leaves the session (both roles) and returns to the home screen. */
    fun leaveSession() {
        cleanupSession()
        status = null
    }

    private fun cleanupSession() {
        client?.close()
        client = null
        host?.stop()
        host = null
        wifi.stopDiscovery()
        wifi.removeGroup()
        wifi.clearPeers()
        activeGame = null
        gameUi = null
        players = emptyList()
        myPlayerId = 0
        isHost = false
        screen = Screen.Home
    }

    override fun onCleared() {
        cleanupSession()
        wifi.unregister()
    }
}
