# Changelog

Notable changes to The Kids Channel are recorded here.

## [Unreleased]

## [1.10.8] - 2026-10-06

### Fixed

- Decode AC-3 and other unsupported audio formats in software in the Standard
  build, restoring English audio on devices without an Android AC-3 decoder.
- Show Standard-build text subtitles with a transparent background and a black
  outline instead of black boxes, including styled text subtitles.

## [1.10.7] - 2026-10-06

### Added

- Default audio and subtitle language settings, both initially English, with a
  subtitles switch that also disables forced subtitles. Preferences are saved
  and applied to current and prepared channels in both playback builds.
- Separate Audio and Subtitles settings sections, with a saved subtitle font-size
  control from 50% to 200%. Picture subtitles keep their original size; styled
  ASS subtitles also retain their embedded size in VLC.

### Fixed

- Retain prepared neighboring players when switching to a channel that has not
  finished loading, so rapid swipes do not discard warm videos.
- Save outgoing screenshots asynchronously after pausing instead of delaying
  channel handoff, and avoid reading live video frames during playback.
- Show a prepared channel directly during handoff and update native video
  positions without recomposing every player during the scroll animation.
- Preserve the paused image when returning from the background and reset frame
  readiness when a video or its surface changes, preventing stale screenshots
  and premature loading-indicator dismissal.

## [1.10.6] - 2026-10-03

### Fixed

- Ignore repeated VLC texture updates for the same frame so a frozen image does
  not prevent playback recovery after channel switching.

- Keep neighboring VLC players silent from startup through preparation and
  channel handoff, while retaining prepared audio buffers for quick switching.

- Use VLC's OpenSL ES audio output to prevent audio stutters caused by AudioTrack
  flushing buffers and inserting silence when prepared channels resume.

- Release discarded VLC decoders off the UI thread and avoid stopping playback
  when creating a video view, so channel switches and the loading spinner stay
  responsive during decoder cleanup.

- Escalate VLC frame-stall recovery from pause/resume to a decoder reload at the
  current position, with software decoding if hardware recovery keeps failing.
  Remember software fallback for the affected video during the app session.

- Clear the entry screenshot when fresh playback frames arrive, including rapid
  switches to a VLC neighbor that is already playing during preparation.

- Reveal resumed video after frame updates without waiting for VLC position
  reports, and retain prepared neighbors throughout the handoff.

- Wait for VLC to confirm the saved playback position before accepting a prepared
  frame, and allow pause to finish before updating the outgoing preview.

- Keep the incoming paused frame over the channel handoff until resumed playback
  delivers fresh frame updates.

- Show the neighboring channel's prepared video frame during a swipe, with its
  saved screenshot as a fallback while video is not ready.

## [1.10.5] - 2026-10-03

### Fixed

- Stop periodic VLC screenshot capture during playback; refresh previews while
  paused or leaving a channel instead.
- Recover stalled VLC video even when playback-position updates arrive less
  frequently than the recovery checks.

## [1.10.4] - 2026-10-03

### Fixed

- Keep a touch placed during a channel transition available for the next swipe
  once the new channel is selected.
- Allow Android's normal screen timeout while playback is paused in both builds.
- Move the current channel's live video with the swipe using native view
  translation, keeping playback visible without a Compose video layer.

## [1.10.3] - 2026-10-02

### Fixed

- Keep live video outside the translated swipe layer and animate screenshots
  over it, preventing rapid loading-time swipes from stranding a TextureView.
- Allow VLC stalled-frame recovery before the first displayed frame and without
  relying on delayed buffering events.

## [1.10.2] - 2026-10-02

### Fixed

- Read folder entries and their metadata in one query per directory to reduce
  channel-loading delays for large playlists on older devices.

## [1.10.1] - 2026-10-02

### Fixed

- Switch prepared video visibility on the native views instead of a Compose
  alpha layer to avoid stale TextureView frames on older devices.
- Pause VLC neighbours once after warming, and automatically resume stalled
  video rendering when playback time advances without new frames.

## [1.10.0] - 2026-10-01

### Added

- Keep the next and previous channels prepared and paused at their saved positions
  for faster switching in both playback builds. Limit the pool to three players,
  mute background preparation, and exclude it from watch time.

### Fixed

- Stay in settings after adding a root folder, including while its channels are
  scanned, and wait for the user to return to the player before starting video.
- Create a fresh Standard player for each channel so the previous decoder cannot
  render a retained frame into the incoming channel's video view.
- Move loading screenshots with their outgoing channel during another swipe and
  reset preview fade animations when the selected channel changes.
- Composite Standard video in a TextureView and wait for its first displayed
  frame before removing the incoming screenshot.

## [1.9.8] - 2026-10-01

### Fixed

- Give Standard playback a fresh video view on channel changes and discard
  delayed callbacks and frame captures from the previous channel.
- Start the loading spinner's 200 ms delay when the vertical transition ends,
  and show a thinner blue spinner in the centre above the incoming screenshot.

## [1.9.7] - 2026-10-01

### Fixed

- Clear the previous channel's paused state when switching channels and keep
  the pause button hidden throughout the channel transition.

## [1.9.6] - 2026-10-01

### Added

- Show a loading spinner over the channel screenshot when waiting for video
  takes longer than 200 ms, in both player builds.

## [1.9.5] - 2026-10-01

### Fixed

- Keep VLC's channel screenshot visible until the video texture receives a
  frame, instead of hiding it on playback-time events or a fixed delay.

## [1.9.4] - 2026-10-01

### Fixed

- Activate normalization on VLC's actual player audio output instead of
  relying on startup options that LibVLC overrides.

## [1.9.3] - 2026-10-01

### Fixed

- Apply stronger boosting to very quiet videos while retaining compression
  for loud audio, and stop treating low-volume recordings as silence in the
  Standard build.

## [1.9.2] - 2026-10-01

### Fixed

- Save the current video and position before opening Settings and resume from
  that position when returning to playback.

## [1.9.1] - 2026-10-01

### Fixed

- Boost quiet audio as well as reduce loud passages in the VLC build, and
  widen the Standard build's automatic volume adjustment range.

## [1.9.0] - 2026-10-01

### Fixed

- Allow swiping to another channel while the current channel is still loading.

### Changed

- Require paused playback before holding the channel title to open Settings.
- Show the channel title only while paused or changing channels.
- Set the vertical channel transition to 150 ms and lower the swipe threshold
  to 10 percent of screen height.
- Fill the Settings hold indicator with red from both edges toward the finger's
  press position.
- Open Settings by holding the existing upper-left channel title for two
  seconds. Keep the pause icon in the center when playback is paused.

## [1.8.1] - 2026-10-01

### Fixed

- Give VLC a fresh video view on channel changes to prevent retained frames
  flashing over the next channel, and discard delayed events and preview
  captures from the previous channel.

## [1.8.0] - 2026-10-01

### Added

- Generate missing channel previews from the first frame of a video when
  channels are discovered.
- Track active watch time by root folder and channel on a Stats page in Settings.
- Temporarily disable root folders without removing their access or playback
  positions.

### Changed

- Replace the Paused label with a pause icon and double the control's size,
  using a rounded square shape. Hold it for five seconds to open Settings.

## [1.7.0] - 2026-09-30

### Added

- Replaced playback buttons with gestures: tap to pause or resume, swipe to
  change channels, and hold the Paused label for five seconds to open Settings.
- Added an interactive channel transition that follows the swipe and returns
  to the current channel when less than 20 percent of the screen is crossed.

### Changed

- Kept the current video playing during a swipe and used one saved preview per
  channel for the incoming channel.

### Fixed

- Corrected channel ordering and wraparound during repeated rapid swipes.
- Prevented taps during a swipe from pausing playback.
- Prevented stale previews and transition cleanup from covering or interrupting
  the active video.
- Kept playback visible when the next video starts within the same channel.

## [1.6.0] - 2026-09-30

### Added

- Animated channel changes with the current video frame sliding out and the
  next channel's saved preview sliding in when available.

### Fixed

- Kept the next channel's saved preview visible until video frames are ready,
  then faded it into playback over 100 ms to soften black flashes.

## [1.5.1] - 2026-09-30

### Fixed

- Preserved channel playback positions when switching channels quickly,
  including while VLC is still starting or waiting to seek.

## [1.5.0] - 2026-09-29

### Added

- Optional automatic audio normalization, enabled by default, for more
  consistent volume between videos and channels.
- Changelog-backed GitHub release notes with validation for missing or empty
  version sections.

## [1.4.0] - 2026-09-29

### Added

- Saved video-frame previews that appear while a channel resumes or loads.

### Fixed

- Prevented previews from being saved against the wrong channel during rapid
  channel changes.
- Removed preview movement and fading when live video becomes ready.

## [1.3.3] - 2026-09-29

### Fixed

- Restored VLC video output after leaving the app and returning to it.

## [1.3.2] - 2026-09-29

### Changed

- Redesigned the launcher icon as a rounded old CRT television.

## [1.3.1] - 2026-09-29

### Changed

- Refined the launcher icon's rounded styling.

## [1.3.0] - 2026-09-29

### Added

- Added a VLC-based build for devices and video formats that do not render
  correctly with the Standard player.
- Published separate signed Standard and VLC APKs in each release.

## [1.2.1] - 2026-09-29

### Fixed

- Improved Standard-player video rendering compatibility on Huawei Android 9
  devices.

## [1.2.0] - 2026-09-29

### Added

- Added pause and resume playback controls.
- Added the party-hat launcher icon.

### Changed

- Combined pause and protected Settings access into one control.
- Reworked the documentation around local video files and app installation.

## [1.1.0] - 2026-09-28

### Added

- Added folder-based channels with nested-folder playback and saved progress.
- Added looping, immersive fullscreen playback, and auto-hiding controls.
- Added protected Settings access by holding the Settings control for five
  seconds.
- Added signed Android releases through GitHub Actions.
