package net.nicochristmann.p2pgames.game

import net.nicochristmann.p2pgames.net.Msg
import org.json.JSONObject

/**
 * Host-side rules engine for Hangman.
 *
 * Two modes:
 *  - Custom word: the host typed a secret word and sits out ([setterId] = 0);
 *    everyone else takes turns guessing.
 *  - Random word: nobody knows the word ([setterId] = -1) and every player,
 *    including the host, guesses.
 *
 * A correct guess keeps the turn; a wrong guess passes it on. Six wrong
 * guesses in total and the round is lost.
 */
class HangmanGame(
    private val word: String,
    val setterId: Int,
    guesserIds: List<Int>,
) : HostGame {
    companion object {
        const val STATUS_PLAYING = "playing"
        const val STATUS_WON = "won"
        const val STATUS_LOST = "lost"
        const val MAX_WRONG = 6
        const val NO_SETTER = -1

        fun guessInput(letter: Char): JSONObject =
            JSONObject().put("action", "guess").put("letter", letter.toString())
    }

    private val guessers = guesserIds.toMutableList()
    private var turnIndex = 0
    private val rightLetters = linkedSetOf<Char>()
    private val wrongLetters = linkedSetOf<Char>()
    private var status = STATUS_PLAYING
    private var winner = -1

    override val gameId: String get() = Msg.GAME_HANGMAN
    override val isFinished: Boolean get() = status != STATUS_PLAYING

    override fun input(playerId: Int, data: JSONObject): Boolean {
        if (data.optString("action") != "guess") return false
        val letter = data.optString("letter").firstOrNull() ?: return false
        return guess(playerId, letter)
    }

    override fun playerLeft(playerId: Int): Boolean {
        if (isFinished) return true
        if (playerId == setterId) return false
        return removeGuesser(playerId)
    }

    override fun toJsonFor(playerId: Int): JSONObject = toJson()

    private fun currentTurn(): Int =
        if (guessers.isEmpty()) NO_SETTER else guessers[turnIndex % guessers.size]

    /** Applies a guess if legal; returns true when the state changed. */
    fun guess(playerId: Int, rawLetter: Char): Boolean {
        if (status != STATUS_PLAYING) return false
        if (playerId != currentTurn()) return false
        val letter = rawLetter.lowercaseChar()
        if (letter !in 'a'..'z') return false
        if (letter in rightLetters || letter in wrongLetters) return false

        if (word.contains(letter)) {
            rightLetters.add(letter)
            if (word.all { it in rightLetters }) {
                status = STATUS_WON
                winner = playerId
            }
        } else {
            wrongLetters.add(letter)
            if (wrongLetters.size >= MAX_WRONG) {
                status = STATUS_LOST
            } else {
                turnIndex = (turnIndex + 1) % guessers.size
            }
        }
        return true
    }

    /**
     * Drops a player who left mid-round. Returns false when the round can no
     * longer continue (no guessers left).
     */
    fun removeGuesser(playerId: Int): Boolean {
        val index = guessers.indexOf(playerId)
        if (index >= 0) {
            if (index < turnIndex) turnIndex-- else if (turnIndex >= guessers.size - 1) turnIndex = 0
            guessers.removeAt(index)
        }
        return guessers.isNotEmpty()
    }

    fun toJson(): JSONObject {
        val masked = word.map { if (it in rightLetters || isFinished) it else '_' }
            .joinToString("")
        return JSONObject()
            .put("masked", masked)
            .put("right", rightLetters.joinToString(""))
            .put("wrong", wrongLetters.joinToString(""))
            .put("maxWrong", MAX_WRONG)
            .put("turn", currentTurn())
            .put("setter", setterId)
            .put("status", status)
            .put("winner", winner)
    }
}

/** Snapshot of a Hangman game as rendered by every device. */
data class HangmanUiState(
    val masked: String,
    val rightLetters: String,
    val wrongLetters: String,
    val maxWrong: Int,
    val turn: Int,
    val setterId: Int,
    val status: String,
    val winner: Int,
) : GameUi {
    val wrongCount: Int get() = wrongLetters.length

    fun isGuessed(letter: Char): Boolean {
        val l = letter.lowercaseChar()
        return rightLetters.contains(l) || wrongLetters.contains(l)
    }

    companion object {
        fun from(json: JSONObject): HangmanUiState = HangmanUiState(
            masked = json.getString("masked"),
            rightLetters = json.getString("right"),
            wrongLetters = json.getString("wrong"),
            maxWrong = json.getInt("maxWrong"),
            turn = json.getInt("turn"),
            setterId = json.getInt("setter"),
            status = json.getString("status"),
            winner = json.getInt("winner"),
        )
    }
}

/** Fallback words for "random word" rounds. */
object WordBank {
    private val words = listOf(
        "android", "kotlin", "compose", "network", "socket", "wireless",
        "gateway", "protocol", "package", "variable", "function", "elephant",
        "giraffe", "penguin", "dolphin", "octopus", "volcano", "glacier",
        "harbor", "lantern", "compass", "voyage", "pyramid", "orchestra",
        "galaxy", "asteroid", "gravity", "horizon", "thunder", "rainbow",
        "bicycle", "umbrella", "sandwich", "chocolate", "adventure", "treasure",
    )

    fun random(): String = words.random()
}
