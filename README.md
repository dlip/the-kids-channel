# <img src="artwork/app-icon-party-hat.png" alt="The Kids Channel party-hat icon" width="64" align="absmiddle"> The Kids Channel

The Kids Channel is a simple player for video files stored locally on an
Android phone, tablet, SD card, or connected storage. It does not provide,
stream, or download videos. You choose folders containing your own video
files, and each folder becomes a channel for children to watch.

There is no timeline, seeking, playlist editing, or next-video button. Videos
play in order, loop continuously, and resume where they left off.

![The Kids Channel paused with the channel title and pause button visible](docs/player-controls.png)

## Download and install

The Kids Channel requires Android 8.0 or newer.

1. Open the [latest release](https://github.com/dlip/the-kids-channel/releases/latest).
2. Under **Assets**, download the Standard APK. It is the smaller download and
   is recommended for most devices.
3. Open the downloaded APK on your phone or tablet.
4. If Android blocks the installation, allow your browser or file manager to
   install unknown apps, then try again.

If videos have sound but show a black screen, or a video format will not play,
install the larger APK with `vlc` in its filename. The VLC build supports more
devices and video formats. It can be installed over the Standard build without
losing folders or saved playback positions.

The Standard build includes a software audio decoder for formats such as AC-3
when Android cannot decode them. Text subtitles use white lettering with a
black outline and a transparent background.

Future versions can be installed over the current app without removing its
folders or saved playback positions.

## Set up channels

1. Open the app and tap **Add root folder**.
2. Select a folder containing one subfolder for each channel.
3. Swipe up or down to change channels.

For example:

```text
Kids Videos/
├── Songs/
│   ├── 01 Hello.mp4
│   └── More Songs/
│       └── 02 Goodbye.mp4
└── Stories/
    ├── 01 The Bear.mp4
    └── 02 The Moon.mp4
```

`Songs` and `Stories` are channels. Nested folders such as `More Songs` remain
part of their parent channel. Videos are played in natural filename order, so
number prefixes can be used to control their order.

## Controls

- Swipe up to move to the next channel and swipe down to move to the previous
  channel, including while a channel is loading. Channels wrap around at either end.
- Tap anywhere to pause or resume playback.
- While paused, hold the channel title in the upper left for two seconds to
  open Settings.

Settings includes automatic audio normalization, enabled by default, to keep
quiet and loud videos at a more consistent volume. Default audio and subtitle
languages are English and can be changed in Settings. Subtitles are enabled by
default; the Subtitles switch turns them off, including forced subtitles.
The subtitle font-size slider adjusts text size from 50% to 200%, with 100% as
normal. Picture-based subtitle tracks keep their original size; styled ASS
subtitles also retain their embedded size in the VLC build. Language
preferences select existing tracks; they do not translate videos.

Open Stats in Settings to see total watch time grouped by root folder and
channel, including disabled roots. Watch time recorded before channel tracking
is listed separately under its root. Each root's switch in Settings temporarily
disables its channels without removing the folder or playback positions.

The app remembers the current video and playback position separately for each
channel. When it reaches the end of a channel, it starts again from the
beginning.

When channels are discovered, the app generates missing preview images from
the first frame of their videos in the background. Playback refreshes these
previews as you watch.

Developer setup, building, deployment, and release instructions are in
[DEVELOPMENT.md](DEVELOPMENT.md). See [CHANGELOG.md](CHANGELOG.md) for the
version history.
