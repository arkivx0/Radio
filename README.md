# Radio — a fork of RadioDroid

**Radio** is an unofficial, personal fork of [RadioDroid](https://github.com/segler-alex/RadioDroid)
by segler-alex and contributors. It installs alongside upstream (`applicationId io.github.arkivx0.radio`).
All credit for the app itself goes to the RadioDroid project. Licensed GPL-3.0 like upstream.

## What this fork adds
- **Incoming-call crash fix**: audio-focus loss (e.g. a spam call) no longer kills the service on Android 12+;
  playback pauses cleanly and resumes after the call (foreground-service restart guarded, mediaPlayback FGS type).
- **11 skins** with swatch previews in Settings → theme: Light, Dark, Omarchy / Tokyo Night (default),
  AMOLED Black, Gruvbox Dark, Catppuccin Mocha, Nord, Dracula, Enclave, Terminal (green phosphor), Vintage (tube radio).
  Bundled OFL fonts: VT323 (Terminal, Enclave headings) and Limelight (Vintage headings).
- **Time-shift**: skip back 5 s (repeatable, up to ~5 min) and **Go live**, with a behind-live indicator
  in the player and notification.
- **Lock-screen / media controls**: MediaStyle notification on a public channel, with Back 5 s, Go live and Record
  as media-session custom actions (Android 13+ media controls).
- **Scheduled recordings**: pick station, start time, duration, once or repeat days (⋮ → Scheduled recordings).
- **Android 13/14 fixes**: recording works without legacy storage permission (files in `Music/Radio`),
  exact-alarm fallback, notification-permission prompt, unique provider authorities, new app icon.

## Install
Download `Radio.apk` from the [Releases](../../releases) page and sideload it (allow "install unknown apps").
It does not replace RadioDroid. Builds are the `free` flavor (no Google Play services, no analytics).

## Known limits
- HLS streams cannot be rewound (time-shift works for regular Icecast/Shoutcast streams).
- Scheduled recordings play the stream aloud while recording and take audio focus.
- Time-shift positions are estimated from the stream bitrate; jumps can cause a short gap.

---

<div align=center><img src="https://user-images.githubusercontent.com/38007519/39290477-e1bac174-4959-11e8-9229-836355a74a7c.png"/></div>

# <div align=center>Android radio streaming app based on <a href="http://www.radio-browser.info" target="_blank">Radio Browser</a></div>

<div align=center><a href="https://f-droid.org/repository/browse/?fdid=net.programmierecke.radiodroid2" target="_blank">
<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80"/></a>
<a href="https://play.google.com/store/apps/details?id=net.programmierecke.radiodroid2" target="_blank">
<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Get it on Google Play" height="80"/></a></div>



### <div align=center>Please add issues or feature requests on github under issues.</div>
### <div align=center>Download releases here https://github.com/segler-alex/RadioDroid/releases</div>

<div align=center><a href="http://www.gnu.org/licenses/gpl-3.0.html" target="_blank">
<img src="http://www.gnu.org/graphics/gplv3-with-text-84x42.png" alt="GPLv3 Logo"/></a></div>
