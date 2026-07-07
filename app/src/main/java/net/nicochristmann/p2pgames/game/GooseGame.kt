package net.nicochristmann.p2pgames.game

import net.nicochristmann.p2pgames.net.Msg
import org.json.JSONArray
import org.json.JSONObject

/**
 * Host-side rules engine for the classic Game of the Goose (63 fields).
 *
 * One die per turn. Field 63 must be hit exactly — overshoot bounces back.
 * Special fields:
 *  - Goose (5, 9, 14, 18, 23, 27, 32, 36, 41, 45, 50, 54, 59): hop the same
 *    distance again.
 *  - Bridge (6): jump ahead to 12.
 *  - Inn (19): skip one turn.
 *  - Well (31): skip two turns.
 *  - Maze (42): back to 30.
 *  - Prison (52): skip two turns.
 *  - Death (58): back to start.
 */
class GooseGame(playerIds: List<Int>) : HostGame {

    companion object {
        const val PHASE_PLAY = "play"
        const val PHASE_OVER = "over"
        const val FIELDS = 63

        val GEESE = setOf(5, 9, 14, 18, 23, 27, 32, 36, 41, 45, 50, 54, 59)
        const val BRIDGE = 6
        const val INN = 19
        const val WELL = 31
        const val MAZE = 42
        const val PRISON = 52
        const val DEATH = 58

        fun rollInput(): JSONObject = JSONObject().put("action", "roll")
    }

    private val order = playerIds.take(8).toMutableList()
    private val positions = HashMap<Int, Int>().apply { order.forEach { put(it, 0) } }
    private val skips = HashMap<Int, Int>().apply { order.forEach { put(it, 0) } }
    private var turnIndex = 0
    private var die = 0
    private var phase = PHASE_PLAY
    private var winner = -1
    private var lastEvent: String = ""

    override val gameId: String get() = Msg.GAME_GOOSE
    override val isFinished: Boolean get() = phase == PHASE_OVER

    private fun currentPlayer(): Int = order[turnIndex]

    override fun input(playerId: Int, data: JSONObject): Boolean {
        if (phase != PHASE_PLAY) return false
        if (playerId != currentPlayer()) return false
        if (data.optString("action") != "roll") return false

        die = (1..6).random()
        var pos = positions.getValue(playerId) + die
        val events = mutableListOf<String>()
        if (pos > FIELDS) {
            pos = FIELDS - (pos - FIELDS)
            events.add("bounce")
        }

        // Goose fields chain the same hop; guard against pathological loops.
        var guard = 0
        while (pos in GEESE && guard++ < 10) {
            pos += die
            if (pos > FIELDS) pos = FIELDS - (pos - FIELDS)
            events.add("goose")
        }

        when (pos) {
            BRIDGE -> { pos = 12; events.add("bridge") }
            INN -> { skips[playerId] = 1; events.add("inn") }
            WELL -> { skips[playerId] = 2; events.add("well") }
            MAZE -> { pos = 30; events.add("maze") }
            PRISON -> { skips[playerId] = 2; events.add("prison") }
            DEATH -> { pos = 0; events.add("death") }
        }

        positions[playerId] = pos
        lastEvent = events.joinToString(",")

        if (pos == FIELDS) {
            phase = PHASE_OVER
            winner = playerId
            return true
        }
        advanceTurn()
        return true
    }

    private fun advanceTurn() {
        repeat(order.size * 3) {
            turnIndex = (turnIndex + 1) % order.size
            val p = order[turnIndex]
            val s = skips.getValue(p)
            if (s > 0) {
                skips[p] = s - 1
            } else {
                return
            }
        }
    }

    override fun playerLeft(playerId: Int): Boolean {
        val idx = order.indexOf(playerId)
        if (idx == -1 || isFinished) return true
        val wasTurn = idx == turnIndex
        if (idx < turnIndex) turnIndex--
        order.removeAt(idx)
        positions.remove(playerId)
        skips.remove(playerId)
        if (order.size < 2) return false
        if (turnIndex >= order.size) turnIndex = 0
        if (wasTurn) {
            // The leaver's turn passes on; honor pending skips.
            turnIndex = (turnIndex - 1 + order.size) % order.size
            advanceTurn()
        }
        return true
    }

    override fun toJsonFor(playerId: Int): JSONObject {
        val playersJson = JSONArray()
        order.forEach { p ->
            playersJson.put(
                JSONObject()
                    .put("id", p)
                    .put("pos", positions.getValue(p))
                    .put("skips", skips.getValue(p)),
            )
        }
        return JSONObject()
            .put("players", playersJson)
            .put("turn", if (isFinished) -1 else currentPlayer())
            .put("die", die)
            .put("phase", phase)
            .put("winner", winner)
            .put("event", lastEvent)
    }
}

/** One racer on the goose board. */
data class GoosePlayer(val id: Int, val pos: Int, val skips: Int)

/** Snapshot of a Goose game as rendered by every device. */
data class GooseUiState(
    val players: List<GoosePlayer>,
    val turn: Int,
    val die: Int,
    val phase: String,
    val winner: Int,
    val event: String,
) : GameUi {
    companion object {
        fun from(json: JSONObject): GooseUiState {
            val arr = json.getJSONArray("players")
            return GooseUiState(
                players = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    GoosePlayer(o.getInt("id"), o.getInt("pos"), o.getInt("skips"))
                },
                turn = json.getInt("turn"),
                die = json.getInt("die"),
                phase = json.getString("phase"),
                winner = json.getInt("winner"),
                event = json.optString("event"),
            )
        }
    }
}
