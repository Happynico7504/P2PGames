package com.nico.wifidirectgames

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import com.nico.wifidirectgames.game.HangmanGame
import com.nico.wifidirectgames.game.HangmanUiState
import com.nico.wifidirectgames.game.TicTacToeGame
import com.nico.wifidirectgames.game.TttUiState
import com.nico.wifidirectgames.game.WordBank
import com.nico.wifidirectgames.net.GameClient
import com.nico.wifidirectgames.net.GameHost
import com.nico.wifidirectgames.net.Msg
import com.nico.wifidirectgames.net.Player
import com.nico.wifidirectgames.wifi.WifiDirectController
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Which screen the app is currently showing. */
sealed interface Screen {
    data object Home : Screen
    data object HostLobby : Screen
    data object Discover : Screen
    data object ClientLobby : Screen
    data object TicTacToe : Screen
    data object Hangman : Screen
}

/**
 * Coordinates the whole session: Wi-Fi Direct group/discovery, the TCP
 * host/client, the roster, and the currently running game.
 *
 * The host is authoritative: it owns the rules engines, applies every move
 * (its own directly, clients' via socket messages) and broadcasts the
 * resulting state. Clients only render states and send inputs.
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
    var tttState by mutableStateOf<TttUiState?>(null)
        private set
    var hangmanState by mutableStateOf<HangmanUiState?>(null)
        private set

    private var host: GameHost? = null
    private var client: GameClient? = null
    private var tttGame: TicTacToeGame? = null
    private var hangmanGame: HangmanGame? = null

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
                tttGame?.let { host?.send(clientId, Msg.start(Msg.GAME_TTT, it.toJson())) }
                hangmanGame?.let { host?.send(clientId, Msg.start(Msg.GAME_HANGMAN, it.toJson())) }
                status = "$name joined."
            }
            Msg.MOVE -> {
                val game = tttGame ?: return
                if (game.move(clientId, message.optInt("cell", -1))) publishTtt()
            }
            Msg.GUESS -> {
                val game = hangmanGame ?: return
                val letter = message.optString("letter").firstOrNull() ?: return
                if (game.guess(clientId, letter)) publishHangman()
            }
        }
    }

    private fun handleClientLeft(clientId: Int) {
        val leaving = players.firstOrNull { it.id == clientId } ?: return
        players = players.filterNot { it.id == clientId }
        host?.broadcast(Msg.roster(players))
        status = "${leaving.name} left."

        tttGame?.let { game ->
            if (game.involves(clientId)) {
                returnToLobby("${leaving.name} left the game.")
                return
            }
        }
        hangmanGame?.let { game ->
            if (game.isFinished) return@let
            if (clientId == game.setterId || !game.removeGuesser(clientId)) {
                returnToLobby("${leaving.name} left the game.")
            } else {
                publishHangman()
            }
        }
    }

    fun startTicTacToe() {
        if (!isHost || players.size < 2) return
        val game = TicTacToeGame(xPlayerId = players[0].id, oPlayerId = players[1].id)
        tttGame = game
        hangmanGame = null
        hangmanState = null
        publishTtt()
        host?.broadcast(Msg.start(Msg.GAME_TTT, game.toJson()))
        screen = Screen.TicTacToe
        status = null
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
        val game = HangmanGame(word, setterId, guessers)
        hangmanGame = game
        tttGame = null
        tttState = null
        publishHangman()
        host?.broadcast(Msg.start(Msg.GAME_HANGMAN, game.toJson()))
        screen = Screen.Hangman
        status = null
    }

    /** Host: ends the current game for everyone and returns to the lobby. */
    fun returnToLobby(message: String? = null) {
        if (!isHost) return
        tttGame = null
        hangmanGame = null
        tttState = null
        hangmanState = null
        host?.broadcast(Msg.lobby(message))
        screen = Screen.HostLobby
        if (message != null) status = message
    }

    private fun publishTtt() {
        val game = tttGame ?: return
        val json = game.toJson()
        tttState = TttUiState.from(json)
        host?.broadcast(Msg.state(Msg.GAME_TTT, json))
    }

    private fun publishHangman() {
        val game = hangmanGame ?: return
        val json = game.toJson()
        hangmanState = HangmanUiState.from(json)
        host?.broadcast(Msg.state(Msg.GAME_HANGMAN, json))
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
                val state = message.getJSONObject("state")
                when (message.optString("game")) {
                    Msg.GAME_TTT -> {
                        tttState = TttUiState.from(state)
                        hangmanState = null
                        screen = Screen.TicTacToe
                    }
                    Msg.GAME_HANGMAN -> {
                        hangmanState = HangmanUiState.from(state)
                        tttState = null
                        screen = Screen.Hangman
                    }
                }
                status = null
            }
            Msg.STATE -> {
                val state = message.getJSONObject("state")
                when (message.optString("game")) {
                    Msg.GAME_TTT -> tttState = TttUiState.from(state)
                    Msg.GAME_HANGMAN -> hangmanState = HangmanUiState.from(state)
                }
            }
            Msg.LOBBY -> {
                tttState = null
                hangmanState = null
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

    // ------------------------------------------------------------------ moves

    fun playTttCell(cell: Int) {
        if (isHost) {
            val game = tttGame ?: return
            if (game.move(myPlayerId, cell)) publishTtt()
        } else {
            client?.send(Msg.move(cell))
        }
    }

    fun guessLetter(letter: Char) {
        if (isHost) {
            val game = hangmanGame ?: return
            if (game.guess(myPlayerId, letter)) publishHangman()
        } else {
            client?.send(Msg.guess(letter))
        }
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
        tttGame = null
        hangmanGame = null
        tttState = null
        hangmanState = null
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
