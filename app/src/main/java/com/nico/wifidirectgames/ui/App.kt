package com.nico.wifidirectgames.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.nico.wifidirectgames.GameSessionViewModel
import com.nico.wifidirectgames.Screen

@Composable
fun App(
    viewModel: GameSessionViewModel,
    ensurePermissions: (action: () -> Unit) -> Unit,
) {
    WiFiDirectGamesTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val screen = viewModel.screen
            BackHandler(enabled = screen != Screen.Home) { viewModel.leaveSession() }

            when (screen) {
                Screen.Home -> HomeScreen(
                    name = viewModel.playerName,
                    onNameChange = { viewModel.playerName = it },
                    status = viewModel.status,
                    onHost = { ensurePermissions { viewModel.hostGame() } },
                    onJoin = { ensurePermissions { viewModel.joinGame() } },
                )

                Screen.HostLobby -> HostLobbyScreen(
                    players = viewModel.players,
                    status = viewModel.status,
                    onStartTicTacToe = { viewModel.startTicTacToe() },
                    onStartHangman = { word -> viewModel.startHangman(word) },
                    onLeave = { viewModel.leaveSession() },
                )

                Screen.Discover -> {
                    val peers by viewModel.wifi.peers.collectAsState()
                    DiscoverScreen(
                        peers = peers,
                        status = viewModel.status,
                        onRefresh = { viewModel.refreshDiscovery() },
                        onConnect = { viewModel.connectToPeer(it) },
                        onBack = { viewModel.leaveSession() },
                    )
                }

                Screen.ClientLobby -> ClientLobbyScreen(
                    players = viewModel.players,
                    status = viewModel.status,
                    onLeave = { viewModel.leaveSession() },
                )

                Screen.TicTacToe -> TicTacToeScreen(
                    state = viewModel.tttState,
                    myPlayerId = viewModel.myPlayerId,
                    isHost = viewModel.isHost,
                    nameOf = viewModel::nameOf,
                    onCell = { viewModel.playTttCell(it) },
                    onBackToLobby = { viewModel.returnToLobby() },
                    onLeave = { viewModel.leaveSession() },
                )

                Screen.Hangman -> HangmanScreen(
                    state = viewModel.hangmanState,
                    myPlayerId = viewModel.myPlayerId,
                    isHost = viewModel.isHost,
                    nameOf = viewModel::nameOf,
                    onGuess = { viewModel.guessLetter(it) },
                    onBackToLobby = { viewModel.returnToLobby() },
                    onLeave = { viewModel.leaveSession() },
                )
            }
        }
    }
}
