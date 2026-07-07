package net.nicochristmann.p2pgames.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.nicochristmann.p2pgames.game.HangmanGame
import net.nicochristmann.p2pgames.game.HangmanUiState

@Composable
fun HangmanScreen(
    state: HangmanUiState?,
    myPlayerId: Int,
    isHost: Boolean,
    nameOf: (Int) -> String,
    onGuess: (Char) -> Unit,
    onBackToLobby: () -> Unit,
    onLeave: () -> Unit,
) {
    if (state == null) return

    val playing = state.status == HangmanGame.STATUS_PLAYING
    val iAmSetter = state.setterId == myPlayerId
    val myTurn = playing && !iAmSetter && state.turn == myPlayerId

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Hangman", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        val banner = when {
            state.status == HangmanGame.STATUS_WON && state.winner == myPlayerId ->
                "You guessed it! 🎉"
            state.status == HangmanGame.STATUS_WON ->
                "${nameOf(state.winner)} guessed the word!"
            state.status == HangmanGame.STATUS_LOST ->
                "Out of guesses — the word wins this time."
            iAmSetter -> "Your word — ${nameOf(state.turn)} is guessing"
            myTurn -> "Your turn — pick a letter"
            else -> "Waiting for ${nameOf(state.turn)}…"
        }
        Text(banner, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))

        Gallows(wrongCount = state.wrongCount)
        Spacer(Modifier.height(16.dp))

        // The masked word, e.g. "_ a _ a _ y"
        Text(
            state.masked.toCharArray().joinToString(" ") { it.uppercase() },
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Wrong guesses (${state.wrongCount}/${state.maxWrong}): " +
                state.wrongLetters.toCharArray().joinToString(" ") { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))

        LetterKeyboard(
            enabled = myTurn,
            isGuessed = state::isGuessed,
            onGuess = onGuess,
        )

        Spacer(Modifier.height(16.dp))
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

@Composable
private fun LetterKeyboard(
    enabled: Boolean,
    isGuessed: (Char) -> Boolean,
    onGuess: (Char) -> Unit,
) {
    val rows = ('A'..'Z').chunked(7)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { letter ->
                    OutlinedButton(
                        onClick = { onGuess(letter) },
                        enabled = enabled && !isGuessed(letter),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                    ) {
                        Text(letter.toString())
                    }
                }
                // Keep the last (short) row's buttons the same width.
                repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Classic gallows sketch that grows one body part per wrong guess (max 6). */
@Composable
private fun Gallows(wrongCount: Int) {
    val strokeColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(180.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 8f, cap = StrokeCap.Round)

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(
                color = strokeColor,
                start = Offset(x1 * w, y1 * h),
                end = Offset(x2 * w, y2 * h),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }

        // Gallows structure (always visible)
        line(0.1f, 0.95f, 0.6f, 0.95f)   // base
        line(0.25f, 0.95f, 0.25f, 0.08f) // pole
        line(0.25f, 0.08f, 0.7f, 0.08f)  // beam
        line(0.7f, 0.08f, 0.7f, 0.2f)    // rope

        val headRadius = 0.09f * w
        val headCenter = Offset(0.7f * w, 0.2f * h + headRadius)

        if (wrongCount >= 1) { // head
            drawCircle(
                color = strokeColor,
                radius = headRadius,
                center = headCenter,
                style = Stroke(width = stroke.width),
            )
        }
        val bodyTop = (headCenter.y + headRadius) / h
        val bodyBottom = bodyTop + 0.22f
        if (wrongCount >= 2) line(0.7f, bodyTop, 0.7f, bodyBottom)            // body
        if (wrongCount >= 3) line(0.7f, bodyTop + 0.04f, 0.58f, bodyTop + 0.14f) // left arm
        if (wrongCount >= 4) line(0.7f, bodyTop + 0.04f, 0.82f, bodyTop + 0.14f) // right arm
        if (wrongCount >= 5) line(0.7f, bodyBottom, 0.6f, bodyBottom + 0.14f)    // left leg
        if (wrongCount >= 6) line(0.7f, bodyBottom, 0.8f, bodyBottom + 0.14f)    // right leg
    }
}
