package net.nicochristmann.p2pgames.game

import net.nicochristmann.p2pgames.net.Msg
import org.json.JSONArray
import org.json.JSONObject

/**
 * Host-side rules engine for Ludo ("Mensch ärgere dich nicht"), 2–4 players.
 *
 * - 40-field track; each player has 4 tokens, a start field at seat*10 and a
 *   4-field goal lane reached after a full lap.
 * - A 6 brings a token in from the base (or moves) and grants an extra roll.
 * - When no move is possible and no token is in play, the player gets up to
 *   three attempts to roll a 6.
 * - Landing on an opponent's token sends it back to its base; a field
 *   occupied by one's own token cannot be entered.
 * - A player who brings all 4 tokens home takes the next place on the podium
 *   and drops out of the rotation; the game ends when only one player is
 *   left, producing a full final ranking.
 *
 * Token encoding: -1 = in base, 0..39 = steps travelled on the track,
 * 100+g = goal slot g (0..3).
 */
class LudoGame(playerIds: List<Int>) : HostGame {

    companion object {
        const val PHASE_ROLL = "roll"
        const val PHASE_MOVE = "move"
        const val PHASE_OVER = "over"
        const val BASE = -1
        const val GOAL = 100

        fun rollInput(): JSONObject = JSONObject().put("action", "roll")
        fun moveInput(token: Int): JSONObject =
            JSONObject().put("action", "move").put("token", token)
    }

    private val seats = playerIds.take(4)
    private val tokens = Array(seats.size) { IntArray(4) { BASE } }
    private var turnSeat = 0
    private var die = 0
    private var phase = PHASE_ROLL
    private var triesLeft = 3
    private var movable: List<Int> = emptyList()
    private val finishedSeats = mutableListOf<Int>()
    private var lastEvent: String? = null

    override val gameId: String get() = Msg.GAME_LUDO
    override val isFinished: Boolean get() = phase == PHASE_OVER

    private fun startCell(seat: Int) = seat * 10
    private fun absCell(seat: Int, steps: Int) = (startCell(seat) + steps) % 40

    /** The seat (and token index) occupying an absolute track cell, if any. */
    private fun occupantOfCell(cell: Int): Pair<Int, Int>? {
        for (s in seats.indices) {
            for (t in 0..3) {
                val p = tokens[s][t]
                if (p in 0..39 && absCell(s, p) == cell) return s to t
            }
        }
        return null
    }

    private fun movableTokens(seat: Int, die: Int): List<Int> {
        val result = mutableListOf<Int>()
        for (t in 0..3) {
            when (val p = tokens[seat][t]) {
                BASE -> {
                    if (die == 6) {
                        val occ = occupantOfCell(startCell(seat))
                        if (occ == null || occ.first != seat) result.add(t)
                    }
                }
                in 0..39 -> {
                    val target = p + die
                    if (target <= 39) {
                        val occ = occupantOfCell(absCell(seat, target))
                        if (occ == null || occ.first != seat) result.add(t)
                    } else {
                        val g = target - 40
                        if (g in 0..3 && tokens[seat].none { it == GOAL + g }) result.add(t)
                    }
                }
                else -> {
                    // Move within the goal lane if the target slot is free.
                    val g = (p - GOAL) + die
                    if (g in 0..3 && tokens[seat].none { it == GOAL + g }) result.add(t)
                }
            }
        }
        return result
    }

    private fun beginTurn(seat: Int) {
        turnSeat = seat
        phase = PHASE_ROLL
        die = 0
        movable = emptyList()
        // Three attempts to roll a 6 when nothing is on the track.
        triesLeft = if (tokens[seat].all { it == BASE || it >= GOAL }) 3 else 1
    }

    private fun nextTurn() {
        var next = turnSeat
        repeat(seats.size) {
            next = (next + 1) % seats.size
            if (next !in finishedSeats) {
                beginTurn(next)
                return
            }
        }
    }

    override fun input(playerId: Int, data: JSONObject): Boolean {
        if (phase == PHASE_OVER) return false
        val seat = seats.indexOf(playerId)
        if (seat == -1 || seat != turnSeat) return false

        when (data.optString("action")) {
            "roll" -> {
                if (phase != PHASE_ROLL) return false
                die = (1..6).random()
                lastEvent = null
                movable = movableTokens(seat, die)
                if (movable.isEmpty()) {
                    triesLeft--
                    if (triesLeft <= 0) nextTurn()
                } else {
                    phase = PHASE_MOVE
                }
                return true
            }
            "move" -> {
                if (phase != PHASE_MOVE) return false
                val t = data.optInt("token", -1)
                if (t !in movable) return false
                applyMove(seat, t)
                return true
            }
        }
        return false
    }

    private fun applyMove(seat: Int, token: Int) {
        var captured: Pair<Int, Int>? = null
        when (val p = tokens[seat][token]) {
            BASE -> {
                captured = occupantOfCell(startCell(seat))
                tokens[seat][token] = 0
            }
            in 0..39 -> {
                val target = p + die
                if (target <= 39) {
                    captured = occupantOfCell(absCell(seat, target))
                    tokens[seat][token] = target
                } else {
                    tokens[seat][token] = GOAL + (target - 40)
                }
            }
            else -> tokens[seat][token] = GOAL + (p - GOAL) + die
        }
        captured?.let { (cs, ct) ->
            if (cs != seat) {
                tokens[cs][ct] = BASE
                lastEvent = "capture:${seats[cs]}"
            }
        }
        movable = emptyList()

        if (tokens[seat].all { it >= GOAL }) {
            finishedSeats.add(seat)
            lastEvent = "finished:${seats[seat]}"
            val remaining = seats.indices.filter { it !in finishedSeats }
            if (remaining.size <= 1) {
                remaining.forEach { finishedSeats.add(it) }
                phase = PHASE_OVER
                return
            }
            nextTurn()
            return
        }

        if (die == 6) {
            phase = PHASE_ROLL
            triesLeft = 1
        } else {
            nextTurn()
        }
    }

    override fun playerLeft(playerId: Int): Boolean {
        val seat = seats.indexOf(playerId)
        if (seat == -1 || isFinished) return true
        if (seat in finishedSeats) return true
        // Remove the leaver's tokens and treat them as finishing last-ish:
        // with only one active player left afterwards, the game ends.
        for (t in 0..3) tokens[seat][t] = BASE
        finishedSeats.add(seat)
        val remaining = seats.indices.filter { it !in finishedSeats }
        if (remaining.size <= 1) {
            remaining.forEach { finishedSeats.add(it) }
            phase = PHASE_OVER
            return true
        }
        if (turnSeat == seat) nextTurn()
        return true
    }

    override fun toJsonFor(playerId: Int): JSONObject {
        val tokensJson = JSONArray()
        tokens.forEach { arr -> tokensJson.put(JSONArray(arr.toList())) }
        return JSONObject()
            .put("seats", JSONArray(seats))
            .put("tokens", tokensJson)
            .put("turn", if (isFinished) -1 else seats[turnSeat])
            .put("die", die)
            .put("phase", phase)
            .put("movable", JSONArray(movable))
            .put("ranking", JSONArray(finishedSeats.map { seats[it] }))
            .put("event", lastEvent ?: "")
    }
}

/** Snapshot of a Ludo game as rendered by every device. */
data class LudoUiState(
    val seats: List<Int>,
    val tokens: List<List<Int>>,
    val turn: Int,
    val die: Int,
    val phase: String,
    val movable: List<Int>,
    val ranking: List<Int>,
    val event: String,
) : GameUi {
    fun seatOf(playerId: Int): Int = seats.indexOf(playerId)

    companion object {
        fun from(json: JSONObject): LudoUiState {
            val seatsArr = json.getJSONArray("seats")
            val tokensArr = json.getJSONArray("tokens")
            val movableArr = json.getJSONArray("movable")
            val rankingArr = json.getJSONArray("ranking")
            return LudoUiState(
                seats = (0 until seatsArr.length()).map { seatsArr.getInt(it) },
                tokens = (0 until tokensArr.length()).map { s ->
                    val inner = tokensArr.getJSONArray(s)
                    (0 until inner.length()).map { inner.getInt(it) }
                },
                turn = json.getInt("turn"),
                die = json.getInt("die"),
                phase = json.getString("phase"),
                movable = (0 until movableArr.length()).map { movableArr.getInt(it) },
                ranking = (0 until rankingArr.length()).map { rankingArr.getInt(it) },
                event = json.optString("event"),
            )
        }
    }
}
