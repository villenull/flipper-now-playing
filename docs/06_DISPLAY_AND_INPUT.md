# 06 — Display, typography, time, and physical input

## 6.1 Rendering intent and hard constraints
Implement a true **128 × 64, one-bit** UI. All coordinates below are integer pixels with origin at the top-left. The amber backlight is a hardware property; graphics are black/white, not RGB artwork. Use the approved render as a hierarchy reference, not as a claim that its displayed text sizes and hardware proportions are physically exact. [S25]

Main screen: note icon; title; artist; album; time/progress; previous, central play/pause, next; + above and − below center. No permanently visible toolbar or extra screen title wasting the metadata rows. Do not discard album to make room for a giant controller diagram.

## 6.2 Layout boxes
Coordinates are inclusive bounding boxes unless stated otherwise. The agent may adjust a baseline by a pixel for the actual pinned font metrics, but must preserve non-overlap and the required hierarchy.

| Element | Box / geometry | Treatment |
|---|---|---|
| Music note tile | x=2…17, y=2…17 | One-pixel outline and simple native one-bit note. |
| Title row | x=22…120, y=0…10 | Bold/native primary font if measured to fit vertically; clipped scrolling. |
| Connection marker | x=124…127, y=2…5 | Small solid/hollow indicator; status text appears only on state screens/overlays. |
| Artist row | x=22…126, y=11…20 | Compact font; independently measured. |
| Album row | x=2…126, y=21…29 | Compact font, full-width viewport. |
| Time/progress row | x=2…125, y=30…38 | Left elapsed, right total/remaining, progress frame in the measured gap. |
| Volume + | centered x=64, y=40…44 | Five-pixel symbol. |
| Previous | x=29…39, y=49…56 | Two left triangles or native skip-style icon matching the concept. |
| Center control | center=(64,53), radius=6 | Circle outline, pause bars while playing / play triangle otherwise. |
| Next | x=89…99, y=49…56 | Mirrored next icon. |
| Volume − | x=62…66, y=62 | Five-pixel horizontal line. |

Set the title baseline only after measuring the font ascent/descent; initial title baseline around 8. Artist baseline around 18; album around 27; time baseline around 36. The pinned source exposes native font metrics including descenders; use them, then verify actual glyph bounds. [S25] Do not assume a font is 6 pixels high just because its name says “small”. If the current native compact font exceeds a box, choose another exported native font and document exact metrics; do not bundle font files from the environment.

Measure text with actual Canvas/font metrics, not character count. A long album name will scroll; the entire “Random Access Memories” string is not required to fit simultaneously in a tiny font exactly as in the large concept image. Letter readability has priority over matching the render's apparent density.

## 6.3 Clipping and scrolling
Use verified exported GUI helpers or an explicitly bounded rendering method. Do not invent `canvas_set_clip()` if it is not exported. A production draw method must clip each scrolling row to its box so text cannot overwrite the note icon or connection marker.

Preferred low-risk approach: use the actual supported scrollable-text element when it fits the desired bounds. An alternative using only native primitives is to draw each text row, erase pixels outside its horizontal viewport within that row's verified vertical band, then draw the note tile and connection marker afterward. This ordering restores the decorations instead of requiring a nonexistent clipping API. Do not erase neighboring metadata rows. A bounded row bitmap is another option only when its font/rendering dependency is actually available. A host adapter can share layout logic, but it must not become the only place where clipping works.

Scroll only overflowing fields. Hold the initial position 1 second, move approximately 12 pixels/second, hold the end 1 second, then return/repeat. Avoid all rows moving together: title gets first priority, album second, artist third; only one overflowing row scrolls at a time in the default cycle. Reset that row's scroll state when its string changes. Do not reset it on every 10-second playback refresh.

Pause scrolling during a temporary error overlay or while a system pairing prompt owns the screen. Long-title tests must prove that every visible pixel stays inside its viewport. The helper's original Unicode text remains available in the phone preview; the Flipper's normalized fallback must not display broken multibyte sequences.

## 6.4 Progress row and formatting
Format elapsed as `m:ss`, then `h:mm:ss` for long content. Format remaining as `-m:ss` or `-h:mm:ss`, clamped to zero at the end. Use floor division for elapsed and a defined remaining policy: `ceil(max(duration-position,0)/1000)` so a subsecond remainder does not show zero early. Total duration uses floor milliseconds-to-seconds for conventional display. These formatting choices are deterministic test cases.

Compute left and right label widths, then allocate the progress bar between them with at least 2 pixels of gap on each side. Typical labels `1:42` and `4:08` leave a useful center bar. For long time strings, reduce bar width rather than overlap. If the available bar width is under 8 pixels, show only the two aligned time labels for that unusually long duration; keep the ordinary-song layout unchanged.

The progress bar is a one-pixel frame with fill proportional to valid position/duration. Use 64-bit integer arithmetic for `fill = inner_width * position / duration`, clamp to width, and never divide by zero. Unknown duration: right label `--:--`, empty/indeterminate-neutral bar with no fabricated percentage. Unknown position: left label `--:--`, no progression. Missing both: both placeholders.

The center icon represents the available play/pause action: pause bars while confirmed PLAYING, play triangle while paused/stopped. Buffering may use a small neutral marker or a temporary “Buffering” overlay; do not show fabricated advancing seconds. When actions are unsupported, visually reduce the corresponding icon prominence without reassigning its key.

## 6.5 Local clock and freshness
When a valid snapshot/state is applied, capture a local monotonic receipt tick and the transmitted position/speed. Advance from that anchor only while confirmed PLAYING, position valid, and peer fresh. Use unsigned wrap-safe tick differences or an extended monotonic counter; do not assume the device tick never wraps.

At each draw, compute rather than accumulate one second into stored position. This prevents timer jitter from causing cumulative error. Clamp at zero and at a known duration. When a seek/pause/state packet arrives, replace the anchor immediately. Do not smooth a backward seek into a slow animation that shows the wrong playback position.

On known disconnect, freeze immediately. If the BLE link appears connected but no valid phone frames arrive for 35 seconds, freeze and show stale/reconnecting. On reconnect, the preserved stale metadata is never sufficient to enable controls; await a new handshake and snapshot. Wall-clock changes on Android must have no effect on elapsed time.

Target refresh is up to 10 Hz while a marquee or key highlight is animating; use roughly 1 Hz for ordinary elapsed-time changes. Suppress redraws when a paused static screen has not changed. Respect normal backlight policy. Do not force the screen backlight continuously just because the approved studio render is brightly lit.

## 6.6 State screens and overlays
| Situation | Required visible behavior |
|---|---|
| Starting / no phone | “Waiting for phone” and a compact long-Back exit hint. No fake song. |
| System pairing | Let the firmware's authentic pairing UI work; do not draw a fake verification code. |
| Link connected, synchronizing | “Reading player…” until a valid snapshot. |
| No selected session | “Open Apple Music” / “Play a song” with connection indicator. |
| Missing field | Unknown track / Unknown artist / Album unavailable for that row only. |
| Paused | Keep metadata/progress; freeze time; show play icon. |
| Buffering | Keep last metadata; freeze; show brief buffering status. |
| Disconnected | Preserve last track briefly with “Reconnecting”; freeze; disable commands. |
| Permission revoked | “Phone needs access” and a repair instruction in the helper. |
| Unsupported/failed command | Short, nonblocking overlay; do not switch screens permanently. |
| Restoring default profile | “Closing…”; no accepting fresh music commands. |

Transient messages can replace the lower controls area for about 1.2 seconds; they must not destroy the model. Short Back may briefly show “Hold Back to exit” while consuming the event; it must not act as Stop or toggle the time mode.

## 6.7 Input reducer
The existing media view sends actions on press and consumes short Back; long Back permits exit. Preserve that user-level behavior, not necessarily identical HID bytes because this app uses its own transport. [S08]

| Key | Press | Release | Short | Long / held behavior |
|---|---|---|---|---|
| Up | One volume-up command; highlight. | Clear highlight and repeat state. | No second command. | Start bounded repeat after 400 ms, every 125 ms. |
| Down | One volume-down command; highlight. | Clear highlight and repeat state. | No second command. | Same repeat schedule. |
| Left | One previous command; highlight. | Clear highlight. | No second command. | No seek and no repeat. |
| Right | One next command; highlight. | Clear highlight. | No second command. | No seek and no repeat. |
| OK | One play/pause command; highlight. | Clear highlight. | No second command. | No repeat. |
| Back | No media action. | Clear transient hold state. | Consume; optionally show exit hint. | On the platform's long-press event, start orderly exit. |

Use either framework repeat events **or** the custom monotonic repeat timer, never both. The specified implementation uses a custom 400/125-ms timer and ignores framework Repeat for media dispatch. Framework Long is still used for Back navigation. Events must not produce two commands because Press and Short both occurred.

For multi-key input, do not permit contradictory simultaneous volume-repeat streams. A newly pressed opposite volume key cancels the previous repeat until the physical state is reconciled. Ignore duplicate press events for an already-held key. Clear all held flags on readiness loss, application focus loss, transport reset, and shutdown. Include a defensive repeat-duration cap (10 seconds per uninterrupted press) to stop a missed release; a fresh physical press can start again.

Do not accumulate repeat events during a blocked worker. Each tick evaluates whether one repeat is due, issues at most one, and moves the next deadline forward from current time. Eight repeats per second is the maximum steady-state rate, not a backlog to catch up.

## 6.8 Required visual evidence
Generate a pixel-exact 128 × 64 screenshot from the production rendering path for: approved fixture, paused, long title/album, missing duration, no session, reconnecting, and a command error. Save a native-resolution bitmap/PNG plus a nearest-neighbor enlarged preview. Label any simulator image as simulated; a device screenshot requires an actual device capture.

Pixel tests check bounds, time-label overlap, icon boxes, scrolling clipping, and marker positions. Visual inspection checks readability. A pixel-perfect test that blesses unreadable text is insufficient. Do not include proprietary font files in the release or use the supplied concept image as the runtime screen background.
