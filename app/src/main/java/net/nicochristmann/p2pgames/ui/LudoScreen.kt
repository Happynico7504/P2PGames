package net.nicochristmann.p2pgames.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import net.nicochristmann.p2pgames.game.LudoGame
import net.nicochristmann.p2pgames.game.LudoUiState
import org.json.JSONObject

/** Seat colors: red, green, yellow, blue (start fields 0, 10, 20, 30). */
private val seatColors = listOf(
    Color(0xFFD32F2F), Color(0xFF388E3C), Color(0xFFF9A825), Color(0xFF1976D2),
)

/** The 40 track cells on an 11×11 grid, clockwise from seat 0's start. */
private val ludoPath: List<Pair<Int, Int>> = buildList {
    add(0 to 4); add(1 to 4); add(2 to 4); add(3 to 4); add(4 to 4)
    add(4 to 3); add(4 to 2); add(4 to 1); add(4 to 0)
    add(5 to 0)
    add(6 to 0); add(6 to 1); add(6 to 2); add(6 to 3)
    add(6 to 4); add(7 to 4); add(8 to 4); add(9 to 4); add(10 to 4)
    add(10 to 5)
    add(10 to 6); add(9 to 6); add(8 to 6); add(7 to 6); add(6 to 6)
    add(6 to 7); add(6 to 8); add(6 to 9); add(6 to 10)
    add(5 to 10)
    add(4 to 10); add(4 to 9); add(4 to 8); add(4 to 7)
    add(4 to 6); add(3 to 6); add(2 to 6); add(1 to 6); add(0 to 6)
    add(0 to 5)
}

/** Goal lanes per seat, from lane entry towards the center. */
private val ludoGoals = listOf(
    listOf(1 to 5, 2 to 5, 3 to 5, 4 to 5),
    listOf(5 to 1, 5 to 2, 5 to 3, 5 to 4),
    listOf(9 to 5, 8 to 5, 7 to 5, 6 to 5),
    listOf(5 to 9, 5 to 8, 5 to 7, 5 to 6),
)

/** Base (start area) slots per seat, in the four board corners. */
private val ludoBases = listOf(
    listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
    listOf(9 to 0, 10 to 0, 9 to 1, 10 to 1),
    listOf(9 to 9, 10 to 9, 9 to 10, 10 to 10),
    listOf(0 to 9, 1 to 9, 0 to 10, 1 to 10),
)

@Composable
fun LudoScreen(
    state: LudoUiState?,
    myPlayerId: Int,
    isHost: Boolean,
    nameOf: (Int) -> String,
    onInput: (JSONObject) -> Unit,
    onBackToLobby: () -> Unit,
    onLeave: () -> Unit,
) {
    if (state == null) return

    val playing = state.phase != LudoGame.PHASE_OVER
    val mySeat = state.seatOf(myPlayerId)
    val myTurn = playing && state.turn == myPlayerId

    val banner = when {
        !playing -> {
            val first = state.ranking.firstOrNull()
            if (first == myPlayerId) "You win! 🎉" else "${nameOf(first ?: -1)} wins!"
        }
        mySeat == -1 -> "You're spectating — ${nameOf(state.turn)}'s turn"
        myTurn && state.phase == LudoGame.PHASE_ROLL -> "Your turn — roll the die!"
        myTurn -> "Tap a highlighted token to move ${state.die}"
        else -> "Waiting for ${nameOf(state.turn)}…"
    }

    val eventText = when {
        state.event.startsWith("capture:") ->
            "${nameOf(state.event.removePrefix("capture:").toIntOrNull() ?: -1)} was sent back home!"
        state.event.startsWith("finished:") ->
            "${nameOf(state.event.removePrefix("finished:").toIntOrNull() ?: -1)} brought all tokens home!"
        else -> null
    }

    val panel: @Composable () -> Unit = {
        Text(banner, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (state.die > 0 && playing) {
            Spacer(Modifier.height(4.dp))
            Text("Die: ${dieFace(state.die)}", fontSize = 28.sp)
        }
        eventText?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
        state.seats.forEachIndexed { seat, pid ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(seatColors[seat], CircleShape),
                )
                Spacer(Modifier.size(6.dp))
                val rank = state.ranking.indexOf(pid)
                Text(
                    nameOf(pid) + if (rank >= 0) "  (${rank + 1}.)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (pid == myPlayerId) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        if (!playing && state.ranking.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Final ranking", style = MaterialTheme.typography.titleSmall)
            state.ranking.forEachIndexed { i, pid ->
                Text("${i + 1}. ${nameOf(pid)}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onInput(LudoGame.rollInput()) },
            enabled = myTurn && state.phase == LudoGame.PHASE_ROLL,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Roll")
        }
        Spacer(Modifier.height(8.dp))
        GameActionButtons(
            isHost = isHost,
            playing = playing,
            onBackToLobby = onBackToLobby,
            onLeave = onLeave,
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        if (maxWidth > maxHeight) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    LudoBoard(state, mySeat, myTurn, onInput)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    panel()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Ludo", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    LudoBoard(state, mySeat, myTurn, onInput)
                }
                Spacer(Modifier.height(12.dp))
                panel()
            }
        }
    }
}

@Composable
private fun LudoBoard(
    state: LudoUiState,
    mySeat: Int,
    myTurn: Boolean,
    onInput: (JSONObject) -> Unit,
) {
    BoxWithConstraints {
        val side = min(maxWidth, if (maxHeight > 0.dp) maxHeight else maxWidth)
        val cell = side / 11
        Box(Modifier.size(side)) {
            // Track cells (start fields tinted in the seat color).
            ludoPath.forEachIndexed { index, (x, y) ->
                val startSeat = if (index % 10 == 0) index / 10 else -1
                BoardCell(
                    x = x, y = y, cell = cell,
                    color = if (startSeat in 0..3 && startSeat < state.seats.size) {
                        seatColors[startSeat].copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                )
            }
            // Goal lanes and bases for the seats in play.
            for (seat in state.seats.indices) {
                ludoGoals[seat].forEach { (x, y) ->
                    BoardCell(x, y, cell, seatColors[seat].copy(alpha = 0.25f))
                }
                ludoBases[seat].forEach { (x, y) ->
                    BoardCell(x, y, cell, seatColors[seat].copy(alpha = 0.15f))
                }
            }
            // Tokens.
            for (seat in state.seats.indices) {
                state.tokens[seat].forEachIndexed { tokenIndex, pos ->
                    val (x, y) = when {
                        pos == LudoGame.BASE -> ludoBases[seat][tokenIndex]
                        pos >= LudoGame.GOAL -> ludoGoals[seat][pos - LudoGame.GOAL]
                        else -> ludoPath[(seat * 10 + pos) % 40]
                    }
                    val movable = myTurn && seat == mySeat && tokenIndex in state.movable
                    Box(
                        modifier = Modifier
                            .offset(x = cell * x, y = cell * y)
                            .size(cell)
                            .padding(cell / 10)
                            .background(seatColors[seat], CircleShape)
                            .border(
                                width = if (movable) 3.dp else 1.dp,
                                color = if (movable) Color.White else Color(0x66000000),
                                shape = CircleShape,
                            )
                            .clickable(enabled = movable) {
                                onInput(LudoGame.moveInput(tokenIndex))
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardCell(x: Int, y: Int, cell: Dp, color: Color) {
    Box(
        modifier = Modifier
            .offset(x = cell * x, y = cell * y)
            .size(cell)
            .padding(1.dp)
            .background(color, CircleShape)
            .border(0.5.dp, Color(0x33000000), CircleShape),
    )
}
