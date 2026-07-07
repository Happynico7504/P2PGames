# P2PGames

An Android app for playing local multiplayer games over **Wi-Fi Direct** — no
router, hotspot, or internet connection needed. One player hosts a session,
nearby players discover it and join, and the host picks a game from the lobby.
Everything runs phone-to-phone.

## Games

| Game | Players | Rules in short |
|------|---------|----------------|
| Tic-Tac-Toe | 2 | Host plays X, first joiner plays O; everyone else spectates live. |
| Hangman | 2+ | Host types a secret word (and sits out) **or** picks a random word so everyone guesses. Correct guess keeps the turn; 6 wrong guesses lose the round. |
| Ludo | 2–4 | 40-field board, 4 tokens each. 6 = bring a token in / extra roll, three tries when everything is in the base, captures send tokens home. Finished players take podium places; the game runs until only one player remains and shows the final ranking. |
| Wild Cards | 2–8 | Uno-style shedding game: full 108-card deck with skip, reverse, +2, wild and +4. A drawn card may be played immediately or kept. First empty hand wins. |
| Goose Race | 2–8 | The classic 63-field goose game: geese hop again; bridge, inn, well, maze, prison and death do what they always did. Field 63 must be hit exactly (overshoot bounces back). |
| Five Dice | 2–8 | Yahtzee-style dice game: 13 categories, up to three rolls with holds, 35-point bonus at 63+ in the upper section. Highest total wins. |
| Sea Battle | 2 | 10×10 grids, fleet 5-4-3-3-2 placed randomly (reshuffle until happy). A hit grants another shot; sink the whole fleet to win. |

Extra players beyond a game's limit join as live spectators, and late joiners
are synced into the running game.

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
2. **Joining** — joiners run peer discovery, tap the host's device, and
   connect with `groupOwnerIntent = 0` (so the host stays group owner). Once
   the group forms, they open a TCP socket to the group owner's address.
3. **Protocol** — newline-delimited JSON messages (`net/Protocol.kt`). The
   host is **authoritative**: clients only send inputs (`hello`, `input`);
   the host validates every input against the rules engine and then sends
   **each device its own view** of the resulting state. That per-player view
   is what keeps hidden information hidden — Wild Cards hands and Sea Battle fleets
   never leave the host except to their owner.

### Code map

```
app/src/main/java/net/nicochristmann/p2pgames/
├── MainActivity.kt            # runtime-permission gate + Compose entry point
├── GameSessionViewModel.kt    # session coordinator (roster, screens, host logic)
├── wifi/WifiDirectController.kt  # WifiP2pManager wrapper (discovery, group, flows)
├── net/Protocol.kt            # message types + Player model
├── net/GameHost.kt            # TCP server run by the host
├── net/GameClient.kt          # TCP client run by joiners
├── game/HostGame.kt           # rules-engine interface + GameUi parser
├── game/TicTacToeGame.kt      # one file per game: engine + UI state
├── game/HangmanGame.kt
├── game/LudoGame.kt
├── game/UnoGame.kt
├── game/GooseGame.kt
├── game/KniffelGame.kt
├── game/BattleshipGame.kt
└── ui/                        # Compose screens (home, lobbies, one per game)
```

### Adding a new game

1. Write a rules engine in `game/` implementing `HostGame` — `input()`
   validates and applies player inputs, `toJsonFor(playerId)` returns that
   player's view, `playerLeft()` decides whether the game survives a
   departure. Add a matching `UiState.from(json)` implementing `GameUi`.
2. Register a game id in `net/Protocol.kt` and a parser branch in
   `GameUiParser`.
3. Add the game to `startSelectedGame()` in `GameSessionViewModel` and to
   the picker list in `ui/LobbyScreens.kt`.
4. Add a Compose screen in `ui/` and wire it into `GameScreen` in `App.kt`.

The screens follow one adaptive pattern: side-by-side panes in landscape,
a scrollable column in portrait, width caps on tablets.

## Building

Open the repo in Android Studio (Koala or newer), or on the command line
with the Android SDK installed:

```bash
./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/
./gradlew bundleRelease        # AAB at app/build/outputs/bundle/release/
```

Requires JDK 17+. `compileSdk 34`, `minSdk 24` (Android 7.0+).

### CI

Every push to `main` runs [GitHub Actions](.github/workflows/android.yml):

- **Debug APK** — installable artifact `app-debug` on each run (14 days).
- **Release AAB** — Play-Store-ready artifact `app-release-aab` (30 days),
  signed when the signing secrets below are configured, unsigned otherwise.
  The `versionCode` is the CI run number, so it strictly increases on its
  own; bump `versionName` in `app/build.gradle.kts` when it should read
  differently in the store.

## Releasing to the Play Store

One-time setup:

1. **Create an upload keystore** (keep it safe — losing it means losing the
   ability to update the app):

   ```bash
   keytool -genkeypair -v -keystore upload-keystore.jks -alias upload \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Add the four repository secrets** (GitHub → Settings → Secrets and
   variables → Actions):

   | Secret | Value |
   |--------|-------|
   | `KEYSTORE_BASE64` | `base64 -w0 upload-keystore.jks` output |
   | `KEYSTORE_PASSWORD` | the keystore password |
   | `KEY_ALIAS` | optional — defaults to `upload` |
   | `KEY_PASSWORD` | optional — defaults to `KEYSTORE_PASSWORD` (that's also what pressing Enter at keytool's key-password prompt means, and PKCS12 keystores always share it) |

3. **Create the app** in the [Play Console](https://play.google.com/console),
   enroll in Play App Signing, and upload the `app-release.aab` artifact from
   the latest CI run to an internal-testing track.

Per release afterwards: push to `main` (or trigger the workflow manually),
download `app-release-aab` from the run, upload it to a release track.

> **Naming note:** the in-app game names are deliberately trademark-free
> ("Wild Cards", "Five Dice", "Sea Battle", …). Keep it that way in the
> store listing too; the comparisons to well-known games in this README are
> descriptions, not names used in the app.

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
5. The host picks a game from the lobby once everyone is in; after a round
   the host returns everyone to the lobby for the next game.

### Troubleshooting

- **Host doesn't appear in the list** — tap *Rescan*; make sure Wi-Fi is on
  on both sides and the host's lobby says "Session is live".
- **"Wi-Fi Direct is busy"** — a stale group from a previous run usually
  clears within a few seconds (the app retries once automatically); toggling
  Wi-Fi off/on also resets it.
- **Stuck on "Connecting…"** — some devices show a system invitation dialog
  on the host that must be accepted before the group forms.
- **A player drops mid-game** — games that can continue without the player
  do (their tokens/cards are removed); games that can't return everyone to
  the lobby.
