package net.nicochristmann.p2pgames.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import net.nicochristmann.p2pgames.GameSessionViewModel
import net.nicochristmann.p2pgames.Screen
import net.nicochristmann.p2pgames.game.BattleshipUiState
import net.nicochristmann.p2pgames.game.GooseUiState
import net.nicochristmann.p2pgames.game.HangmanGame
import net.nicochristmann.p2pgames.game.HangmanUiState
import net.nicochristmann.p2pgames.game.KniffelUiState
import net.nicochristmann.p2pgames.game.LudoUiState
import net.nicochristmann.p2pgames.game.TicTacToeGame
import net.nicochristmann.p2pgames.game.TttUiState
import net.nicochristmann.p2pgames.game.UnoUiState

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
                    onStartGame = { viewModel.startSelectedGame(it) },
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

                Screen.Game -> GameScreen(viewModel)
            }
        }
    }
}

@Composable
private fun GameScreen(viewModel: GameSessionViewModel) {
    val myPlayerId = viewModel.myPlayerId
    val isHost = viewModel.isHost
    val onBackToLobby = { viewModel.returnToLobby() }
    val onLeave = { viewModel.leaveSession() }

    when (val ui = viewModel.gameUi) {
        is TttUiState -> TicTacToeScreen(
            state = ui,
            myPlayerId = myPlayerId,
            isHost = isHost,
            nameOf = viewModel::nameOf,
            onCell = { viewModel.sendInput(TicTacToeGame.moveInput(it)) },
            onBackToLobby = onBackToLobby,
            onLeave = onLeave,
        )

        is HangmanUiState -> HangmanScreen(
            state = ui,
            myPlayerId = myPlayerId,
            isHost = isHost,
            nameOf = viewModel::nameOf,
            onGuess = { viewModel.sendInput(HangmanGame.guessInput(it)) },
            onBackToLobby = onBackToLobby,
            onLeave = onLeave,
        )

        is LudoUiState -> LudoScreen(
            state = ui,
            myPlayerId = myPlayerId,
            isHost = isHost,
            nameOf = viewModel::nameOf,
            onInput = viewModel::sendInput,
            onBackToLobby = onBackToLobby,
            onLeave = onLeave,
        )

        is UnoUiState -> UnoScreen(
            state = ui,
            myPlayerId = myPlayerId,
            isHost = isHost,
            nameOf = viewModel::nameOf,
            onInput = viewModel::sendInput,
            onBackToLobby = onBackToLobby,
            onLeave = onLeave,
        )

        is GooseUiState -> GooseScreen(
            state = ui,
            myPlayerId = myPlayerId,
            isHost = isHost,
            nameOf = viewModel::nameOf,
            onInput = viewModel::sendInput,
            onBackToLobby = onBackToLobby,
            onLeave = onLeave,
        )

        is KniffelUiState -> KniffelScreen(
            state = ui,
            myPlayerId = myPlayerId,
            isHost = isHost,
            nameOf = viewModel::nameOf,
            onInput = viewModel::sendInput,
            onBackToLobby = onBackToLobby,
            onLeave = onLeave,
        )

        is BattleshipUiState -> BattleshipScreen(
            state = ui,
            myPlayerId = myPlayerId,
            isHost = isHost,
            nameOf = viewModel::nameOf,
            onInput = viewModel::sendInput,
            onBackToLobby = onBackToLobby,
            onLeave = onLeave,
        )

        null -> Unit // brief gap while the first state message is in flight
    }
}
