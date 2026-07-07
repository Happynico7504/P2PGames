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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.nicochristmann.p2pgames.game.BattleshipGame
import net.nicochristmann.p2pgames.game.BattleshipUiState
import org.json.JSONObject

@Composable
fun BattleshipScreen(
    state: BattleshipUiState?,
    myPlayerId: Int,
    isHost: Boolean,
    nameOf: (Int) -> String,
    onInput: (JSONObject) -> Unit,
    onBackToLobby: () -> Unit,
    onLeave: () -> Unit,
) {
    if (state == null) return

    val iAmPlaying = state.mySide != -1
    val placement = state.phase == BattleshipGame.PHASE_PLACEMENT
    val battle = state.phase == BattleshipGame.PHASE_BATTLE
    val over = state.phase == BattleshipGame.PHASE_OVER
    val myTurn = battle && state.turn == myPlayerId

    val banner = when {
        over && state.winner == myPlayerId -> "Fleet destroyed — you win! 🎉"
        over -> "${nameOf(state.winner)} sank the whole fleet!"
        !iAmPlaying -> "You're spectating this battle"
        placement && !state.myReady -> "Place your fleet (shuffle until you like it)"
        placement -> if (state.oppReady) "Both ready…" else "Waiting for the opponent's fleet…"
        myTurn -> when (state.event) {
            "hit" -> "Hit — shoot again!"
            "sunk" -> "Ship sunk — shoot again!"
            else -> "Your turn — fire at the enemy grid"
        }
        else -> "Waiting for ${nameOf(state.turn)} to fire…"
    }

    val enemyGrid: @Composable (Modifier) -> Unit = { m ->
        val shotMap = state.myShots.associate { it.cell to it.hit }
        val sunkCells = state.sunkEnemyShips.flatten().toSet()
        BattleGrid(
            modifier = m,
            cellContent = { cell ->
                when {
                    cell in sunkCells -> CellStyle(Color(0xFF8B0000), "✕")
                    shotMap[cell] == true -> CellStyle(Color(0xFFD32F2F), "✕")
                    shotMap[cell] == false -> CellStyle(Color(0xFF90CAF9), "·")
                    else -> CellStyle(Color(0xFFE3F2FD), "")
                }
            },
            onCell = { cell ->
                if (myTurn && cell !in shotMap) onInput(BattleshipGame.fireInput(cell))
            },
        )
    }

    val myGrid: @Composable (Modifier) -> Unit = { m ->
        val shipCells = state.myShips.flatten().toSet()
        val shotsAtMe = state.shotsAtMe.toSet()
        BattleGrid(
            modifier = m,
            cellContent = { cell ->
                val ship = cell in shipCells
                val shot = cell in shotsAtMe
                when {
                    ship && shot -> CellStyle(Color(0xFFD32F2F), "✕")
                    ship -> CellStyle(Color(0xFF546E7A), "")
                    shot -> CellStyle(Color(0xFF90CAF9), "·")
                    else -> CellStyle(Color(0xFFE3F2FD), "")
                }
            },
            onCell = {},
        )
    }

    val controls: @Composable () -> Unit = {
        Text(banner, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        if (placement && iAmPlaying && !state.myReady) {
            Button(
                onClick = { onInput(BattleshipGame.readyInput()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ready")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onInput(BattleshipGame.shuffleInput()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Shuffle fleet")
            }
            Spacer(Modifier.height(8.dp))
        }
        GameActionButtons(
            isHost = isHost,
            playing = !over,
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
                Column(
                    modifier = Modifier
                        .weight(1.4f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (placement || !iAmPlaying) {
                        Text("Your fleet", style = MaterialTheme.typography.titleSmall)
                        myGrid(
                            Modifier
                                .fillMaxWidth(0.9f)
                                .aspectRatio(1f),
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("Enemy waters", style = MaterialTheme.typography.titleSmall)
                                enemyGrid(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("Your fleet", style = MaterialTheme.typography.titleSmall)
                                myGrid(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                )
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    controls()
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
                        .widthIn(max = 480.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Sea Battle", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    if (placement || !iAmPlaying) {
                        Text("Your fleet", style = MaterialTheme.typography.titleSmall)
                        myGrid(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        )
                    } else {
                        Text("Enemy waters — tap to fire", style = MaterialTheme.typography.titleSmall)
                        enemyGrid(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Your fleet", style = MaterialTheme.typography.titleSmall)
                        myGrid(
                            Modifier
                                .fillMaxWidth(0.6f)
                                .aspectRatio(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    controls()
                }
            }
        }
    }
}

private data class CellStyle(val color: Color, val label: String)

@Composable
private fun BattleGrid(
    modifier: Modifier,
    cellContent: (Int) -> CellStyle,
    onCell: (Int) -> Unit,
) {
    Column(modifier = modifier) {
        for (y in 0..9) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                for (x in 0..9) {
                    val cell = x * 10 + y
                    val style = cellContent(cell)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(0.5.dp)
                            .background(style.color)
                            .border(0.5.dp, Color(0x33000000))
                            .clickable { onCell(cell) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (style.label.isNotEmpty()) {
                            Text(style.label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
