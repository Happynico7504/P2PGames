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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import net.nicochristmann.p2pgames.net.Msg
import net.nicochristmann.p2pgames.net.Player

/** An entry in the host's game picker. */
private data class GameOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val minPlayers: Int,
)

private val gameOptions = listOf(
    GameOption(Msg.GAME_TTT, "Tic-Tac-Toe", "2 play, extras watch", 2),
    GameOption(Msg.GAME_HANGMAN, "Hangman", "2+ players", 2),
    GameOption(Msg.GAME_LUDO, "Ludo", "2–4 players", 2),
    GameOption(Msg.GAME_UNO, "Uno", "2–8 players", 2),
    GameOption(Msg.GAME_GOOSE, "Goose Race", "2–8 players", 2),
    GameOption(Msg.GAME_KNIFFEL, "Kniffel", "2–8 players", 2),
    GameOption(Msg.GAME_BATTLESHIP, "Sea Battle", "2 play, extras watch", 2),
)

@Composable
fun HostLobbyScreen(
    players: List<Player>,
    status: String?,
    onStartGame: (gameId: String) -> Unit,
    onStartHangman: (customWord: String?) -> Unit,
    onLeave: () -> Unit,
) {
    var showHangmanDialog by remember { mutableStateOf(false) }
    val enoughPlayers = players.size >= 2

    val gamePicker: @Composable () -> Unit = {
        Text("Pick a game", style = MaterialTheme.typography.titleMedium)
        if (!enoughPlayers) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Waiting for at least one more player…",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        gameOptions.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowOptions.forEach { option ->
                    val enabled = players.size >= option.minPlayers
                    Card(
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) {
                                    if (option.id == Msg.GAME_HANGMAN) {
                                        showHangmanDialog = true
                                    } else {
                                        onStartGame(option.id)
                                    }
                                }
                                .padding(12.dp),
                        ) {
                            Text(
                                option.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (enabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            )
                            Text(
                                option.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
                if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
            Text("Close session")
        }
    }

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
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    ScreenHeader("Your session", status)
                    Spacer(Modifier.height(16.dp))
                    PlayerList(players = players, modifier = Modifier.weight(1f))
                }
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    gamePicker()
                }
            }
        } else {
            // Portrait: one scrollable column so any player count and the
            // full game list stay reachable on any screen height.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                ScreenHeader("Your session", status)
                Spacer(Modifier.height(16.dp))
                players.forEach { player ->
                    PlayerCard(player)
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(8.dp))
                gamePicker()
            }
        }
    }

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

/**
 * Two-pane scaffold for the joiner screens: [main] beside [side] in
 * landscape (side pane scrolls), [main] above [side] in portrait.
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
            androidx.compose.material3.Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
            ) {
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
private fun PlayerCard(player: Player) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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

@Composable
private fun PlayerList(
    players: List<Player>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(players, key = { it.id }) { player ->
            PlayerCard(player)
        }
    }
}
