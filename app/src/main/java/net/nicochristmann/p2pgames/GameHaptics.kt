package net.nicochristmann.p2pgames

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import net.nicochristmann.p2pgames.game.BattleshipGame
import net.nicochristmann.p2pgames.game.BattleshipUiState
import net.nicochristmann.p2pgames.game.GameUi
import net.nicochristmann.p2pgames.game.GooseGame
import net.nicochristmann.p2pgames.game.GooseUiState
import net.nicochristmann.p2pgames.game.HangmanGame
import net.nicochristmann.p2pgames.game.HangmanUiState
import net.nicochristmann.p2pgames.game.KniffelGame
import net.nicochristmann.p2pgames.game.KniffelScoring
import net.nicochristmann.p2pgames.game.KniffelUiState
import net.nicochristmann.p2pgames.game.LudoGame
import net.nicochristmann.p2pgames.game.LudoUiState
import net.nicochristmann.p2pgames.game.TicTacToeGame
import net.nicochristmann.p2pgames.game.TttUiState
import net.nicochristmann.p2pgames.game.UnoGame
import net.nicochristmann.p2pgames.game.UnoUiState

/**
 * Central haptic feedback for game-state transitions. Every state update
 * (host and client alike) flows through [onStateChange], which compares the
 * previous and new state and plays at most one vibration:
 *
 *  - victory / game over
 *  - strong events (your ship got hit, your token got captured, you sank one)
 *  - light events (your hit, your capture, a special goose field you landed on)
 *  - "your turn" double pulse
 */
class GameHaptics(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun waveform(timings: LongArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(timings, -1)
        }
    }

    // Waveforms are (delay, vibrate, delay, vibrate, …) in milliseconds.
    fun turnStarted() = waveform(longArrayOf(0, 45, 90, 60))
    fun lightEvent() = waveform(longArrayOf(0, 35))
    fun strongEvent() = waveform(longArrayOf(0, 90))
    fun victory() = waveform(longArrayOf(0, 50, 70, 50, 70, 140))
    fun gameOver() = waveform(longArrayOf(0, 60))

    /** Decides which single haptic (if any) a state transition deserves. */
    fun onStateChange(old: GameUi?, new: GameUi, myId: Int) {
        // 1. Game just ended.
        val newWinner = winnerOf(new)
        val oldWinner = old?.let { winnerOf(it) } ?: -1
        if (newWinner != oldWinner && newWinner != -1) {
            if (newWinner == myId) victory() else gameOver()
            return
        }
        if (isOver(new) && old != null && !isOver(old)) {
            // Ended without a single winner (draw, hangman loss, …).
            gameOver()
            return
        }

        // 2. Game-specific events.
        if (old != null && old::class == new::class && gameEvent(old, new, myId)) return

        // 3. My turn just began.
        val oldTurn = old?.let { turnOf(it) } ?: -1
        if (turnOf(new) == myId && oldTurn != myId) turnStarted()
    }

    /** Returns true when an event haptic fired. */
    private fun gameEvent(old: GameUi, new: GameUi, myId: Int): Boolean {
        when {
            old is BattleshipUiState && new is BattleshipUiState -> {
                // Incoming fire: one of my ship cells newly shot.
                if (new.shotsAtMe.size > old.shotsAtMe.size) {
                    val previous = old.shotsAtMe.toSet()
                    val myCells = new.myShips.flatten().toSet()
                    if (new.shotsAtMe.any { it !in previous && it in myCells }) {
                        strongEvent()
                        return true
                    }
                }
                // My shot landed.
                if (new.myShots.size > old.myShots.size) {
                    when (new.event) {
                        "sunk" -> { strongEvent(); return true }
                        "hit" -> { lightEvent(); return true }
                    }
                }
            }

            old is GooseUiState && new is GooseUiState -> {
                // Special field on my own move (the mover was on turn before).
                if (old.turn == myId && new.event.isNotBlank()) {
                    lightEvent()
                    return true
                }
            }

            old is LudoUiState && new is LudoUiState -> {
                if (new.event.startsWith("capture:")) {
                    val victim = new.event.removePrefix("capture:").toIntOrNull()
                    if (victim == myId) {
                        strongEvent()
                        return true
                    }
                    if (old.turn == myId) { // I captured
                        lightEvent()
                        return true
                    }
                }
                if (new.event == "finished:$myId" && old.event != new.event) {
                    // I brought all tokens home (podium place mid-game).
                    if (new.ranking.firstOrNull() == myId) victory() else lightEvent()
                    return true
                }
            }
        }
        return false
    }

    private fun turnOf(ui: GameUi): Int = when (ui) {
        is TttUiState -> if (ui.status == TicTacToeGame.STATUS_PLAYING) ui.turn else -1
        is HangmanUiState -> if (ui.status == HangmanGame.STATUS_PLAYING) ui.turn else -1
        is LudoUiState -> if (ui.phase != LudoGame.PHASE_OVER) ui.turn else -1
        is UnoUiState -> if (ui.phase == UnoGame.PHASE_PLAY) ui.turn else -1
        is GooseUiState -> if (ui.phase == GooseGame.PHASE_PLAY) ui.turn else -1
        is KniffelUiState -> if (ui.phase == KniffelGame.PHASE_PLAY) ui.turn else -1
        is BattleshipUiState -> if (ui.phase == BattleshipGame.PHASE_BATTLE) ui.turn else -1
    }

    private fun winnerOf(ui: GameUi): Int = when (ui) {
        is TttUiState -> ui.winner
        is HangmanUiState -> ui.winner
        is LudoUiState ->
            if (ui.phase == LudoGame.PHASE_OVER) ui.ranking.firstOrNull() ?: -1 else -1
        is UnoUiState -> ui.winner
        is GooseUiState -> ui.winner
        is KniffelUiState ->
            if (ui.phase == KniffelGame.PHASE_OVER) {
                ui.players.maxByOrNull { KniffelScoring.totals(it.card).third }?.id ?: -1
            } else {
                -1
            }
        is BattleshipUiState -> ui.winner
    }

    private fun isOver(ui: GameUi): Boolean = when (ui) {
        is TttUiState -> ui.status != TicTacToeGame.STATUS_PLAYING
        is HangmanUiState -> ui.status != HangmanGame.STATUS_PLAYING
        is LudoUiState -> ui.phase == LudoGame.PHASE_OVER
        is UnoUiState -> ui.phase == UnoGame.PHASE_OVER
        is GooseUiState -> ui.phase == GooseGame.PHASE_OVER
        is KniffelUiState -> ui.phase == KniffelGame.PHASE_OVER
        is BattleshipUiState -> ui.phase == BattleshipGame.PHASE_OVER
    }
}
