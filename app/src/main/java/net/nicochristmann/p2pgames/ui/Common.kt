package net.nicochristmann.p2pgames.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Bottom controls shared by the game screens (back to lobby / leave). */
@Composable
fun GameActionButtons(
    isHost: Boolean,
    playing: Boolean,
    onBackToLobby: () -> Unit,
    onLeave: () -> Unit,
) {
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
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
    }
    OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
        Text(if (isHost) "Close session" else "Leave session")
    }
}

/** Screen title with an optional one-line status underneath. */
@Composable
fun ScreenHeader(title: String, status: String?) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    status?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium)
    }
}
