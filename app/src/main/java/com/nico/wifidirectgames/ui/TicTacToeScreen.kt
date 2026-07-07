package com.nico.wifidirectgames.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nico.wifidirectgames.game.TicTacToeGame
import com.nico.wifidirectgames.game.TttUiState

@Composable
fun TicTacToeScreen(
    state: TttUiState?,
    myPlayerId: Int,
    isHost: Boolean,
    nameOf: (Int) -> String,
    onCell: (Int) -> Unit,
    onBackToLobby: () -> Unit,
    onLeave: () -> Unit,
) {
    if (state == null) return

    val iAmPlaying = myPlayerId == state.xPlayerId || myPlayerId == state.oPlayerId
    val playing = state.status == TicTacToeGame.STATUS_PLAYING
    val myTurn = playing && iAmPlaying && state.turn == myPlayerId

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Tic-Tac-Toe", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "${nameOf(state.xPlayerId)} (X)  vs  ${nameOf(state.oPlayerId)} (O)",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))

        val banner = when {
            state.status == TicTacToeGame.STATUS_DRAW -> "It's a draw!"
            state.status == TicTacToeGame.STATUS_WON && state.winner == myPlayerId -> "You win! 🎉"
            state.status == TicTacToeGame.STATUS_WON -> "${nameOf(state.winner)} wins!"
            !iAmPlaying -> "You're spectating — ${nameOf(state.turn)}'s turn"
            myTurn -> "Your turn"
            else -> "Waiting for ${nameOf(state.turn)}…"
        }
        Text(banner, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (row in 0..2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (col in 0..2) {
                        val cell = row * 3 + col
                        val symbol = state.board[cell]
                        val inWinLine = state.winLine.contains(cell)
                        val cellColor = when {
                            inWinLine -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(cellColor)
                                .border(1.dp, MaterialTheme.colorScheme.outline)
                                .clickable(enabled = myTurn && symbol.isEmpty()) { onCell(cell) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                symbol,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (symbol == "X") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        if (isHost && !playing) {
            Button(onClick = onBackToLobby, modifier = Modifier.fillMaxWidth()) {
                Text("Back to lobby")
            }
            Spacer(Modifier.height(8.dp))
        }
        if (!isHost && !playing) {
            Text(
                "Waiting for the host to return to the lobby…",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
            Text(if (isHost) "Close session" else "Leave session")
        }
    }
}
