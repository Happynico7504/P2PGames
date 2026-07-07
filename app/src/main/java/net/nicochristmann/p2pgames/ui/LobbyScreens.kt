package net.nicochristmann.p2pgames.ui

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.nicochristmann.p2pgames.net.Player

/**
 * Two-pane lobby scaffold: [main] beside [side] in landscape (side pane
 * scrolls), [main] above [side] in portrait.
 */
@Composable
private fun AdaptiveLobby(
    main: @Composable (Modifier) -> Unit,
    side: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        if (maxWidth > maxHeight) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                ) {
                    main(Modifier.weight(1f))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                ) {
                    side()
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                main(Modifier.weight(1f))
                Spacer(Modifier.height(16.dp))
                side()
            }
        }
    }
}

@Composable
fun HostLobbyScreen(
    players: List<Player>,
    status: String?,
    onStartTicTacToe: () -> Unit,
    onStartHangman: (customWord: String?) -> Unit,
    onLeave: () -> Unit,
) {
    var showHangmanDialog by remember { mutableStateOf(false) }
    val enoughPlayers = players.size >= 2

    AdaptiveLobby(
        main = { listModifier ->
            ScreenHeader("Your session", status)
            Spacer(Modifier.height(16.dp))
            PlayerList(players = players, modifier = listModifier)
        },
        side = {
            if (!enoughPlayers) {
                Text(
                    "Waiting for at least one more player…",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = onStartTicTacToe,
                enabled = enoughPlayers,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start Tic-Tac-Toe")
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { showHangmanDialog = true },
                enabled = enoughPlayers,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start Hangman")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                Text("Close session")
            }
        },
    )

    if (showHangmanDialog) {
        HangmanWordDialog(
            onDismiss = { showHangmanDialog = false },
            onStart = { word ->
                showHangmanDialog = false
                onStartHangman(word)
            },
        )
    }
}

@Composable
private fun HangmanWordDialog(
    onDismiss: () -> Unit,
    onStart: (customWord: String?) -> Unit,
) {
    var word by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hangman") },
        text = {
            Column {
                Text(
                    "Type a secret word for the others to guess, or let the app " +
                        "pick a random word so you can play along too.",
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = word,
                    onValueChange = { input -> word = input.filter { it.isLetter() }.take(20) },
                    label = { Text("Secret word (A–Z)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onStart(word) },
                enabled = word.trim().length >= 3,
            ) {
                Text("Use my word")
            }
        },
        dismissButton = {
            TextButton(onClick = { onStart(null) }) {
                Text("Random word")
            }
        },
    )
}

@Composable
fun DiscoverScreen(
    peers: List<WifiP2pDevice>,
    status: String?,
    onRefresh: () -> Unit,
    onConnect: (WifiP2pDevice) -> Unit,
    onBack: () -> Unit,
) {
    AdaptiveLobby(
        main = { listModifier ->
            ScreenHeader("Nearby sessions", status)
            Spacer(Modifier.height(16.dp))
            if (peers.isEmpty()) {
                Column(
                    modifier = listModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Looking for nearby devices…\nMake sure the host has started a session.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = listModifier,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(peers, key = { it.deviceAddress }) { device ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onConnect(device) }
                                    .padding(16.dp),
                            ) {
                                Text(
                                    device.deviceName.ifBlank { "Unknown device" },
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    device.deviceAddress,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        side = {
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("Rescan")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        },
    )
}

@Composable
fun ClientLobbyScreen(
    players: List<Player>,
    status: String?,
    onLeave: () -> Unit,
) {
    AdaptiveLobby(
        main = { listModifier ->
            ScreenHeader("Session lobby", status)
            Spacer(Modifier.height(16.dp))
            PlayerList(players = players, modifier = listModifier)
        },
        side = {
            Text(
                "The host picks the next game — hang tight!",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                Text("Leave session")
            }
        },
    )
}

@Composable
private fun PlayerList(
    players: List<Player>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(players, key = { it.id }) { player ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        player.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (player.id == 0) {
                        Text("Host", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
