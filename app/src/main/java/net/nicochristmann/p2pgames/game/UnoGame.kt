package net.nicochristmann.p2pgames.game

import net.nicochristmann.p2pgames.net.Msg
import org.json.JSONArray
import org.json.JSONObject

/**
 * Host-side rules engine for an Uno-style card game, 2–8 players.
 *
 * Cards are encoded as strings: color letter (R/G/B/Y) + value, where value
 * is 0-9, "S" (skip), "R" (reverse) or "D" (draw two); wilds are "W" and
 * "W4". Matching: same color, same value/symbol, or any wild. After drawing,
 * the drawn card may be played immediately or kept ("pass"). First empty
 * hand wins. No "call Uno" penalty.
 */
class UnoGame(playerIds: List<Int>) : HostGame {

    companion object {
        const val PHASE_PLAY = "play"
        const val PHASE_OVER = "over"
        const val COLORS = "RGBY"

        fun playInput(card: String, chosenColor: Char?): JSONObject =
            JSONObject().put("action", "play").put("card", card)
                .apply { if (chosenColor != null) put("color", chosenColor.toString()) }

        fun drawInput(): JSONObject = JSONObject().put("action", "draw")
        fun passInput(): JSONObject = JSONObject().put("action", "pass")

        fun isWild(card: String) = card == "W" || card == "W4"
        fun colorOf(card: String): Char? = if (isWild(card)) null else card[0]
        fun valueOf(card: String): String = if (isWild(card)) card else card.substring(1)
    }

    private val order = playerIds.take(8).toMutableList()
    private val hands = HashMap<Int, MutableList<String>>()
    private val drawPile = mutableListOf<String>()
    private val discard = mutableListOf<String>()
    private var currentColor = 'R'
    private var turnIndex = 0
    private var direction = 1
    private var drawnPending: String? = null
    private var phase = PHASE_PLAY
    private var winner = -1
    private var lastEvent = ""

    init {
        val deck = mutableListOf<String>()
        for (c in COLORS) {
            deck.add("${c}0")
            for (v in listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "S", "R", "D")) {
                deck.add("$c$v")
                deck.add("$c$v")
            }
        }
        repeat(4) { deck.add("W") }
        repeat(4) { deck.add("W4") }
        deck.shuffle()
        drawPile.addAll(deck)

        order.forEach { p ->
            hands[p] = MutableList(7) { drawPile.removeAt(drawPile.size - 1) }
        }
        // Flip the starting card; re-flip until it's a plain number card.
        while (true) {
            val top = drawPile.removeAt(drawPile.size - 1)
            if (!isWild(top) && valueOf(top).length == 1 && valueOf(top)[0].isDigit()) {
                discard.add(top)
                currentColor = top[0]
                break
            }
            drawPile.add(0, top)
        }
    }

    override val gameId: String get() = Msg.GAME_UNO
    override val isFinished: Boolean get() = phase == PHASE_OVER

    private fun currentPlayer(): Int = order[turnIndex]
    private fun topCard(): String = discard.last()

    private fun playable(card: String): Boolean =
        isWild(card) || card[0] == currentColor ||
            (!isWild(topCard()) && valueOf(card) == valueOf(topCard()))

    private fun drawCard(): String? {
        if (drawPile.isEmpty()) {
            if (discard.size <= 1) return null
            val top = discard.removeAt(discard.size - 1)
            drawPile.addAll(discard.shuffled())
            discard.clear()
            discard.add(top)
        }
        return if (drawPile.isEmpty()) null else drawPile.removeAt(drawPile.size - 1)
    }

    private fun advance(extraSkip: Int = 0) {
        val n = order.size
        turnIndex = ((turnIndex + direction * (1 + extraSkip)) % n + n) % n
    }

    override fun input(playerId: Int, data: JSONObject): Boolean {
        if (phase != PHASE_PLAY) return false
        if (playerId != currentPlayer()) return false
        val hand = hands.getValue(playerId)

        when (data.optString("action")) {
            "play" -> {
                val card = data.optString("card")
                if (card.isEmpty() || card !in hand) return false
                if (drawnPending != null && card != drawnPending) return false
                if (!playable(card)) return false

                hand.remove(card)
                discard.add(card)
                drawnPending = null
                lastEvent = ""

                currentColor = if (isWild(card)) {
                    val chosen = data.optString("color").firstOrNull()
                    if (chosen == null || chosen !in COLORS) 'R' else chosen
                } else {
                    card[0]
                }

                if (hand.isEmpty()) {
                    phase = PHASE_OVER
                    winner = playerId
                    return true
                }

                when (valueOf(card)) {
                    "S" -> advance(extraSkip = 1)
                    "R" -> {
                        direction = -direction
                        if (order.size == 2) advance(extraSkip = 1) else advance()
                    }
                    "D" -> {
                        dealToNext(2)
                        advance(extraSkip = 1)
                    }
                    "W4" -> {
                        dealToNext(4)
                        advance(extraSkip = 1)
                    }
                    else -> advance()
                }
                return true
            }
            "draw" -> {
                if (drawnPending != null) return false
                val card = drawCard() ?: run {
                    lastEvent = "empty"
                    advance()
                    return true
                }
                hand.add(card)
                if (playable(card)) {
                    drawnPending = card
                } else {
                    advance()
                }
                return true
            }
            "pass" -> {
                if (drawnPending == null) return false
                drawnPending = null
                advance()
                return true
            }
        }
        return false
    }

    private fun dealToNext(count: Int) {
        val n = order.size
        val next = order[((turnIndex + direction) % n + n) % n]
        repeat(count) { drawCard()?.let { hands.getValue(next).add(it) } }
    }

    override fun playerLeft(playerId: Int): Boolean {
        val idx = order.indexOf(playerId)
        if (idx == -1 || isFinished) return true
        val wasTurn = idx == turnIndex
        // Their cards go back under the draw pile.
        hands.remove(playerId)?.let { drawPile.addAll(0, it) }
        if (idx < turnIndex) turnIndex--
        order.removeAt(idx)
        if (order.size < 2) return false
        if (turnIndex >= order.size) turnIndex = 0
        if (wasTurn) drawnPending = null
        return true
    }

    override fun toJsonFor(playerId: Int): JSONObject {
        val playersJson = JSONArray()
        order.forEach { p ->
            playersJson.put(
                JSONObject().put("id", p).put("count", hands.getValue(p).size),
            )
        }
        val myTurn = !isFinished && playerId == currentPlayer()
        return JSONObject()
            .put("players", playersJson)
            .put("hand", JSONArray(hands[playerId]?.toList() ?: emptyList<String>()))
            .put("top", topCard())
            .put("color", currentColor.toString())
            .put("turn", if (isFinished) -1 else currentPlayer())
            .put("dir", direction)
            .put("drawnPending", if (myTurn) drawnPending ?: "" else "")
            .put("phase", phase)
            .put("winner", winner)
            .put("event", lastEvent)
    }
}

/** Another player's seat as seen by the UI. */
data class UnoSeat(val id: Int, val count: Int)

/** Snapshot of an Uno game as seen by one device. */
data class UnoUiState(
    val seats: List<UnoSeat>,
    val hand: List<String>,
    val top: String,
    val color: Char,
    val turn: Int,
    val direction: Int,
    val drawnPending: String,
    val phase: String,
    val winner: Int,
    val event: String,
) : GameUi {
    companion object {
        fun from(json: JSONObject): UnoUiState {
            val seatsArr = json.getJSONArray("players")
            val handArr = json.getJSONArray("hand")
            return UnoUiState(
                seats = (0 until seatsArr.length()).map { i ->
                    val o = seatsArr.getJSONObject(i)
                    UnoSeat(o.getInt("id"), o.getInt("count"))
                },
                hand = (0 until handArr.length()).map { handArr.getString(it) },
                top = json.getString("top"),
                color = json.getString("color").first(),
                turn = json.getInt("turn"),
                direction = json.getInt("dir"),
                drawnPending = json.optString("drawnPending"),
                phase = json.getString("phase"),
                winner = json.getInt("winner"),
                event = json.optString("event"),
            )
        }
    }
}
