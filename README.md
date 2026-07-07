# WiFi Direct Games

An Android app for playing local multiplayer games over **Wi-Fi Direct** — no
router, hotspot, or internet connection needed. One player hosts a session,
nearby players discover it and join, and the host picks a game.

**Included games**

| Game | Players | Notes |
|------|---------|-------|
| Tic-Tac-Toe | 2 | Host plays X, first joiner plays O; everyone else spectates live. |
| Hangman | 2+ | Host types a secret word (and sits out) **or** picks a random word so everyone guesses. 6 wrong guesses lose the round. |
| Ludo (Mensch argere dich nicht) | 2-4 | Classic 40-field board. 6 = enter/extra roll, captures send tokens home. Finished players take podium places; the game runs until one player remains. |
| Uno | 2-8 | Full 108-card deck with skip/reverse/+2/wild/+4. A drawn card may be played immediately or kept. First empty hand wins. |
| Goose Race | 2-8 | The classic 63-field goose game: geese hop again, bridge, inn, well, maze, prison, death; field 63 must be hit exactly. |
| Kniffel | 2-8 | 13 categories, three rolls with holds, upper-section bonus at 63. Highest total wins. |
| Sea Battle | 2 | Random fleet placement with reshuffle, then take turns firing; a hit grants another shot. |

## How it works

```
┌────────────┐  Wi-Fi Direct group   ┌────────────┐
│   Host     │◄─────────────────────►│  Joiner(s) │
│ (group     │   TCP :8988           │            │
│  owner)    │◄─────────────────────►│            │
└────────────┘   JSON lines          └────────────┘
```

1. **Hosting** — the host calls `WifiP2pManager.createGroup()`, making itself
   the group owner, and starts a `ServerSocket` on port 8988.
2. **Joining** — joiners run peer discovery, tap the host's device, and connect
   with `groupOwnerIntent = 0` (so the host stays group owner). Once the group
   forms, they open a TCP socket to the group owner's address.
3. **Protocol** — newline-delimited JSON messages (`net/Protocol.kt`). The host
   is **authoritative**: clients only send inputs (`hello`, `move`, `guess`);
   the host validates every move against the rules engine and broadcasts the
   resulting `state` to all devices. Late joiners are synced into a running
   game as spectators.

### Code map

```
app/src/main/java/com/nico/wifidirectgames/
├── MainActivity.kt            # runtime-permission gate + Compose entry point
├── GameSessionViewModel.kt    # session coordinator (roster, screens, host logic)
├── wifi/WifiDirectController.kt  # WifiP2pManager wrapper (discovery, group, flows)
├── net/Protocol.kt            # message types + Player model
├── net/GameHost.kt            # TCP server run by the host
├── net/GameClient.kt          # TCP client run by joiners
├── game/TicTacToeGame.kt      # rules engine + UI state
├── game/HangmanGame.kt        # rules engine + UI state + word bank
└── ui/                        # Compose screens (home, lobbies, games)
```

### Adding a new game

1. Write a rules-engine class in `game/` with `toJson()` and a matching
   `UiState.from(json)` (see `TicTacToeGame` for the pattern).
2. Add a game id + input message to `net/Protocol.kt`.
3. Handle the input in `GameSessionViewModel.handleClientMessage()` and add a
   `startYourGame()` that broadcasts `Msg.start(...)`.
4. Add a Compose screen in `ui/` and wire it into `Screen` + `App.kt`.

## Building

Open the `android/` folder in Android Studio (Koala or newer), or build from
the command line with the Android SDK installed:

```bash
cd android
./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/
```

Requires JDK 17+. `compileSdk 34`, `minSdk 24` (Android 7.0+).

## Running it

You need **two or more physical Android devices** — emulators do not support
Wi-Fi Direct.

1. On every device: turn on Wi-Fi (Android 12 and below also need Location
   services for peer discovery).
2. Grant the permission prompt (Nearby devices on Android 13+, Location on
   older versions).
3. Device A: enter a name → **Host a session**.
4. Device B: enter a name → **Join a session** → tap device A in the list.
   Android may show a Wi-Fi Direct invitation dialog on the host — accept it.
5. The host starts a game from the lobby once everyone is in.

### Troubleshooting

- **Host doesn't appear in the list** — tap *Rescan*; make sure Wi-Fi is on on
  both sides and the host's lobby says "Session is live".
- **"Wi-Fi Direct is busy"** — a stale group from a previous run usually
  clears within a few seconds (the app retries once automatically); toggling
  Wi-Fi off/on also resets it.
- **Stuck on "Connecting…"** — some devices show a system invitation dialog on
  the host that must be accepted before the group forms.
