package net.nicochristmann.p2pgames.game

import net.nicochristmann.p2pgames.net.Msg
import org.json.JSONArray
import org.json.JSONObject

/** The 13 Kniffel categories in scorecard order. */
object KniffelCategories {
    const val ONES = "ones"
    const val TWOS = "twos"
    const val THREES = "threes"
    const val FOURS = "fours"
    const val FIVES = "fives"
    const val SIXES = "sixes"
    const val THREE_KIND = "threeKind"
    const val FOUR_KIND = "fourKind"
    const val FULL_HOUSE = "fullHouse"
    const val SMALL_STRAIGHT = "smallStraight"
    const val LARGE_STRAIGHT = "largeStraight"
    const val KNIFFEL = "kniffel"
    const val CHANCE = "chance"

    val UPPER = listOf(ONES, TWOS, THREES, FOURS, FIVES, SIXES)
    val LOWER = listOf(
        THREE_KIND, FOUR_KIND, FULL_HOUSE,
        SMALL_STRAIGHT, LARGE_STRAIGHT, KNIFFEL, CHANCE,
    )
    val ALL = UPPER + LOWER

    fun label(category: String): String = when (category) {
        ONES -> "Ones"
        TWOS -> "Twos"
        THREES -> "Threes"
        FOURS -> "Fours"
        FIVES -> "Fives"
        SIXES -> "Sixes"
        THREE_KIND -> "Three of a kind"
        FOUR_KIND -> "Four of a kind"
        FULL_HOUSE -> "Full house"
        SMALL_STRAIGHT -> "Small straight"
        LARGE_STRAIGHT -> "Large straight"
        KNIFFEL -> "Five Dice!"
        CHANCE -> "Chance"
        else -> category
    }
}

/** Scoring rules, shared by the host engine and the score preview in the UI. */
object KniffelScoring {
    fun score(category: String, dice: List<Int>): Int {
        val counts = IntArray(7)
        dice.forEach { counts[it]++ }
        val sum = dice.sum()
        return when (category) {
            KniffelCategories.ONES -> counts[1] * 1
            KniffelCategories.TWOS -> counts[2] * 2
            KniffelCategories.THREES -> counts[3] * 3
            KniffelCategories.FOURS -> counts[4] * 4
            KniffelCategories.FIVES -> counts[5] * 5
            KniffelCategories.SIXES -> counts[6] * 6
            KniffelCategories.THREE_KIND -> if (counts.any { it >= 3 }) sum else 0
            KniffelCategories.FOUR_KIND -> if (counts.any { it >= 4 }) sum else 0
            KniffelCategories.FULL_HOUSE ->
                if (counts.contains(3) && counts.contains(2)) 25 else 0
            KniffelCategories.SMALL_STRAIGHT -> if (hasRun(counts, 4)) 30 else 0
            KniffelCategories.LARGE_STRAIGHT -> if (hasRun(counts, 5)) 40 else 0
            KniffelCategories.KNIFFEL -> if (counts.any { it >= 5 }) 50 else 0
            KniffelCategories.CHANCE -> sum
            else -> 0
        }
    }

    private fun hasRun(counts: IntArray, length: Int): Boolean {
        var run = 0
        for (v in 1..6) {
            if (counts[v] > 0) {
                run++
                if (run >= length) return true
            } else {
                run = 0
            }
        }
        return false
    }

    /** Upper-section sum, bonus (35 from 63) and grand total for a card. */
    fun totals(card: Map<String, Int>): Triple<Int, Int, Int> {
        val upper = KniffelCategories.UPPER.sumOf { card[it] ?: 0 }
        val bonus = if (upper >= 63) 35 else 0
        val total = upper + bonus + KniffelCategories.LOWER.sumOf { card[it] ?: 0 }
        return Triple(upper, bonus, total)
    }
}

/**
 * Host-side rules engine for Kniffel (Yahtzee-style). Each turn: up to three
 * rolls with holds in between, then one category must be scored. The game
 * ends when every player has filled all 13 categories.
 */
class KniffelGame(playerIds: List<Int>) : HostGame {

    companion object {
        const val PHASE_PLAY = "play"
        const val PHASE_OVER = "over"

        fun rollInput(): JSONObject = JSONObject().put("action", "roll")
        fun holdInput(index: Int, held: Boolean): JSONObject =
            JSONObject().put("action", "hold").put("index", index).put("held", held)
        fun scoreInput(category: String): JSONObject =
            JSONObject().put("action", "score").put("category", category)
    }

    private val order = playerIds.take(8).toMutableList()
    private val cards = HashMap<Int, MutableMap<String, Int>>().apply {
        order.forEach { put(it, mutableMapOf()) }
    }
    private var turnIndex = 0
    private val dice = IntArray(5)
    private val held = BooleanArray(5)
    private var rollsUsed = 0
    private var phase = PHASE_PLAY

    override val gameId: String get() = Msg.GAME_KNIFFEL
    override val isFinished: Boolean get() = phase == PHASE_OVER

    private fun currentPlayer(): Int = order[turnIndex]

    override fun input(playerId: Int, data: JSONObject): Boolean {
        if (phase != PHASE_PLAY) return false
        if (playerId != currentPlayer()) return false

        when (data.optString("action")) {
            "roll" -> {
                if (rollsUsed >= 3) return false
                for (i in 0..4) {
                    if (rollsUsed == 0 || !held[i]) dice[i] = (1..6).random()
                }
                rollsUsed++
                return true
            }
            "hold" -> {
                if (rollsUsed == 0 || rollsUsed >= 3) return false
                val i = data.optInt("index", -1)
                if (i !in 0..4) return false
                held[i] = data.optBoolean("held", !held[i])
                return true
            }
            "score" -> {
                if (rollsUsed == 0) return false
                val category = data.optString("category")
                if (category !in KniffelCategories.ALL) return false
                val card = cards.getValue(playerId)
                if (card.containsKey(category)) return false
                card[category] = KniffelScoring.score(category, dice.toList())
                endTurn()
                return true
            }
        }
        return false
    }

    private fun endTurn() {
        rollsUsed = 0
        for (i in 0..4) {
            dice[i] = 0
            held[i] = false
        }
        if (order.all { cards.getValue(it).size >= KniffelCategories.ALL.size }) {
            phase = PHASE_OVER
            return
        }
        // Advance to the next player who still has open categories.
        repeat(order.size) {
            turnIndex = (turnIndex + 1) % order.size
            if (cards.getValue(currentPlayer()).size < KniffelCategories.ALL.size) return
        }
    }

    override fun playerLeft(playerId: Int): Boolean {
        val idx = order.indexOf(playerId)
        if (idx == -1 || isFinished) return true
        val wasTurn = idx == turnIndex
        if (idx < turnIndex) turnIndex--
        order.removeAt(idx)
        cards.remove(playerId)
        if (order.size < 2) return false
        if (turnIndex >= order.size) turnIndex = 0
        if (wasTurn) {
            rollsUsed = 0
            for (i in 0..4) {
                dice[i] = 0
                held[i] = false
            }
        }
        return true
    }

    override fun toJsonFor(playerId: Int): JSONObject {
        val playersJson = JSONArray()
        order.forEach { p ->
            val cardJson = JSONObject()
            cards.getValue(p).forEach { (cat, value) -> cardJson.put(cat, value) }
            playersJson.put(JSONObject().put("id", p).put("card", cardJson))
        }
        return JSONObject()
            .put("players", playersJson)
            .put("turn", if (isFinished) -1 else currentPlayer())
            .put("dice", JSONArray(dice.toList()))
            .put("held", JSONArray(held.toList()))
            .put("rollsUsed", rollsUsed)
            .put("phase", phase)
    }
}

/** One player's scorecard as seen by the UI. */
data class KniffelPlayer(val id: Int, val card: Map<String, Int>)

/** Snapshot of a Kniffel game as rendered by every device. */
data class KniffelUiState(
    val players: List<KniffelPlayer>,
    val turn: Int,
    val dice: List<Int>,
    val held: List<Boolean>,
    val rollsUsed: Int,
    val phase: String,
) : GameUi {
    companion object {
        fun from(json: JSONObject): KniffelUiState {
            val playersArr = json.getJSONArray("players")
            val diceArr = json.getJSONArray("dice")
            val heldArr = json.getJSONArray("held")
            return KniffelUiState(
                players = (0 until playersArr.length()).map { i ->
                    val o = playersArr.getJSONObject(i)
                    val cardJson = o.getJSONObject("card")
                    val card = mutableMapOf<String, Int>()
                    cardJson.keys().forEach { key -> card[key] = cardJson.getInt(key) }
                    KniffelPlayer(o.getInt("id"), card)
                },
                turn = json.getInt("turn"),
                dice = (0 until diceArr.length()).map { diceArr.getInt(it) },
                held = (0 until heldArr.length()).map { heldArr.getBoolean(it) },
                rollsUsed = json.getInt("rollsUsed"),
                phase = json.getString("phase"),
            )
        }
    }
}
