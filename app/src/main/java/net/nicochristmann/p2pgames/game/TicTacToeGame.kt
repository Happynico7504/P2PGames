package net.nicochristmann.p2pgames.game

import org.json.JSONArray
import org.json.JSONObject

/**
 * Host-side rules engine for Tic-Tac-Toe. The host validates every move
 * (its own and the clients') through [move] and broadcasts [toJson] after
 * each change.
 */
class TicTacToeGame(val xPlayerId: Int, val oPlayerId: Int) {

    companion object {
        const val STATUS_PLAYING = "playing"
        const val STATUS_WON = "won"
        const val STATUS_DRAW = "draw"

        private val WIN_LINES = listOf(
            intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
            intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
            intArrayOf(0, 4, 8), intArrayOf(2, 4, 6),
        )
    }

    private val board = Array(9) { "" }
    private var turn = xPlayerId
    private var status = STATUS_PLAYING
    private var winner = -1
    private var winLine: IntArray? = null

    fun involves(playerId: Int) = playerId == xPlayerId || playerId == oPlayerId

    /** Applies a move if legal; returns true when the state changed. */
    fun move(playerId: Int, cell: Int): Boolean {
        if (status != STATUS_PLAYING) return false
        if (playerId != turn) return false
        if (cell !in 0..8 || board[cell].isNotEmpty()) return false

        board[cell] = if (playerId == xPlayerId) "X" else "O"

        for (line in WIN_LINES) {
            val (a, b, c) = Triple(board[line[0]], board[line[1]], board[line[2]])
            if (a.isNotEmpty() && a == b && b == c) {
                status = STATUS_WON
                winner = playerId
                winLine = line
                return true
            }
        }
        if (board.all { it.isNotEmpty() }) {
            status = STATUS_DRAW
            return true
        }
        turn = if (turn == xPlayerId) oPlayerId else xPlayerId
        return true
    }

    fun toJson(): JSONObject = JSONObject()
        .put("board", JSONArray(board.toList()))
        .put("turn", turn)
        .put("x", xPlayerId)
        .put("o", oPlayerId)
        .put("status", status)
        .put("winner", winner)
        .put("line", JSONArray((winLine ?: intArrayOf()).toList()))
}

/** Snapshot of a Tic-Tac-Toe game as rendered by every device. */
data class TttUiState(
    val board: List<String>,
    val turn: Int,
    val xPlayerId: Int,
    val oPlayerId: Int,
    val status: String,
    val winner: Int,
    val winLine: List<Int>,
) {
    companion object {
        fun from(json: JSONObject): TttUiState {
            val boardArr = json.getJSONArray("board")
            val lineArr = json.getJSONArray("line")
            return TttUiState(
                board = (0 until boardArr.length()).map { boardArr.getString(it) },
                turn = json.getInt("turn"),
                xPlayerId = json.getInt("x"),
                oPlayerId = json.getInt("o"),
                status = json.getString("status"),
                winner = json.getInt("winner"),
                winLine = (0 until lineArr.length()).map { lineArr.getInt(it) },
            )
        }
    }
}
