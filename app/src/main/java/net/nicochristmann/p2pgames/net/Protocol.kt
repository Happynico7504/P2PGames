package net.nicochristmann.p2pgames.net

import org.json.JSONArray
import org.json.JSONObject

/** A player in the current session. The host is always id 0. */
data class Player(val id: Int, val name: String)

/**
 * Wire protocol: newline-delimited JSON objects over a TCP socket to the
 * group owner. Every message carries a "type" field.
 *
 * Client -> Host:  hello, move, guess
 * Host -> Client:  welcome, roster, start, state, lobby, error
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
    const val MOVE = "move"
    const val GUESS = "guess"

    const val GAME_TTT = "tictactoe"
    const val GAME_HANGMAN = "hangman"

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

    fun move(cell: Int): JSONObject =
        JSONObject().put(TYPE, MOVE).put("cell", cell)

    fun guess(letter: Char): JSONObject =
        JSONObject().put(TYPE, GUESS).put("letter", letter.toString())

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
