package net.nicochristmann.p2pgames.game

import net.nicochristmann.p2pgames.net.Msg
import org.json.JSONObject

/**
 * A rules engine run by the session host. The host applies every input
 * (its own and the clients') through [input] and after each change sends
 * every device its own view via [toJsonFor] — which is what allows games
 * with hidden information (card hands, ship positions) to work.
 */
interface HostGame {
    val gameId: String
    val isFinished: Boolean

    /** Applies a player input; returns true when the state changed. */
    fun input(playerId: Int, data: JSONObject): Boolean

    /**
     * Handles a player leaving mid-game. Returns false when the game
     * cannot meaningfully continue and should be aborted.
     */
    fun playerLeft(playerId: Int): Boolean

    /** The state as seen by [playerId]; non-participants get a spectator view. */
    fun toJsonFor(playerId: Int): JSONObject
}

/** Marker for the parsed, renderable state of any game. */
sealed interface GameUi

/** Turns a (gameId, state) wire pair into the matching [GameUi]. */
object GameUiParser {
    fun parse(gameId: String, state: JSONObject): GameUi? = when (gameId) {
        Msg.GAME_TTT -> TttUiState.from(state)
        Msg.GAME_HANGMAN -> HangmanUiState.from(state)
        Msg.GAME_LUDO -> LudoUiState.from(state)
        Msg.GAME_UNO -> UnoUiState.from(state)
        Msg.GAME_GOOSE -> GooseUiState.from(state)
        Msg.GAME_KNIFFEL -> KniffelUiState.from(state)
        Msg.GAME_BATTLESHIP -> BattleshipUiState.from(state)
        else -> null
    }
}
