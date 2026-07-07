package net.nicochristmann.p2pgames.game

import net.nicochristmann.p2pgames.net.Msg
import org.json.JSONArray
import org.json.JSONObject

/**
 * Host-side rules engine for Sea Battle (Battleship), exactly 2 players.
 *
 * Fleet: ships of length 5, 4, 3, 3, 2 on a 10×10 grid. Ships are placed
 * randomly; during the placement phase each player may reshuffle until
 * they press ready. A hit grants another shot; sinking every opponent
 * ship wins. Cells are encoded as x * 10 + y.
 */
class BattleshipGame(p0: Int, p1: Int) : HostGame {

    companion object {
        const val PHASE_PLACEMENT = "placement"
        const val PHASE_BATTLE = "battle"
        const val PHASE_OVER = "over"
        const val SIZE = 10
        val FLEET = listOf(5, 4, 3, 3, 2)

        fun shuffleInput(): JSONObject = JSONObject().put("action", "shuffle")
        fun readyInput(): JSONObject = JSONObject().put("action", "ready")
        fun fireInput(cell: Int): JSONObject =
            JSONObject().put("action", "fire").put("cell", cell)
    }

    private val playerIds = listOf(p0, p1)
    private val fleets = arrayOf(randomFleet(), randomFleet())
    private val shotsAt = arrayOf(mutableSetOf<Int>(), mutableSetOf<Int>())
    private val ready = booleanArrayOf(false, false)
    private var phase = PHASE_PLACEMENT
    private var turn = 0
    private var winner = -1
    private var lastEvent = ""

    override val gameId: String get() = Msg.GAME_BATTLESHIP
    override val isFinished: Boolean get() = phase == PHASE_OVER

    /** Random non-overlapping fleet; ships as lists of cells. */
    private fun randomFleet(): List<List<Int>> {
        while (true) {
            val taken = mutableSetOf<Int>()
            val fleet = mutableListOf<List<Int>>()
            var failed = false
            for (len in FLEET) {
                var placed = false
                var attempts = 0
                while (!placed && attempts++ < 200) {
                    val horizontal = listOf(true, false).random()
                    val x = (0 until if (horizontal) SIZE - len + 1 else SIZE).random()
                    val y = (0 until if (horizontal) SIZE else SIZE - len + 1).random()
                    val cells = (0 until len).map { i ->
                        if (horizontal) (x + i) * 10 + y else x * 10 + (y + i)
                    }
                    if (cells.none { it in taken }) {
                        taken.addAll(cells)
                        fleet.add(cells)
                        placed = true
                    }
                }
                if (!placed) {
                    failed = true
                    break
                }
            }
            if (!failed) return fleet
        }
    }

    private fun sideOf(playerId: Int): Int = playerIds.indexOf(playerId)

    override fun input(playerId: Int, data: JSONObject): Boolean {
        val me = sideOf(playerId)
        if (me == -1) return false

        when (data.optString("action")) {
            "shuffle" -> {
                if (phase != PHASE_PLACEMENT || ready[me]) return false
                fleets[me] = randomFleet()
                return true
            }
            "ready" -> {
                if (phase != PHASE_PLACEMENT || ready[me]) return false
                ready[me] = true
                if (ready[0] && ready[1]) {
                    phase = PHASE_BATTLE
                    turn = 0
                }
                return true
            }
            "fire" -> {
                if (phase != PHASE_BATTLE || me != turn) return false
                val cell = data.optInt("cell", -1)
                if (cell !in 0..99) return false
                val enemy = 1 - me
                if (cell in shotsAt[enemy]) return false
                shotsAt[enemy].add(cell)
                val hitShip = fleets[enemy].firstOrNull { cell in it }
                if (hitShip != null) {
                    val sunk = hitShip.all { it in shotsAt[enemy] }
                    lastEvent = if (sunk) "sunk" else "hit"
                    if (fleets[enemy].all { ship -> ship.all { it in shotsAt[enemy] } }) {
                        phase = PHASE_OVER
                        winner = playerIds[me]
                    }
                    // A hit grants another shot: turn stays.
                } else {
                    lastEvent = "miss"
                    turn = enemy
                }
                return true
            }
        }
        return false
    }

    override fun playerLeft(playerId: Int): Boolean = sideOf(playerId) == -1

    override fun toJsonFor(playerId: Int): JSONObject {
        val me = sideOf(playerId)
        val json = JSONObject()
            .put("p0", playerIds[0])
            .put("p1", playerIds[1])
            .put("phase", phase)
            .put("turn", if (phase == PHASE_BATTLE) playerIds[turn] else -1)
            .put("winner", winner)
            .put("event", lastEvent)
            .put("mySide", me)

        if (me != -1) {
            val enemy = 1 - me
            json.put("myShips", fleetJson(fleets[me]))
            json.put("shotsAtMe", JSONArray(shotsAt[me].toList()))
            json.put("myReady", ready[me])
            json.put("oppReady", ready[enemy])
            // My shots at the enemy, with hit info; sunk enemy ships revealed.
            val shots = JSONArray()
            shotsAt[enemy].forEach { cell ->
                val hit = fleets[enemy].any { cell in it }
                shots.put(JSONObject().put("c", cell).put("h", hit))
            }
            json.put("myShots", shots)
            val sunk = JSONArray()
            fleets[enemy].forEach { ship ->
                if (ship.all { it in shotsAt[enemy] }) sunk.put(JSONArray(ship))
            }
            json.put("sunkEnemyShips", sunk)
        } else {
            // Spectators see both shot maps, no un-hit ship positions.
            json.put("myShips", JSONArray())
            json.put("shotsAtMe", JSONArray())
            json.put("myReady", false)
            json.put("oppReady", false)
            val shots = JSONArray()
            shotsAt[0].forEach { cell ->
                val hit = fleets[0].any { cell in it }
                shots.put(JSONObject().put("c", cell).put("h", hit))
            }
            json.put("myShots", shots)
            json.put("sunkEnemyShips", JSONArray())
        }
        return json
    }

    private fun fleetJson(fleet: List<List<Int>>): JSONArray {
        val arr = JSONArray()
        fleet.forEach { ship -> arr.put(JSONArray(ship)) }
        return arr
    }
}

/** One of my shots at the enemy grid. */
data class BattleshipShot(val cell: Int, val hit: Boolean)

/** Snapshot of a Sea Battle game as seen by one device. */
data class BattleshipUiState(
    val player0: Int,
    val player1: Int,
    val phase: String,
    val turn: Int,
    val winner: Int,
    val event: String,
    val mySide: Int,
    val myShips: List<List<Int>>,
    val shotsAtMe: List<Int>,
    val myReady: Boolean,
    val oppReady: Boolean,
    val myShots: List<BattleshipShot>,
    val sunkEnemyShips: List<List<Int>>,
) : GameUi {
    companion object {
        fun from(json: JSONObject): BattleshipUiState {
            fun cellsList(arr: JSONArray): List<List<Int>> =
                (0 until arr.length()).map { i ->
                    val inner = arr.getJSONArray(i)
                    (0 until inner.length()).map { inner.getInt(it) }
                }

            val shotsArr = json.getJSONArray("myShots")
            val shotsAtMeArr = json.getJSONArray("shotsAtMe")
            return BattleshipUiState(
                player0 = json.getInt("p0"),
                player1 = json.getInt("p1"),
                phase = json.getString("phase"),
                turn = json.getInt("turn"),
                winner = json.getInt("winner"),
                event = json.optString("event"),
                mySide = json.getInt("mySide"),
                myShips = cellsList(json.getJSONArray("myShips")),
                shotsAtMe = (0 until shotsAtMeArr.length()).map { shotsAtMeArr.getInt(it) },
                myReady = json.getBoolean("myReady"),
                oppReady = json.getBoolean("oppReady"),
                myShots = (0 until shotsArr.length()).map { i ->
                    val o = shotsArr.getJSONObject(i)
                    BattleshipShot(o.getInt("c"), o.getBoolean("h"))
                },
                sunkEnemyShips = cellsList(json.getJSONArray("sunkEnemyShips")),
            )
        }
    }
}
