# HomeFlix TV — v5 Redesign & Stability Plan

Branch: `v5` (cut from `release/v4` @ `42dd376`)
Goal: Netflix-TV-style top navigation, a focus-led content layout, smooth D-pad
navigation without stutter, fast app open/close, and reliable resume.

---

## 1. What's wrong today (found in the code)

### Navigation / focus
| # | Problem | Where |
|---|---------|-------|
| F1 | Every row hand-routes D-pad keys (`onKeyEvent` on each card) and keeps a `FocusRequester` list **capped at 20**. Pressing RIGHT on item 20+ is consumed but nothing gets focus, so focus gets stuck. | `components/MediaRow.kt` (same pattern in Top10Row, ThumbLogoRow, ContinueWatchingRow, FeaturedBanner) |
| F2 | Horizontal scroll uses `scrollToItem()` (a jump, no animation) only when the card is already off screen. This causes the visible "snap"/stutter. | `MediaRow.kt` `onFocusChanged` |
| F3 | Vertical UP is a hand-built chain of requesters + `animateScrollToItem` + `repeat(6){ requestFocus; delay(50) }` retry loops. Focus and scroll fight each other, and focus can be lost when a row is not composed yet. | `screens/home/NetflixHomeScreen.kt` (`navUpTo`, `onNavigateUpToHero`) |
| F4 | 31 `requestFocus()` calls and 41 `delay()` calls across presentation, including a 300 ms startup delay before the first focus. | whole `presentation/` |
| F5 | The side nav is duplicated inside 9 screens, so every tab switch rebuilds the whole screen and loses its scroll/focus position. | `NetflixSideNavigation` used by Home, Browse, Search, TvShows, MyList, Notifications, Details, TvSeriesDetails, TvSeriesSeason |
| F6 | Cards use Material3 `Card` with animated elevation (shadow re-render), plus `border` and `zIndex` swapped in `then{}` on every focus change. This makes recomposition and drawing heavy on TV GPUs. | `MediaCard.kt`, `PosterCard.kt` |

### Player / lifecycle (the "pause → exit → reopen doesn't play" bug)
| # | Problem | Where |
|---|---------|-------|
| P1 | `ON_PAUSE`/`ON_STOP` only saves progress **if `player.isPlaying`**. If you pause and then press Home, the position is never saved. | `components/VideoPlayer.kt` ~L703 |
| P2 | `savePlaybackProgress()` launches on `rememberCoroutineScope()`. That scope is cancelled in `onDispose`, so the save on close is usually dropped. | `VideoPlayer.kt` L163 + L691 |
| P3 | Manifest has `singleTask` + `clearTaskOnLaunch="true"`, so reopening from the launcher clears the player. Nothing re-prepares or resumes the player on `ON_START`. | `AndroidManifest.xml`, `VideoPlayer.kt` |
| P4 | ExoPlayer is created and owned inside a composable (`LaunchedEffect(media.id)`), so it is torn down and rebuilt by recomposition or navigation instead of by the lifecycle. | `VideoPlayer.kt` L261 |
| P5 | Loading isn't tied to "media ready": the `Loading` UI state renders nothing (blank screen). A **blind 10 s timeout** then hides the spinner and forces `playWhenReady` even if the stream isn't ready, which leaves a black or frozen screen. | `VideoPlayerScreen.kt`, `VideoPlayer.kt` L266 |
| P6 | Subtitle fetch blocks `prepare()` for up to 3 s. | `VideoPlayer.kt` |
| P7 | The URL fallback loop is ineffective: `setMediaItem()`/`prepare()` never throw for a bad URL (errors arrive async in `onPlayerError`), so the first URL always "wins". | `VideoPlayer.kt` L403-457 |
| P8 | The 500 ms position poll writes state that recomposes the whole 1,300-line player. | `VideoPlayer.kt` L660 |

### Build / startup
- `isMinifyEnabled = false` and no baseline profile, so the release build runs mostly interpreted code on first launches. That causes janky first scrolls and a slow cold start.
- No SplashScreen API, and Home shows a spinner until the network responds even though `ContentCache` exists.

---

## 2. Target UX (from the Netflix reference screenshots)

```
┌───────────────────────────────────────────────────────────────────┐
│ [avatar▾]        🔍  Home  Shows  Movies  My HomeFlix          [H] │  ← top nav (pill on selected)
│ ───────────────────────────────────────────────────────────────── │
│  HERO / SPOTLIGHT  (backdrop, title logo, meta, Play | More Info)   │
│  Show • Thriller • 2026 • 7 Episodes • 16+                          │
│  description…                                                       │
│ Because you watched X                                               │
│ ▢ ▢ ▢ ▢ ▢ ▢ (row peeks at bottom)                                   │
└───────────────────────────────────────────────────────────────────┘
```
After pressing DOWN into rows (screenshot 2/3):
```
│ US Sci-Fi Movies                                                    │
│ ┌──────────────────────┐ ▯ ▯ ▯   ← focused card EXPANDS to 16:9     │
│ │ backdrop / preview   │          backdrop, others stay 2:3 posters │
│ └──────────────────────┘                                            │
│ Movie • Drama • 2025 • 2h 32m • 18+                                 │
│ description of the focused title                                    │
│ Next row title (peeks)                                              │
```

### D-pad contract
| Where | Key | Result |
|-------|-----|--------|
| Top nav | ◀ ▶ | Move between Search / Home / Shows / Movies / My HomeFlix / profile. The tab **switches after ~300 ms dwell** (like Netflix) or on OK. |
| Top nav | ▼ | Into the page and restore the last focused item for that tab. |
| Top nav | Back | On Home: exit the app. On another tab: go to Home. |
| Hero | ◀ ▶ | Between Play / More Info. ▲ goes to the top nav. ▼ goes to the first row. |
| Row | ◀ ▶ | Move within the row. The focused card stays pinned at the left keyline and the row slides under it. The first item doesn't wrap. |
| Row | ▲ ▼ | Previous/next row. That row's last focused column is restored (`focusRestorer`). The page scrolls so the focused row sits at a fixed vertical keyline. |
| Row (deep) | Back | Smooth-scroll to top, focus the hero; second Back focuses the top nav. |
| Detail | Back | Pop to the previous screen with its focus restored. |

---

## 3. Architecture changes

### 3.1 App shell with top navigation (fixes F5)
- New `presentation/shell/AppShell.kt`: `TopNavBar` (rewritten) + nested `NavHost` for the tab graph (`home`, `shows`, `movies`, `my-homeflix`, `search`).
- Tab switches use `navigate(route) { popUpTo(home){ saveState = true }; launchSingleTop = true; restoreState = true }`, so each tab keeps its scroll and focus state.
- Full-screen destinations (Details, Series, Season, Player) live in the outer graph, outside the shell. No top nav is drawn over the player.
- The top nav auto-hides (slides up + fades) when focus is ≥ 2 rows deep, and reappears when the page scrolls back to top. This is a `graphicsLayer` translation, not a layout change.
- Profile avatar dropdown (top-left): Switch server, Notifications, Settings, Exit. This replaces the Notifications side-nav entry.
- Delete `NetflixSideNavigation.kt` and `SidebarOverlay.kt`, and remove them from all 9 screens.
- "Movies" replaces Browse(type=movie). "Shows" replaces TvShows. "My HomeFlix" = My List + Continue Watching + Notifications.

### 3.2 Focus system (fixes F1-F4)
Replace all manual key routing with Compose's native focus traversal:
- Each row is a `LazyRow` with `Modifier.focusGroup().focusRestorer()`, so UP/DOWN returns to the last focused card in that row with no requester lists.
- Each page root uses `focusProperties { enter = … }` to pick its initial target (hero Play button) instead of `delay(300); requestFocus()`.
- Top nav ↔ content crossing uses `focusProperties { up = topNavRequester }` on the hero only. That leaves one `FocusRequester` per screen instead of ~10.
- No `onKeyEvent` for arrows anywhere except the player and the Back handler. Use `BackHandler` instead of `Key.Back` in `onKeyEvent`.
- Delete every `repeat(n){ try requestFocus } catch delay` loop.

### 3.3 Netflix-style pinned scrolling (fixes F2, stutter)
- A custom `BringIntoViewSpec` provided via `LocalBringIntoViewSpec` (Compose foundation 1.7, already in BOM 2024.09). The focused item's `calculateScrollDistance` pins it to a keyline (row: 48 dp from the left edge; column: ~30% from the top). The spec uses `tween(300, FastOutSlowInEasing)`.
- This replaces every `scrollToItem`/`animateScrollToItem` call that focus currently triggers. Scrolling is driven by focus alone, which removes the focus-vs-scroll race.
- Key repeat (holding ◀▶): throttle to one move per ~90 ms while a scroll animation is running so long presses glide instead of queueing up.

### 3.4 Focus-led content layout
- `SpotlightCard`: when focused, it animates width from poster 2:3 to 16:9 backdrop. The expansion is debounced ~150 ms, so fast scrolling through a row doesn't thrash layout. Neighbours shift right, as in screenshot 2.
- The metadata line (`Movie • Drama • 2025 • 2h 32m • 18+`) and description render **below the focused row**. All fields already exist in `Media` (type, genreNames, year, runtime, certification, description).
- Title logo overlay via `ApiUtils.getLogoUrl()`. Badges (`Recently Added`, `Top 10`, `New Episode`) come from existing data.
- Focused card plays its preview or trailer (see 3.7). It falls back to the banner/backdrop, or to the poster when there's no preview.
- Hero (Home, Shows, Movies): full-bleed backdrop with gradient scrims, logo, meta, Play / More Info, and the next row peeking at the bottom, as in screenshot 5.

### 3.5 Rendering performance (fixes F6)
- Use `androidx.tv:tv-material` `Surface`/`Card` (already a dependency) with `scale`, `border` and `glow` focus indications. These are GPU-only and don't trigger recomposition. Drop Material3 `Card` elevation on TV.
- `@Immutable` UI models. Give lazy lists `key = { it.id }` and `contentType`.
- Coil: request explicit sizes (poster ≈ 342 px, backdrop ≈ 1280 px), `crossfade(150)`, and prefetch the next 4 cards' images plus the focused item's backdrop.
- Read animated values only inside `graphicsLayer { }` lambdas.
- Bump the Compose BOM to the latest stable, and drop `tv-foundation` alpha (TvLazy* is deprecated and unused).
- **Baseline profile** module + `profileinstaller`. Enable R8 (`isMinifyEnabled = true`, `isShrinkResources = true`) with keep rules for Retrofit/Gson DTOs. This is the single biggest win against first-scroll jank on TV.

### 3.6 Smooth open / close
- `core-splashscreen` with `setKeepOnScreenCondition { !homeReady }`, plus a black window background so there's no white flash.
- **Cache-first Home**: render the `ContentCache` snapshot immediately and refresh in the background, swapping rows without moving focus. Skeleton shimmer rows show only on the very first install.
- Back on the Home top nav exits the app with a fade. There's no "press again to exit" toast unless you want one.
- Remove `clearTaskOnLaunch` (see P3) and handle "return to player" explicitly (below).

### 3.7 Focus-driven preview / trailer playback (Netflix "focus plays trailer")
Today `BackgroundVideo.kt` builds a **new ExoPlayer for every title**. Codec init alone takes 300-800 ms on TV SoCs. It also waits `600 ms + 2500 ms` plus a `while(!READY) delay(200)` poll, and it requests `/preview-clips/{id}` even for titles that have no clip (404). After `ON_STOP` it releases the player, but `ON_RESUME` calls `player?.play()` on null, so the preview never comes back after reopening the app.

v5 design:
- **One app-wide `PreviewPlayer`** (Hilt `@Singleton`, created once, `PlayerView` surface reused). Changing titles = `setMediaItem()` + `prepare()` on the warm player, with no codec teardown. It is shared by the Home/Shows/Movies hero, the expanded `SpotlightCard`, and Details.
- **Know availability up front:** use `media.previewClipPath` (fallback `trailerPath`, both already in `Media`/`MediaDto`). If both are null, show a static image only and never touch the player.
- **Image first, always:** banner/backdrop (16:9) when it exists, otherwise the poster blurred-fill + sharp poster. It is preloaded by Coil while the neighbour is focused, so the tile is never empty.
- **Dwell → preload → play:**
  1. Focus lands: after **~400 ms dwell**, start `prepare()` paused (fast D-pad scrolling never spawns loads; each new focus cancels the previous one).
  2. Reveal at **~1.2 s** (hero ~1.5 s) only once `onRenderedFirstFrame` fires. Crossfade 300 ms from the image to the video. If not ready yet, keep the image and reveal the moment the first frame arrives. No polling loops.
  3. Focus leaves: fade the video out over 150 ms, then `stop()` (keep the player warm, don't release).
- **Start fast:** `DefaultLoadControl` with `bufferForPlayback ≈ 500 ms`, small max buffer (≈ 8 s), and video size capped to the tile/screen (`setMaxVideoSize(1280,720)` for tiles, 1080p for the hero) so the TV decodes less. Audio is muted on tiles. The hero may unmute after 3 s if you want that (setting).
- **Prefetch neighbours:** use media3 `PreloadManager`/`CacheDataSource` (`SimpleCache`, ~200 MB LRU) to pre-buffer the first ~2 s of the focused item's ±1 neighbours, so ◀▶ onto a neighbour starts almost instantly. The repeat-view case is served from the disk cache.
- **Lifecycle:** `ON_STOP` releases the codec (`stop()` + `clearVideoSurface`). `ON_START` re-prepares the currently focused item, which fixes the "preview never comes back" bug. The preview is paused whenever the full player opens.
- **Low-end guard:** `DeviceCapabilities` (already present) disables tile previews on < 1.5 GB RAM devices but keeps the hero preview, and the setting can turn previews off.
- **Backend nice-to-have:** generate short (30-60 s) 720p H.264 preview clips with **faststart** (moov atom at front) or as HLS. That is the single biggest factor in "instant" start. Expose `has_preview` in list endpoints.

---

## 4. Player rewrite (fixes P1-P8)

1. **`PlayerController` owned by `VideoPlayerViewModel`.** It creates the ExoPlayer once per nav entry, survives recomposition, and is released in `onCleared()`.
2. **Lifecycle-driven:**
   - `ON_STOP`: always save the position (playing **or paused**), remember `wasPlaying`, and release decoders (`player.stop()`) to free hardware for other apps.
   - `ON_START`: re-`prepare()` at the saved position. Restore `playWhenReady = wasPlaying`. If the user had paused, show the pause overlay at the same frame.
   - If the process was killed, launching from the launcher opens Home, where Continue Watching shows the exact position because it was persisted (step 3).
3. **Progress that is never lost:**
   - Write the position locally (DataStore/Room `playback_progress`) every 10 s, and on pause, stop, seek and close. The local value is the resume source of truth.
   - Sync to the backend `POST /playback/progress` from an application-scoped coroutine (`@ApplicationScope` via Hilt), not a composable scope. Use WorkManager as a retry fallback when offline.
4. **Explicit loading state machine:**
   `Resolving (metadata) → Preparing (backdrop + logo + spinner) → FirstFrame (onRenderedFirstFrame) → Playing | Paused | Buffering | Error(retry)`.
   The loading overlay stays up until `onRenderedFirstFrame` and fades out over 200 ms. The blind 10 s timeout goes. A real error shows "Retry / Back" instead of auto-closing.
5. **Faster start:** call `prepare()` immediately and load subtitles in parallel. Attach sidecar subtitles when they arrive, or let ExoPlayer pick embedded ones. Tune `DefaultLoadControl` for start (`bufferForPlayback` ≈ 1.5 s) and use `setSeekParameters(CLOSEST_SYNC)` for fast resume seeks.
6. **Real fallback:** walk the URL list inside `onPlayerError` instead of a try/catch around `prepare()`.
7. **Split the 1,300-line `VideoPlayer.kt`** into `PlayerSurface`, `PlayerControls`, `PauseOverlay` (the screenshot-1 style rating bar, title, scrubber, audio/subtitle chips), `SettingsPanel` and `NextEpisodeCard`. Position ticks are read via `derivedStateOf`/`graphicsLayer` only in the scrubber, so the poll stops recomposing everything.
8. **Controls UX (screenshot 1):** ◀▶ on a hidden UI = seek ±10 s with the scrubber shown. OK = pause/show. ▼ = audio/subtitle chips. Back = hide controls, then close (saving first).

---

## 5. Backend (`homeflix-wifi`)
No backend changes are required for v5. Existing endpoints cover it: `/playback/progress`, `/playback/progress/{id}`, `/playback/recently-watched`, logos, backdrops and preview clips. Nice-to-haves for later:
- `GET /home/rows`: server-composed rows ("Because you watched…", genre rows) so Home loads in one request.
- Return `certification`, `runtime` and `logoUrl` consistently for episodes and series.

---

## 6. Delivery order (each step is a commit on `v5`, buildable and testable on TV)

1. **Player reliability** (P1-P8): fixes the most painful bug first and is independent of the UI rework.
2. **Focus foundation**: `BringIntoViewSpec` keyline scrolling + `focusRestorer` rows. Rewrite `MediaRow` and remove the manual key handlers.
3. **App shell + top nav**: remove the side nav from all screens and add tab state save/restore.
4. **Home redesign**: hero, `SpotlightCard` expanding tiles, info-below-row, cache-first load, plus the shared `PreviewPlayer` focus trailers (3.7).
5. **Shows / Movies / My HomeFlix / Search** on the same components. Search uses the left keyboard grid + genre list and a results grid, as in screenshot 4.
6. **Details / Series / Season** restyle to match.
7. **Performance pass**: baseline profile, R8, Coil sizing, Compose BOM bump, and a Macrobenchmark for startup + row scroll frame timing.
8. Splash + open/close polish, then QA on a low-end TV box (1-2 GB RAM).

### Acceptance checks
- Hold ▶ through a 50-item row: no stuck focus, no frame drops > 1 per second in Macrobenchmark.
- ▲ from any row repeatedly reaches the hero, then the top nav, with no lost focus.
- Pause → Home → reopen app or player: resumes at the same frame, paused. Play → Home → reopen: resumes playing. Kill process → Continue Watching shows the correct position.
- Focus a title with a preview: video fades in ≤ 1.5 s, and there is no stutter while holding ◀▶ through the row. A title without a preview shows its banner, or its poster if there's no banner, never a blank tile.
- The player never shows a blank black screen. A spinner or error is always visible until the first frame.
- Cold start to interactive Home in < 1.5 s with cache.
