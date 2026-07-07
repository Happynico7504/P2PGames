package net.nicochristmann.p2pgames.net

import org.json.JSONArray
import org.json.JSONObject

/** A player in the current session. The host is always id 0. */
data class Player(val id: Int, val name: String)

/**
 * Wire protocol: newline-delimited JSON objects over a TCP socket to the
 * group owner. Every message carries a "type" field.
 *
 * Client -> Host:  hello, input
 * Host -> Client:  welcome, roster, start, state, lobby, error
 *
 * Game inputs travel as `input` messages whose `data` payload is defined
 * by each game's rules engine (see the companion builders on the game
 * classes). Game state always flows host -> client via `start`/`state`,
 * and each client receives its own view (hidden information stays hidden).
 */
object Msg {
    const val TYPE = "type"

    const val HELLO = "hello"
    const val WELCOME = "welcome"
    const val ROSTER = "roster"
    const val START = "start"
    const val STATE = "state"
    const val LOBBY = "lobby"
    const val ERROR = "error"
    const val INPUT = "input"

    const val GAME_TTT = "tictactoe"
    const val GAME_HANGMAN = "hangman"
    const val GAME_LUDO = "ludo"
    const val GAME_UNO = "uno"
    const val GAME_GOOSE = "goose"
    const val GAME_KNIFFEL = "kniffel"
    const val GAME_BATTLESHIP = "battleship"

    fun hello(name: String): JSONObject =
        JSONObject().put(TYPE, HELLO).put("name", name)

    fun welcome(playerId: Int, players: List<Player>): JSONObject =
        JSONObject().put(TYPE, WELCOME).put("playerId", playerId).put("players", playersToJson(players))

    fun roster(players: List<Player>): JSONObject =
        JSONObject().put(TYPE, ROSTER).put("players", playersToJson(players))

    fun start(game: String, state: JSONObject): JSONObject =
        JSONObject().put(TYPE, START).put("game", game).put("state", state)

    fun state(game: String, state: JSONObject): JSONObject =
        JSONObject().put(TYPE, STATE).put("game", game).put("state", state)

    fun lobby(message: String? = null): JSONObject =
        JSONObject().put(TYPE, LOBBY).apply { if (message != null) put("message", message) }

    fun error(message: String): JSONObject =
        JSONObject().put(TYPE, ERROR).put("message", message)

    fun input(data: JSONObject): JSONObject =
        JSONObject().put(TYPE, INPUT).put("data", data)

    private fun playersToJson(players: List<Player>): JSONArray {
        val arr = JSONArray()
        players.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name)) }
        return arr
    }

    fun parsePlayers(arr: JSONArray): List<Player> =
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Player(o.getInt("id"), o.getString("name"))
        }
}
