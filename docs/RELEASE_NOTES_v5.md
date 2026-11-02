# HomeFlix TV v5 (5.11.0)

HomeFlix TV is rebuilt from the ground up as a Netflix-style living-room app. It brings a new look, smooth D-pad navigation, trailers on focus, a new player, casting from the web app, automatic updates, a HomeFlix row on your TV's home screen, and recommendations that learn from what you watch and rate.

**Download:** `homeflix-tv-v5.apk` (5.11.0, versionCode 22, 16.3 MB)
SHA-256: `8a7f1e15a053aade485b31019a994a9cdbbfd33af08dc4606e91e01f9a6fe331`

**Requires:**
- Android TV or Google TV, Android 6.0 or newer. The home-screen row needs Android 8.0 or newer.
- The HomeFlix server **[homeflix-wifi `release/v5.5`](https://github.com/azad25/homeflix-wifi/tree/release/v5.5)**.

---

## Highlights

- **New Netflix-style design:** a top menu (Search, Home, Shows, Movies, My HomeFlix), a full-screen billboard, and rows of titles.
- **Trailers on focus:** rest on a title and its tile widens and plays the trailer. The Home billboard rotates through this year's newest additions with their trailers.
- **Smooth navigation:** every press moves the row in one step, with no jumping back and forth. Holding a direction glides steadily.
- **New player:** Netflix-style controls and pause screen, reliable resume, and a Next Episode card.
- **HomeFlix on your TV's home screen:** Continue Watching, new arrivals and picks for you, right on the launcher.
- **Rate and My List:** Not for me / I like this / Love this! on every movie and show. Your ratings, your list and your watch history shape your recommendations.
- **Cast from the web app:** play any title on the TV from the HomeFlix website.
- **Automatic updates:** the app updates itself from your HomeFlix server. This is the last APK you need to install by hand.

## What's new

### Browsing
- A new top menu that hides while you browse rows, plus a notification bell and a profile menu (Notifications, Settings, About, Refresh, Exit).
- Movie, show, notification, settings and about pages also show the top menu.
- Each tab remembers where you were. Back goes from the page to the menu, then to Home, then exits.
- Home shows Continue Watching, **Top Picks for You**, Top 10, Trending, New on HomeFlix, **Because You Liked / Watched …**, TV Shows and genre rows.
- Continue Watching shows on Home, Movies and Shows. Show cards say where you are, e.g. "S1:E2 · Pilot · 20m left".
- Pages open instantly from the last saved copy, then refresh quietly in the background.

### Movie and show pages
- A trailer billboard with Resume, Play from beginning, **My List** and **Rate**.
- Shows also get season chips, an episode row with progress, and "Resume S1:E3".
- More Like This on movie pages.

### Player
- Netflix-style controls: a red timeline with scrub preview, time remaining, Audio & Subtitles, Speed and Next Episode.
- The pause screen shows the title logo, "Play from beginning" and My List.
- OK plays or pauses. Left and Right skip 10 s, and longer while held. Media keys work.
- Resume works reliably: leaving the app and coming back continues from the same frame.
- Progress is saved every few seconds and synced with the server.
- Recovery when a video won't play:
  - It drops a broken subtitle.
  - It switches to a transcoded stream if the TV can't decode the file.
  - It retries on network errors.
- Videos keep their aspect ratio. Trailers stay inside their frame.

### HomeFlix on the TV home screen
- A "HomeFlix" row on the launcher, in the Apps tab on Google TV. It shows what you're watching (with a progress bar), then new arrivals and picks for you, as 16:9 cards with title art.
- Cards resume playback or open the title, and Back returns to HomeFlix Home.
- In-progress titles also go into the system "Continue watching" row.
- The row refreshes when you open Home, shortly after you leave the app, after a restart, and every few hours.

### Ratings, My List and recommendations
- Rate any movie or show: **Not for me**, **I like this** or **Love this!** Choosing your current rating again clears it.
- Whole shows can now go in My List, and they appear under My HomeFlix.
- Recommendations learn from what you watch, rate and add to My List. "Not for me" titles are never recommended.

### Search
- A compact QWERTY keyboard with a number row, plus space, delete and clear keys.
- You can also type with a Bluetooth or USB keyboard, or the remote's number keys.
- A genre list and a results grid.

### Cast from the web app
- Pick the TV in the HomeFlix website's cast menu to play, pause, seek, change volume or subtitles.
- The TV stays ready to cast in the background and after a restart.
- **Settings:** turn casting on or off, rename the TV, and allow HomeFlix to open from a cast while another app is on screen.

### Updates
- The app checks your server for a new version at launch, when you return to it, and when the server announces one.
- An **Update available** dialog appears; one press installs it. Settings has **Check for updates** too.

### Notifications, Settings and About
- Redesigned notifications with artwork, and a red dot on the bell for unread items.
- Settings covers casting, the **server address** (editable, with a connection check), app updates and app info. Every row is reachable with the remote.
- About HomeFlix credits Ferdous Azad, developer of HomeFlix. It includes contact details, GitHub, and a Buy Me a Coffee link: https://buymeacoffee.com/ferdousazad

### Performance and stability
- Lighter on 2 GB TVs:
  - Images load at the size they're shown.
  - One shared trailer player.
  - Memory is freed when the system runs low.
  - Faster startup thanks to bundled profiles.
- Fixed "can't reach the server" errors when loading pages.
- Fixed crashes in Search, on movie pages, when playing, and when switching menu tabs.
- Popups (update, crash report) keep the remote's focus until you answer.
- If the app ever closes unexpectedly, the next launch shows a short report you can photograph and send.

## Installing

**New install:**
1. Install `homeflix-tv-v5.apk` (for example with `adb install homeflix-tv-v5.apk`, or a file manager on the TV).
2. Open HomeFlix → Profile → Settings → **Server address**, and enter `<server-ip>:8252`.

**Upgrading from v4:** if Android refuses the install (signature mismatch), uninstall the old app once, then install v5. After that, updates arrive automatically.

**For server owners:**
1. Update the server to `release/v5.5` and restart it.
2. Publish this build in either of two ways:
   - Copy the APK into `backend/app-updates/tv/`.
   - Use web Settings → Dashboard → **HomeFlix TV App** → download.
3. Connected TVs are offered the update right away.

---

Made by **Ferdous Azad** (ferdousazad12@gmail.com · [github.com/azad25](https://github.com/azad25)). Enjoying HomeFlix? [Buy me a coffee](https://buymeacoffee.com/ferdousazad).
