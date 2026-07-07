package net.nicochristmann.p2pgames.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.nicochristmann.p2pgames.game.UnoGame
import net.nicochristmann.p2pgames.game.UnoUiState
import org.json.JSONObject

private fun unoColor(c: Char?): Color = when (c) {
    'R' -> Color(0xFFD32F2F)
    'G' -> Color(0xFF388E3C)
    'B' -> Color(0xFF1976D2)
    'Y' -> Color(0xFFF9A825)
    else -> Color(0xFF37474F)
}

private fun unoLabel(card: String): String = when (UnoGame.valueOf(card)) {
    "S" -> "⊘"
    "R" -> "⇄"
    "D" -> "+2"
    "W" -> "WILD"
    "W4" -> "+4"
    else -> UnoGame.valueOf(card)
}

@Composable
fun UnoScreen(
    state: UnoUiState?,
    myPlayerId: Int,
    isHost: Boolean,
    nameOf: (Int) -> String,
    onInput: (JSONObject) -> Unit,
    onBackToLobby: () -> Unit,
    onLeave: () -> Unit,
) {
    if (state == null) return

    val playing = state.phase == UnoGame.PHASE_PLAY
    val myTurn = playing && state.turn == myPlayerId
    val iAmPlaying = state.seats.any { it.id == myPlayerId }
    val mustPlayDrawn = myTurn && state.drawnPending.isNotEmpty()

    var pendingWild by remember { mutableStateOf<String?>(null) }

    fun playable(card: String): Boolean =
        UnoGame.isWild(card) || card[0] == state.color ||
            (!UnoGame.isWild(state.top) && UnoGame.valueOf(card) == UnoGame.valueOf(state.top))

    fun tapCard(card: String) {
        if (!myTurn || !playable(card)) return
        if (mustPlayDrawn && card != state.drawnPending) return
        if (UnoGame.isWild(card)) {
            pendingWild = card
        } else {
            onInput(UnoGame.playInput(card, null))
        }
    }

    val banner = when {
        state.winner == myPlayerId -> "You emptied your hand — you win! 🎉"
        state.winner >= 0 -> "${nameOf(state.winner)} wins!"
        !iAmPlaying -> "You're spectating — ${nameOf(state.turn)}'s turn"
        mustPlayDrawn -> "You drew ${unoLabel(state.drawnPending)} — play it or pass"
        myTurn -> "Your turn"
        else -> "Waiting for ${nameOf(state.turn)}…"
    }

    val seatsRow: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.seats.forEach { seat ->
                val isTurn = seat.id == state.turn
                Column(
                    modifier = Modifier
                        .background(
                            if (isTurn) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        nameOf(seat.id),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (seat.id == myPlayerId) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text("${seat.count} 🂠", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                if (state.direction > 0) "↻" else "↺",
                fontSize = 24.sp,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }

    val table: @Composable () -> Unit = {
        Text(banner, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UnoCard(
                card = state.top,
                enabled = false,
                overrideColor = if (UnoGame.isWild(state.top)) unoColor(state.color) else null,
                big = true,
                onTap = {},
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Color: ", style = MaterialTheme.typography.bodyMedium)
                    Box(
                        Modifier
                            .size(18.dp)
                            .background(unoColor(state.color), CircleShape),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onInput(UnoGame.drawInput()) },
                    enabled = myTurn && !mustPlayDrawn,
                ) {
                    Text("Draw")
                }
                if (mustPlayDrawn) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = { onInput(UnoGame.passInput()) }) {
                        Text("Keep & pass")
                    }
                }
            }
        }
    }

    val handArea: @Composable () -> Unit = {
        if (iAmPlaying) {
            Text(
                "Your hand (${state.hand.size})",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.hand.forEach { card ->
                    val canPlay = myTurn && playable(card) &&
                        (!mustPlayDrawn || card == state.drawnPending)
                    UnoCard(
                        card = card,
                        enabled = canPlay,
                        overrideColor = null,
                        big = false,
                        onTap = { tapCard(card) },
                    )
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        if (maxWidth > maxHeight) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    table()
                    Spacer(Modifier.height(12.dp))
                    GameActionButtons(
                        isHost = isHost,
                        playing = playing,
                        onBackToLobby = onBackToLobby,
                        onLeave = onLeave,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                ) {
                    seatsRow()
                    Spacer(Modifier.height(12.dp))
                    handArea()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Wild Cards", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    seatsRow()
                    Spacer(Modifier.height(16.dp))
                    table()
                    Spacer(Modifier.height(16.dp))
                    handArea()
                    Spacer(Modifier.height(16.dp))
                    GameActionButtons(
                        isHost = isHost,
                        playing = playing,
                        onBackToLobby = onBackToLobby,
                        onLeave = onLeave,
                    )
                }
            }
        }
    }

    pendingWild?.let { wildCard ->
        AlertDialog(
            onDismissRequest = { pendingWild = null },
            title = { Text("Choose a color") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    "RGBY".forEach { c ->
                        Box(
                            Modifier
                                .size(48.dp)
                                .background(unoColor(c), CircleShape)
                                .clickable {
                                    onInput(UnoGame.playInput(wildCard, c))
                                    pendingWild = null
                                },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingWild = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun UnoCard(
    card: String,
    enabled: Boolean,
    overrideColor: Color?,
    big: Boolean,
    onTap: () -> Unit,
) {
    val baseColor = overrideColor ?: unoColor(UnoGame.colorOf(card))
    val width = if (big) 84.dp else 60.dp
    val height = if (big) 126.dp else 90.dp
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(
                if (enabled || big) baseColor else baseColor.copy(alpha = 0.45f),
                RoundedCornerShape(10.dp),
            )
            .border(
                width = if (enabled) 3.dp else 1.dp,
                color = if (enabled) Color.White else Color(0x55000000),
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(enabled = enabled, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            unoLabel(card),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = if (big) 28.sp else 20.sp,
        )
    }
}
