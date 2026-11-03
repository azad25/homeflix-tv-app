# HomeFlix TV v5 (5.15.3)

HomeFlix TV is rebuilt from the ground up as a Netflix-style living-room app. It brings a new look, smooth D-pad navigation, trailers on focus, a new player, casting from the web app, automatic updates, a HomeFlix row on your TV's home screen, and recommendations that learn from what you watch and rate.

**Download:** `homeflix-tv-v5.apk` (5.15.3, versionCode 32, 12.0 MB)
SHA-256: `9f2826ea0436b2b745071c4595389131c13000c770bd33a402b936ed0309defd`

**Requires:**
- Android TV or Google TV, Android 6.0 or newer. The home-screen row needs Android 8.0 or newer.
- The HomeFlix server **[homeflix-wifi `release/v5.5`](https://github.com/azad25/homeflix-wifi/tree/release/v5.5)**.

---

## New since v5.11.0

- **5.15.3:** the Home billboard plays each title's own preview clip (it used to repeat the first one for every title).

- **Live TV, like a cable box:** channel numbers, ▲▼ zapping, an info banner, a mini guide (OK) and a full guide with live preview (◀), favorites, and Back to your last channel. YouTube-only and offline channels are hidden. It's light on 2 GB TVs.
- **Coming Soon:** the most anticipated upcoming movies and series with their trailers, on the top menu and as Home rows. **Notify Me** has your server download a title on release day. The full-screen screen has a premiere-calendar design.
- **HomeFlix on your TV's home screen:** a HomeFlix row with Continue Watching, new arrivals and picks for you.
- **Smarter pages:** no title appears twice on a page. Top 10 is ranked by real plays. New rows: Top Picks, Because You Liked/Watched, New for You and Hidden Gems. Genres are ordered by your taste.
- **Full-screen movie pages:** the trailer fills the TV and the text fades while it plays.
- **Show previews:** each show previews its own newest uploaded episode.
- **Redesigned Notifications:** All / Movies / Shows filters, day groups and a large artwork card.
- **New logo:** the HomeFlix rounded play button, matching the web app.
- **Keyboard:** Esc works like Back everywhere.
- **Smaller app:** about 25 % smaller download (12.6 MB).

## Highlights

- **New Netflix-style design:** a top menu (Search, Home, Shows, Movies, My HomeFlix), a full-screen billboard, and rows of titles.
- **Trailers on focus:** rest on a title and its tile widens and plays the trailer. The Home billboard rotates through this year's newest additions with their trailers.
- **Smooth navigation:** every press moves the row in one step, with no jumping back and forth. Holding a direction glides steadily.
- **New player:** Netflix-style controls and pause screen, reliable resume, and a Next Episode card.
- **HomeFlix on your TV's home screen:** Continue Watching, new arrivals and picks for you, right on the launcher.
- **Rate and My List:** Not for me / I like this / Love this! on every movie and show. Your ratings, your list and your watch history shape your recommendations.
- **Smarter pages, no repeats:** Home, Movies and Shows never show the same title in two rows.
  - **Top 10** is ranked by what's actually being watched this week.
  - Rows like Top Picks for You, Because You Liked…, New for You and Hidden Gems for You are personalised for you.
- **Live TV, like a cable box:** free-to-air channels from your HomeFlix server.
  - Channel numbers, ▲▼ zapping in about a second, and an info banner on every change.
  - OK opens a mini guide; ◀ opens a full guide with live previews.
  - Favorites, and Back to your last channel.
- **Coming Soon:** the most anticipated upcoming movies and series, with their trailers.
  - **Notify Me** has your server download the title on release day.
- **Cast from the web app:** play any title on the TV from the HomeFlix website.
- **Automatic updates:** the app updates itself from your HomeFlix server. This is the last APK you need to install by hand.

## What's new

### Browsing
- **New logo:** the HomeFlix rounded play button (white on black), matching the web app. It's used on the launcher icon, the TV banner and the **Home**Flix wordmark in the top menu.
- **Coming Soon** is on the top menu, and **Live TV** is in the profile menu.
- A new top menu that hides while you browse rows, plus a notification bell and a profile menu (Notifications, Settings, About, Refresh, Exit).
- Movie, show, notification, settings and about pages also show the top menu.
- Each tab remembers where you were. Back goes from the page to the menu, then to Home, then exits.
- **Home:** Continue Watching, My List, **Top Picks for You**, **Top 10 on HomeFlix This Week**, New on HomeFlix (movies and shows), **Because You Watched / Liked …**, **New for You**, **More with <actor>**, **Hidden Gems for You**, Trending, TV Shows, Critically Acclaimed and genre rows.
- **Movies and Shows pages:** each gets its own Top Picks, its own Top 10, recently added titles, Because You Watched rows, and genre rows. "More Movies" / "More Shows" at the end holds everything not shown above.
- **No title in two rows:** Continue Watching, My List and Top 10 keep their full lists. Every other row only shows titles not already on the page.
- **Top 10 from real plays:**
  - It counts how far each viewer got, how recently, and how many episodes they binged. Episodes count towards their show.
  - This week's plays rank first, then this month's.
  - Quiet servers fill the remaining places with the best rated and newest titles.
- **Genre rows:** your favourite genres come first.
- **Small libraries:** they still get several short rows instead of an empty page.
- Continue Watching shows on Home, Movies and Shows. Show cards say where you are, e.g. "S1:E2 · Pilot · 20m left".
- Pages open instantly from the last saved copy, then refresh quietly in the background.

### Movie and show pages
- **Movie pages fill the screen:** the trailer plays behind the title, and More Like This is one ▼ away.
  - After a few seconds without a key press, the text and menu fade so only the trailer shows. Any key brings them back.
- **Show previews:** show pages, show tiles and the Shows billboard preview the show's newest uploaded episode.
  - **Fixed:** shows no longer play another show's video. The server was mixing in an unrelated episode, and a late-arriving preview could keep the previous clip.
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

### Coming Soon
- **Home rows:** "Coming Soon: Movies" and "Coming Soon: Series" show wide cards.
  - Each card has the title, the date ("In 12 days" / "Dec 18" / "Feb 2027"), a "Season 3" / "New Series" chip, and a bell when Notify Me is on.
  - Resting on a card plays its trailer muted inside the card.
- **Full screen:** open a card, or **Coming Soon** on the top menu.
  - The layout reads like a premiere calendar: a "FEB 12" date tile, a MOVIE/SERIES chip and a "Season 2" chip, a gold countdown, and genre chips.
  - The top row shows "#1 in Movies", "Up next" and "1 / 16".
  - The title strip is larger, with each title's date.
  - Trailers play with sound, one after another.
  - An info overlay fades after a few seconds: title, release label, full date and countdown, studio · genres, cast and overview. Any key brings it back.
- **Remote:**
  - **◀ ▶** previous / next title.
  - **▲ ▼** move between Notify Me, Details and the strip of titles.
  - **OK** presses the focused button.
  - **Play/Pause** pauses.
  - **Back** closes the overlay, then the screen.
- **Notify Me:** shows "We'll Notify You ✓" right away. If the server can't be reached it goes back, and afterwards it shows the server's status (scheduled, queued, downloading).
- **Title pages:** a show getting a new season gets "Season 3 · In 20 days Trailer" and Notify Me buttons.
- **No trailer yet:** the backdrop shows for a few seconds before the next title, so there's no YouTube fallback.

### Live TV
- **Opening it:** use **Live TV** on the top menu, or the **Live TV** row on Home (favorites, then channels you watched recently). It opens full screen on your last channel, or channel 1.
- **While watching:** only the video is on screen.
  - Every channel change shows a banner for about 3 s: channel number, logo, name, ● LIVE, category · quality · country, and the clock.
  - While a channel loads you see its logo, never the previous channel's picture.
- **Remote:**
  - **▲ / CH+** previous channel; **▼ / CH-** next channel. Holding a key zaps quickly; the channel tunes once you stop.
  - **0-9** enters a channel number (`1 2 _`). It tunes after a short pause, or on OK.
  - **OK** opens the mini guide: a channel list over the video, with ● NOW on the channel you're watching.
  - **◀ / GUIDE** opens the full guide:
    - Groups: Favorites, All, each country with its flag, each category.
    - The focused channel plays muted in a preview window.
  - **▶ / INFO / MENU** shows channel info with **Add to Favorites**.
  - **Back** closes a guide, or returns to your last channel. **Back twice** leaves Live TV.
- **When a channel won't play:** the app tries the server's next stream, then shows "isn't available right now". It never jumps to another channel by itself.
- **Only channels that play:** channels the server can only offer as a YouTube stream, and channels its health check found offline, are left out of the lineup. Channel numbers stay the same as on the server.
- **Light on 2 GB TVs:**
  - Live buffers are capped at about 15 s and 12 MB.
  - Video is capped at 1080p, so 4K variants are never decoded.
  - The picture is drawn by the display hardware.
  - The channel list is loaded once and shared, refreshed every 10 minutes.
- **Under the hood:**
  - One player for the whole session.
  - A small start buffer, with live playback about 12 s behind the live edge.
  - Neighbouring channels' streams are looked up in advance.
- **Requires** Live TV to be turned on on the server (web app: Settings → Live TV). Without it, Live TV is hidden.

### Cast from the web app
- Pick the TV in the HomeFlix website's cast menu to play, pause, seek, change volume or subtitles.
- The TV stays ready to cast in the background and after a restart.
- **Settings:** turn casting on or off, rename the TV, and allow HomeFlix to open from a cast while another app is on screen.

### Updates
- The app checks your server for a new version at launch, when you return to it, and when the server announces one.
- An **Update available** dialog appears; one press installs it. Settings has **Check for updates** too.

### Notifications, Settings and About
- **Notifications:**
  - **All / Movies / Shows** filters.
  - A list grouped by Today, Yesterday, This Week and Earlier, with movie/show badges and NEW tags.
  - A large artwork card with the focused item's details, and a red dot on the bell for unread items.
- Settings covers casting, the **server address** (editable, with a connection check), app updates and app info. Every row is reachable with the remote.
- About HomeFlix credits Ferdous Azad, developer of HomeFlix. It includes contact details, GitHub, and a Buy Me a Coffee link: https://buymeacoffee.com/ferdousazad

### Keyboard
- **Esc** works like Back everywhere in the app.

### Performance and stability
- **Smaller app:** about 25 % smaller (12.6 MB instead of 16.7 MB). The code Android has to optimise and map is down from 54 MB to 32 MB, so installs and updates are faster and less storage is used. Only unused icons were removed; nothing else in the app changed.
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
