# Radio Arkiv — a fork of RadioDroid

**Radio Arkiv** (formerly "Radio") is an unofficial fork of [RadioDroid](https://github.com/segler-alex/RadioDroid)
by segler-alex and contributors, an Android internet-radio app built on the free
[Radio Browser](https://www.radio-browser.info) station directory.
It installs alongside upstream (`applicationId io.github.arkivx0.radio`).
All credit for the app itself goes to the RadioDroid project. Licensed GPL-3.0 like upstream.

<p align="center">
  <img src="docs/screenshots/stations.png" width="250" alt="Station list" />
  <img src="docs/screenshots/player.png" width="250" alt="Player with codec line and -5s / Live controls" />
  <img src="docs/screenshots/recordings.png" width="250" alt="Player with the Recordings tab" />
  <img src="docs/screenshots/favorite.png" width="250" alt="Player with a favourite station" />
</p>
<p align="center"><em>Stations shown are a random selection picked from the public directory for screenshots and testing, not an endorsement.</em></p>

## What this fork adds
- **Incoming-call crash fix**: audio-focus loss (e.g. a call) no longer kills the service on Android 12+;
  playback pauses cleanly and resumes after the call.
- **11 skins** with swatch previews in Settings → theme: Omarchy / Tokyo Night (default), AMOLED Black,
  Gruvbox Dark, Catppuccin Mocha, Nord, Dracula, Enclave, Terminal (green phosphor), Vintage (tube radio),
  Light and Dark. Bundled OFL fonts: VT323 and Limelight.
- **Time-shift**: skip back 5 s (repeatable, up to ~5 min) and **Go live**, with a behind-live indicator.
- **Lock-screen / media controls**: media notification with Back 5 s, Go live and Record actions.
- **Scheduled recordings**: pick station, start time, duration, once or on repeat days.
- **Open audio files with Radio Arkiv**: recordings (`Music/Radio`) and other audio files can be opened with Radio Arkiv
  and play in its player with seeking, pause/resume and lock-screen controls. In the recordings list, tap to
  play in Radio Arkiv, long-press to open with another app.
- **Open file**: ⋮ → *Open audio file…* picks any audio file with the system file picker (no storage
  permission needed) and plays it in Radio Arkiv.
- **Codec info line**: the full player shows the decoded source format, e.g. `AAC-LC · 44.1 kHz · stereo`.
- **Android 13/14 fixes**: recording without legacy storage permission, exact-alarm fallback,
  notification-permission prompt, unique provider authorities, working sleep timer and alarms.
- **Fixes for upstream issues**:
  [#1260](https://github.com/segler-alex/RadioDroid/issues/1260) Wi-Fi playback blocked when mobile data is on,
  [#1261](https://github.com/segler-alex/RadioDroid/issues/1261) favourite star not updating,
  [#1241](https://github.com/segler-alex/RadioDroid/issues/1241) crash searching short names like "rad",
  [#1222](https://github.com/segler-alex/RadioDroid/issues/1222) silent failure adding an alarm,
  [#349](https://github.com/segler-alex/RadioDroid/issues/349) stream format details.
- **About screen** works again and shows this fork's info.

## Install
Download `RadioArkiv.apk` from the [Releases](../../releases) page and sideload it (allow "install unknown apps").
It does not replace RadioDroid. Builds are the `free` flavor (no proprietary services, no analytics).

## Known limits
- HLS streams cannot be rewound (time-shift works for regular Icecast/Shoutcast streams).
- Scheduled recordings play the stream aloud while recording and take audio focus.
- Time-shift positions are estimated from the stream bitrate; jumps can cause a short gap.

## Credits and license
Based on [RadioDroid](https://github.com/segler-alex/RadioDroid) by segler-alex and contributors.
Station data from [Radio Browser](https://www.radio-browser.info).
Free software under the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.html).
