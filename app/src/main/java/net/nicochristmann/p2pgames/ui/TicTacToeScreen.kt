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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.nicochristmann.p2pgames.game.TicTacToeGame
import net.nicochristmann.p2pgames.game.TttUiState

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

    val banner = when {
        state.status == TicTacToeGame.STATUS_DRAW -> "It's a draw!"
        state.status == TicTacToeGame.STATUS_WON && state.winner == myPlayerId -> "You win! 🎉"
        state.status == TicTacToeGame.STATUS_WON -> "${nameOf(state.winner)} wins!"
        !iAmPlaying -> "You're spectating — ${nameOf(state.turn)}'s turn"
        myTurn -> "Your turn"
        else -> "Waiting for ${nameOf(state.turn)}…"
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        if (maxWidth > maxHeight) {
            // Landscape: board on the left, info and buttons on the right.
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Board(
                    state = state,
                    myTurn = myTurn,
                    onCell = onCell,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                        .align(Alignment.CenterVertically),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    InfoPanel(state = state, banner = banner, nameOf = nameOf)
                    Spacer(Modifier.height(24.dp))
                    GameActionButtons(
                        isHost = isHost,
                        playing = playing,
                        onBackToLobby = onBackToLobby,
                        onLeave = onLeave,
                    )
                }
            }
        } else {
            // Portrait: info on top, board sized to the space that remains.
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                InfoPanel(state = state, banner = banner, nameOf = nameOf)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Largest square that fits between the info and the buttons.
                    Board(
                        state = state,
                        myTurn = myTurn,
                        onCell = onCell,
                        modifier = Modifier.aspectRatio(1f),
                    )
                }
                Column(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth(),
                ) {
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
}

@Composable
private fun InfoPanel(
    state: TttUiState,
    banner: String,
    nameOf: (Int) -> String,
) {
    Text("Tic-Tac-Toe", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        "${nameOf(state.xPlayerId)} (X)  vs  ${nameOf(state.oPlayerId)} (O)",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(banner, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
}

@Composable
private fun Board(
    state: TttUiState,
    myTurn: Boolean,
    onCell: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
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
                    val cellColor = if (state.winLine.contains(cell)) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(cellColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline)
                            .clickable(enabled = myTurn && symbol.isEmpty()) { onCell(cell) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            symbol,
                            fontSize = 40.sp,
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
}
