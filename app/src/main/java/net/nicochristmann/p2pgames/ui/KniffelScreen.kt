package net.nicochristmann.p2pgames.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.nicochristmann.p2pgames.game.KniffelCategories
import net.nicochristmann.p2pgames.game.KniffelGame
import net.nicochristmann.p2pgames.game.KniffelScoring
import net.nicochristmann.p2pgames.game.KniffelUiState
import org.json.JSONObject

@Composable
fun KniffelScreen(
    state: KniffelUiState?,
    myPlayerId: Int,
    isHost: Boolean,
    nameOf: (Int) -> String,
    onInput: (JSONObject) -> Unit,
    onBackToLobby: () -> Unit,
    onLeave: () -> Unit,
) {
    if (state == null) return

    val playing = state.phase == KniffelGame.PHASE_PLAY
    val myTurn = playing && state.turn == myPlayerId
    val iAmPlaying = state.players.any { it.id == myPlayerId }
    val rolled = state.rollsUsed > 0

    val standings = state.players
        .map { it.id to KniffelScoring.totals(it.card).third }
        .sortedByDescending { it.second }

    val banner = when {
        !playing -> {
            val (winId, winTotal) = standings.first()
            if (winId == myPlayerId) "You win with $winTotal points! 🎉"
            else "${nameOf(winId)} wins with $winTotal points!"
        }
        !iAmPlaying -> "You're spectating — ${nameOf(state.turn)}'s turn"
        myTurn && !rolled -> "Your turn — roll the dice!"
        myTurn -> "Hold dice, re-roll, or pick a category"
        else -> "Waiting for ${nameOf(state.turn)}…"
    }

    val dicePanel: @Composable () -> Unit = {
        Text(banner, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.dice.forEachIndexed { i, value ->
                val held = state.held.getOrElse(i) { false }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .border(
                            width = if (held) 3.dp else 1.dp,
                            color = if (held) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable(enabled = myTurn && rolled && state.rollsUsed < 3) {
                            onInput(KniffelGame.holdInput(i, !held))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (value > 0) dieFace(value) else "·", fontSize = 30.sp)
                }
            }
        }
        if (myTurn && rolled && state.rollsUsed < 3) {
            Spacer(Modifier.height(4.dp))
            Text("Tap dice to hold them", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onInput(KniffelGame.rollInput()) },
            enabled = myTurn && state.rollsUsed < 3,
            modifier = Modifier.widthIn(min = 180.dp),
        ) {
            Text(if (!rolled) "Roll" else "Re-roll (${3 - state.rollsUsed} left)")
        }
        Spacer(Modifier.height(12.dp))
        standings.forEach { (id, total) ->
            Text(
                "${nameOf(id)}: $total points" + if (id == state.turn) "  ← turn" else "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (id == myPlayerId) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Spacer(Modifier.height(12.dp))
        GameActionButtons(
            isHost = isHost,
            playing = playing,
            onBackToLobby = onBackToLobby,
            onLeave = onLeave,
        )
    }

    val scorecard: @Composable () -> Unit = {
        val myCard = state.players.firstOrNull { it.id == myPlayerId }?.card
        if (myCard != null) {
            Text("Your scorecard", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            val canScore = myTurn && rolled
            KniffelCategories.ALL.forEach { cat ->
                val taken = myCard[cat]
                val potential = KniffelScoring.score(cat, state.dice)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canScore && taken == null) {
                            onInput(KniffelGame.scoreInput(cat))
                        }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        KniffelCategories.label(cat),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    when {
                        taken != null -> Text(
                            "$taken",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        canScore -> Text(
                            "take $potential",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        else -> Text(
                            "—",
                            color = MaterialTheme.colorScheme.outline,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (cat == KniffelCategories.SIXES) {
                    val (upper, bonus, _) = KniffelScoring.totals(myCard)
                    HorizontalDivider()
                    Text(
                        "Upper: $upper / 63 — bonus: $bonus",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    HorizontalDivider()
                }
            }
            HorizontalDivider()
            val (_, _, total) = KniffelScoring.totals(myCard)
            Text(
                "Total: $total",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        } else {
            Text(
                "You're spectating this round.",
                style = MaterialTheme.typography.bodyMedium,
            )
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
                    dicePanel()
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    scorecard()
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
                    Text("Kniffel", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    dicePanel()
                    Spacer(Modifier.height(16.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        scorecard()
                    }
                }
            }
        }
    }
}
