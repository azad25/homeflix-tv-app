# HomeFlix TV v5: Netflix-style redesign

Branch `v5`. Installable APK: `apk/homeflix-tv-v5.apk`. It's a signed release build: version 5.11.0 (R8 code shrinking is currently off; see app/build.gradle.kts).

> **First install:** the APK is signed with the repo's sideload key (`app/keystore`). If an older build signed with a different key is on the TV, uninstall it once. After that, every v5 build installs as an update.

## What changed

### Navigation (Netflix 2025 TV layout)
- **Top nav** replaces the side menu: the HOMEFLIX wordmark, then Search, Home, Shows, Movies and My HomeFlix, then a profile menu (Notifications, Refresh, Exit).
- **Switching tabs:** a tab switches when focus rests on it (about 280 ms). OK moves into the page.
- **Per-tab memory:** each tab keeps its scroll and focus position.
- **Back:** goes from the page to the top nav, then to the Home tab, then exits the app. In the player, Back exits playback.
- **Up from the page** always enters the nav on the current tab. **Down from the nav** lands on the billboard.
- **Hiding:** the nav hides while you browse rows and comes back at the top of the page.

### Focus and scrolling (the stutter fixes)
- **No manual key routing.** All per-card arrow handling is gone, including the FocusRequester lists that stopped at item 20, the retry loops, and the 300 ms focus delays.
- **Rows:** each Left/Right press is one eased slide that brings the next tile to the left keyline. It never overshoots and swings back, because tiles keep their size while you move and the tile you leave collapses in step with the slide. Pages glide section by section.
- **Row memory:** entering a row lands on that row's remembered tile.
- **Held D-pad:** repeats are throttled to a steady cadence, so holding a direction scrolls smoothly instead of queueing up.
- **Back from Details or the player** returns focus to the exact tile you left.
- **Tests:** these rules are covered by Robolectric tests that send real D-pad presses (`app/src/test/.../FocusNavigationTest.kt`).

### Focus content and trailers
- **Expanding tile:** once focus rests on a tile (about 0.65 s), it widens to the right from poster to 16:9 backdrop with the title logo. The meta line (`Movie • Drama • 2025 • 2h 32m • 18+`) and synopsis appear under the row.
- **Trailer playback:**
  - The trailer starts after about 1.5 s on a tile and 2 s on the billboard, through **one shared, muted preview player**.
  - It fades in only once frames are actually playing. Titles without a preview keep the banner, or the poster if there's no banner.
  - Clips go through a 200 MB on-disk cache, and the neighbouring tiles' clips are prefetched.
- **Where trailers play:** only boxes with under 1.2 GB of RAM skip tile trailers. 2 GB TVs such as Sony play them everywhere.

### Player (Netflix-style)
- **Ownership and lifecycle:** the ExoPlayer is owned by the ViewModel, so UI changes can never tear it down.
  - Leaving the app saves the position and releases the decoder.
  - Coming back re-prepares at the same frame and keeps the play or pause state. This fixes "paused, left, reopened, won't play".
- **Progress:** saved locally every 5 s and on pause, stop and exit, then synced to the server from an app-wide scope, so it is never lost.
- **Loading:** a loading screen (backdrop and title) stays up until the first video frame. There is no blind timeout.
- **Recovery:** in order, the player drops a broken subtitle, falls back to an H.264 transcode (HLS) if the TV can't decode the file, and retries with backoff on network errors.
- **Controls:** Back, Restart and the title at the top. Play state, elapsed time, the red timeline (with scrub preview) and time remaining at the bottom. Chips for Audio & Subtitles, Speed and Next Episode.
- **Pause screen:** the paused layout shows the logo, "Play from beginning" and "My List". After about 9 s it becomes the "You're watching" screen.
- **Next Episode:** a card appears for the last 25 s, and the next episode autoplays at the end.
- **Remote keys:** OK plays or pauses. Left and Right seek 10 s, then 30 s and 60 s while held. Media keys are supported.

### Screens
- **Home, Shows, Movies and My HomeFlix** are cache-first. They paint instantly from the last feed, then load in parallel and swap in place.
- **Details:** trailer billboard, Resume / Play from beginning / My List, cast and director, and More Like This.
- **Series:** episode trailer, Resume S1:E3, season chips, and an episode row with progress.
- **Search:** on-screen keyboard and genres on the left, results grid on the right.
- **Notifications:** a list on the left, with the focused item's artwork as the background.

### Memory and performance
- **Images:** decoded at the size they're drawn and cached on disk even without server cache headers.
- **Memory pressure:** the app frees the preview decoder and the bitmap cache when the system is low on memory.
- **Player buffers:** sized to the device (40, 64 or 128 MB). Small caches are bounded, and LeakCanary is included in debug builds.
- **Build:** R8 for library code, bundled Compose baseline profiles via profileinstaller, Kotlin 2.0.21 (strong skipping), a black splash with no white flash, and no verbose HTTP logging in release.

### Ratings and My List (5.11)
- **Rate:** movie and show pages have a **Rate** button. It opens Not for me, I like this or Love this!; picking your current rating again clears it.
- **My List on show pages:** whole shows can now go in My List, and they appear in My HomeFlix → My List.
- **Fixed:** the movie page's My List button never showed, because the server's `{"in_list": …}` answer wasn't parsed.
- **Personalised Home:** Home's **Top Picks for You** and up to two **Because You Liked / Watched …** rows come from the server's `/recommendations/for-you`. These learn from what you watch, rate and add to My List. "Not for me" titles are never recommended. Top Picks also feed the TV home-screen row.
- **Search keyboard:** a compact QWERTY keyboard with keycap-style keys and a number row. ▲/▼ go to the key at the same position in the next row. The bottom row has space, delete and clear.
- **Server:** needs homeflix-wifi `release/v5.5`, which has the ratings API (`docs/PERSONALISATION.md` there).

### HomeFlix on the TV home screen (5.10)
- **HomeFlix row:** a "HomeFlix" row on the TV's home screen, like Netflix and Prime Video (on Google TV it is in the Apps tab).
  - What you're watching comes first, with a "Continue Watching" label and a red progress bar. A mix of new arrivals (marked NEW) and recommendations follows.
  - Pressing a card resumes playback, or opens the movie or show page. Back from there goes to HomeFlix Home.
- **Continue watching:** in-progress titles are also published to the system "Continue watching" row (Watch Next). Titles you remove there stay removed until you watch more.
- **Card art:** the app draws each card: the backdrop with the title logo, the label and the progress bar. Launchers get the art from the app (`content://com.homeflix.tv.homeart/…`), so cards show even if the launcher can't reach the server.
- **Freshness:** the row is refreshed every time Home loads, about 20 s after you leave the app, after boot or an update, when the launcher asks, and every 3 h in the background (WorkManager).
- **Code:** `app/src/main/java/com/homeflix/tv/launcher/`. Tests are in `HomeScreenTest` and `AppSmokeTest.homeScreenCard_opensTheTitle`.

### Smoother scrolling (5.10)
- **Rows and pages:** they follow focus with one continuous ease-out motion. A new press retargets the running motion instead of restarting it, so quick or held presses glide.
- **Other lists:** Search results, Notifications, Settings and season chips keep the focused item in view with a margin, using a critically damped spring. Held keys are throttled to a steady pace.

### Cast from the web app (5.1)
This implements `docs/TV_CAST_PROTOCOL.md` from the homeflix-wifi `release/v5.5` branch.
- **Connection:** `CastConnection` keeps `ws://<server>/api/cast/tv/ws` open. It answers ping with pong and reconnects forever with a 1 s → 30 s backoff plus jitter.
  - It reconnects immediately when the network comes back or the server switches between LAN and remote.
  - When the server closes the socket, the TV completes the close handshake and dials again.
- **Commands:** `CastController` handles `play`, `pause`, `resume`, `seek`, `stop`, `volume` and `subtitle`.
  - Every command is acked. Unknown actions get `unsupported action`, a missing player gets `Nothing is playing`, and a 404 gets `Media not found`.
  - `play` for the title that's already open just seeks and resumes.
  - `play` for a different title replaces the player and starts at the exact position the browser sent.
  - A "Casting from …" notice is shown on the TV.
- **Status:** sent on every player state change, every 1 s while playing, and after each reconnect. The TV sends `idle` when you press Back.
- **Background launch:**
  - With "Display over other apps" granted (Settings → *Allow HomeFlix to open from casting*), the player opens directly.
  - Otherwise the TV shows a full-screen "tap to play" notification. If nothing opens within 10 s, the browser gets `Open HomeFlix on the TV to start playback`.
- **Always reachable:** `CastReceiverService` is a foreground service with a minimal "Ready to cast" notification, also started on boot.
  - It uses the `specialUse` type rather than `dataSync`, because Android 15 caps `dataSync` at 6 h/day and blocks it from starting at boot.
- **Settings (profile menu → Settings):** casting on/off, cast name (renames live), connection status, and the overlay-permission button. Every entry is a plain row the D-pad moves through; the cast name and server address open a small edit dialog when you press OK.
- **Popups** (update ready, crash notice) are real dialog windows, so they take the remote's focus and keep it until you answer.
- **Tests:** `CastProtocolTest` plays the server side with MockWebServer: hello, ping/pong, every command and its ack, 404, same-title play, status and reconnect.
- **Granting overlay on TVs that hide the setting:**
  ```bash
  adb shell appops set com.homeflix.tv SYSTEM_ALERT_WINDOW allow
  ```

## Building

```bash
./gradlew assembleRelease testDebugUnitTest
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

## Backend suggestions (homeflix-wifi)

None of these are required. Each one would make the app faster:
- Encode preview clips at 720p with a `-g 48` keyframe interval and `+faststart` on every path. `tryShortPreviewGeneration` currently lacks faststart.
- Add a gzip middleware.
- Support `?w=` image sizes, and add ETag / 304 responses.
- Fill in `runtime` and `certification` for local widget items.
