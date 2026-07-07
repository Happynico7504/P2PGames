package net.nicochristmann.p2pgames.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.nicochristmann.p2pgames.game.GooseGame
import net.nicochristmann.p2pgames.game.GooseUiState
import org.json.JSONObject

/** Distinct token colors for up to 8 players (by seat order). */
internal val playerColors = listOf(
    Color(0xFFD32F2F), Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFF9A825),
    Color(0xFF7B1FA2), Color(0xFF00838F), Color(0xFF5D4037), Color(0xFFE64A19),
)

@Composable
fun GooseScreen(
    state: GooseUiState?,
    myPlayerId: Int,
    isHost: Boolean,
    nameOf: (Int) -> String,
    onInput: (JSONObject) -> Unit,
    onBackToLobby: () -> Unit,
    onLeave: () -> Unit,
) {
    if (state == null) return

    val playing = state.phase == GooseGame.PHASE_PLAY
    val myTurn = playing && state.turn == myPlayerId
    val iAmPlaying = state.players.any { it.id == myPlayerId }

    val banner = when {
        state.winner == myPlayerId -> "You reached 63 — you win! 🎉"
        state.winner >= 0 -> "${nameOf(state.winner)} reached 63 and wins!"
        !iAmPlaying -> "You're spectating — ${nameOf(state.turn)}'s turn"
        myTurn -> "Your turn — roll the die!"
        else -> "Waiting for ${nameOf(state.turn)}…"
    }

    val eventText = state.event.split(",").filter { it.isNotBlank() }.joinToString(" · ") {
        when (it) {
            "goose" -> "🪿 Goose — hop again!"
            "bridge" -> "🌉 Bridge — ahead to 12"
            "inn" -> "🏨 Inn — skip 1 turn"
            "well" -> "🕳️ Well — skip 2 turns"
            "maze" -> "🌀 Maze — back to 30"
            "prison" -> "🚔 Prison — skip 2 turns"
            "death" -> "💀 Death — back to start"
            "bounce" -> "↩️ Overshot — bounced back"
            else -> it
        }
    }

    val panel: @Composable () -> Unit = {
        Text(banner, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (state.die > 0) {
            Spacer(Modifier.height(4.dp))
            Text("Die: ${dieFace(state.die)}", fontSize = 28.sp)
        }
        if (eventText.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(eventText, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
        state.players.forEachIndexed { i, p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(playerColors[i % playerColors.size], CircleShape),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    "${nameOf(p.id)} — field ${p.pos}" +
                        if (p.skips > 0) " (skips ${p.skips})" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onInput(GooseGame.rollInput()) },
            enabled = myTurn,
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
                        .weight(1.4f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    GooseBoard(state, Modifier.aspectRatio(9f / 7f))
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
                Text("Goose Race", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                GooseBoard(
                    state,
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 7f),
                )
                Spacer(Modifier.height(12.dp))
                panel()
            }
        }
    }
}

internal fun dieFace(value: Int): String =
    listOf("", "⚀", "⚁", "⚂", "⚃", "⚄", "⚅").getOrElse(value) { "?" }

/** 63 fields as a 9×7 serpentine; field 0 (start) is off-board. */
@Composable
private fun GooseBoard(state: GooseUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        for (row in 0..6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                for (col in 0..8) {
                    val indexInRow = if (row % 2 == 0) col else 8 - col
                    val field = row * 9 + indexInRow + 1
                    GooseCell(
                        field = field,
                        players = state.players.mapIndexedNotNull { i, p ->
                            if (p.pos == field) i else null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun GooseCell(field: Int, players: List<Int>, modifier: Modifier = Modifier) {
    val label = when (field) {
        in GooseGame.GEESE -> "🪿"
        GooseGame.BRIDGE -> "🌉"
        GooseGame.INN -> "🏨"
        GooseGame.WELL -> "🕳️"
        GooseGame.MAZE -> "🌀"
        GooseGame.PRISON -> "🚔"
        GooseGame.DEATH -> "💀"
        GooseGame.FIELDS -> "🏁"
        else -> null
    }
    val bg = if (label != null) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = modifier
            .padding(1.dp)
            .background(bg)
            .border(0.5.dp, MaterialTheme.colorScheme.outline),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (label != null) "$field $label" else "$field",
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
            )
            if (players.isNotEmpty()) {
                Row {
                    players.take(4).forEach { i ->
                        Box(
                            Modifier
                                .padding(horizontal = 0.5.dp)
                                .size(7.dp)
                                .background(playerColors[i % playerColors.size], CircleShape),
                        )
                    }
                }
                if (players.size > 4) {
                    Text("+${players.size - 4}", fontSize = 7.sp)
                }
            }
        }
    }
}
