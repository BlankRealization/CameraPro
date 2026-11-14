# Camera Pro V1 — Phase 5a (Poses)

## What Phase 5a adds

- **Pose button** (next to the shutter; in landscape it is in the side panel). Tap it and the app picks a pose
  that suits the shot and draws it as a blue-green stick-figure outline. A card shows the pose name and one
  sentence on what to do. **<** and **>** step through other poses; tap **Pose** again to hide it.
- **Smart pick:** the first suggestion depends on how many faces are in view (one or two people), selfie or rear camera,
  how big the face is (close-up, half body or full body), and the scene (landscape, architecture...).
  The pose never changes by itself; it only changes when you press < or >.
- **Outline follows the face:** the figure's head sits on the detected face and scales with it. With no face in
  view it shows at a default spot so the person can step into it. Two-person poses use the two faces.
- **27 built-in poses**, all offline: standing, seated, selfie and couple poses (the file is `assets/poses/poses.json`).
- **Landscape side panel** now uses small round buttons in a fixed grid so it is no longer cramped.

## Phase 5a test checklist

- [ ] Landscape: the side buttons (Grid, Guide, Auto, Rule, Pose, Cam, Info) are not cramped and all work
- [ ] Tap **Pose**: an outline and a card appear; < and > change the pose; Pose again hides it
- [ ] With a person in view, the outline's head lands on their face and follows when they move
- [ ] Walk closer/farther: the outline scales with the face
- [ ] Two people in view, then press >: two-person poses (Arm around, Holding hands...) appear near the top
- [ ] Selfie cam: the first poses are close-up selfie poses; outline sits on your face
- [ ] Landscape orientation: the card appears at the top of the preview and does not hide the buttons
- [ ] Portrait: nothing overlaps (card, chips, buttons); preview is a bit smaller than before to make room
- [ ] Do the outlines look like sensible poses? Tell me which ones look wrong or are missing

## Known limits in Phase 5a

- The app does not yet check whether the person is actually doing the pose. Live cues ("raise your hand") come in 5b.
- Outlines are simple stick figures, and seated poses are rough because a front view hides the legs.
- Left and right in the outline are as seen on the screen, so the cue text avoids saying "left hand".
- Not compiled here; reviewed by reading only. Send me any build errors.

# Phase 4 notes (Orientation, rules, selfie)

## What Phase 4 adds

- **Landscape:** hold the phone sideways and the app turns with you (controls move to the side). Auto-rotate must be on.
- **Steadier target:** the dashed target box no longer jumps between corners. It picks one point, sticks to it,
  and only changes when another is clearly closer, after at least 1.5 s, and not while you are panning.
- **Composition rules:** Thirds, Golden ratio, Center. **Rule** button cycles Auto, Thirds, Golden, Center; the grid
  and target follow the rule. Auto: faces use thirds, big or building/close-up food subjects use center,
  landscapes and small lone subjects use golden ratio, else thirds.
- **Selfie camera:** the **Selfie cam / Rear cam** button flips cameras. Preview is mirrored like a mirror; guidance follows.

## Phase 4 test checklist

- [ ] Rotate to landscape (both directions): preview fills correctly, buttons reachable, nothing stretched
- [ ] Landscape: face boxes, guide line and level line sit on the right spots; level line turns green when level
- [ ] Rotate back to portrait: everything still works, camera does not restart or go black
- [ ] Hold a face near the middle of the frame: the target box stays on one spot instead of flickering
- [ ] Pan slowly toward another side: the target switches once, smoothly
- [ ] Rule button: Auto, Thirds, Golden, Center each change the grid and target; readout shows the rule
- [ ] Point at a building / close plate: Auto picks Center; a small lone object far away: Golden
- [ ] Selfie cam: boxes sit on your face (not mirrored the wrong way), guidance direction makes sense
- [ ] Selfie: tap face to lock, shutter saves a photo; flip back to rear works repeatedly
- [ ] Landscape + selfie together

## Known limits in Phase 4

- Camera info shows the rear camera only. Saved selfies are not mirrored (the preview is).
- Side-panel buttons may feel tight on small screens or large fonts; tell me if anything clips.
- Not compiled here; reviewed by reading only. Send me any build errors.

# Phase 3 notes (Control)

An on-device AI camera coach for Android. **Phase 1** (camera, capture, level line, camera info)
is the base. **Phase 2** added frame analysis and composition guidance. **Phase 3** adds subject
lock with focus and exposure metering, automatic exposure, quality warnings, tap-to-zoom, and a
guide line that shows where to move. Everything runs on the phone; no network is used.

## What Phase 3 adds

- **Guide line (replaces the triangle arrows):** a line runs from the center of your subject to the center
  of the dashed target box, ending in an arrowhead. Move the phone so the subject follows the line into the
  box. The line disappears when you are close, and the box turns green when the framing is good.
  The text at the bottom still says it in words ("Pan left and tilt up").
- **Tap a subject to lock it:** tap inside a face or object box. The box turns thick amber, the guidance now
  follows that subject, and the phone focuses and meters exposure and white balance on it. A chip says
  "Subject locked · tap to release"; tap the chip or the subject again to release. If the subject leaves the
  frame for about 2.5 seconds the lock releases itself. Tap empty space for a plain tap-to-focus there.
- **Auto (top bar):** with Auto on, the app meters exposure on a detected face and gently nudges exposure
  compensation toward a good brightness for the scene (faces, food, landscape), at most one small step
  every 1.5 seconds. The phone still chooses ISO and shutter. The readout shows the current EV.
  Auto off puts exposure back to neutral.
- **Quality warnings:** amber chips for "Hold steady" (phone moving fast), "Low light: hold steady",
  "Subject is backlit" and "Bright areas washing out".
- **Steady shutter:** if the phone is turning fast when you press the shutter, it waits up to 0.6 seconds
  for it to settle so the photo is not blurred.
- **Tap to zoom:** when the advice says "Move closer or zoom in" or "Step back or zoom out", tap that
  message and the app zooms so the subject becomes a comfortable size.

## Phase 3 test checklist

- [ ] The guide line runs from the subject to the dashed box; moving along it brings them together, the line
      fades out and the box turns green
- [ ] Tap a face: box turns thick amber, chip says "Subject locked"; the face stays sharp and well exposed
- [ ] Walk or turn so the face moves around: the lock follows it
- [ ] Tap the chip (or the face again): lock releases. Tap empty space: focus moves there
- [ ] Cover the lens or leave the frame for 3 seconds while locked: it releases ("Lost the locked subject")
- [ ] Auto on, person in front of a bright window or sky: the face stays reasonably bright (not a silhouette)
- [ ] Auto on vs Auto off: compare the EV number and the look of the preview
- [ ] With a face metered, walk from a bright area to a darker one: the exposure should follow the light
      (I could not confirm from documentation how the phone behaves here, so this one matters)
- [ ] EV does not swing up and down repeatedly while you hold still (tell me if it does)
- [ ] Point at a plate of food, then a landscape: note the EV the app settles on
- [ ] Wave the phone side to side: "Hold steady" appears; press the shutter while moving: "Waiting for steady..."
- [ ] Person with a window behind them: "Subject is backlit" appears
- [ ] Point at the bright sky: "Bright areas washing out" appears
- [ ] Dim room or covered lens: "Low light: hold steady" appears (tell me if it never does)
- [ ] Hold the phone very close to a face, then far: tap the "zoom" advice and check the zoom lands sensibly
- [ ] Pinch-to-zoom still works, and the preview stays smooth
- [ ] Tap a spot, take a photo, check it saved to the gallery

## Known limits in Phase 3

- **ISO, shutter speed and white-balance presets are not controlled directly yet.** The phone's own auto
  mode picks those; the app steers it through exposure compensation and metering. Direct control needs
  Camera2 options and depends on what your phone allows (see the Camera info dialog).
- **Zoom is tap-to-fix, not automatic.** Automatic zoom can fight the user's own pinching, so it waits until
  tap-to-zoom feels right.
- **Low-light detection uses frame brightness.** Phones brighten the preview in the dark, so the warning may
  fire late or not at all on a phone with a strong night preview.
- **Only detected faces and objects can be locked.** A tap on empty space focuses there but locks nothing.
- Rear camera only; the overlay and metering are not yet adjusted for selfie mode.

# Phase 2 notes

**Documentation:** https://claude.ai/artifact/6Q1U74JQLRXJRpMJj6wyGx

## What Phase 1 does

- Live camera preview (rear camera, CameraX) with tap-to-focus and pinch-to-zoom
- Photo capture, saved to `Pictures/CameraProV1` in your gallery
- **Camera info** button: lists what your phone's rear camera lets an app control
  (ISO range, shutter range, manual focus, zoom range, exposure compensation, hardware level).
  The same lines are written to Logcat under the tag `CameraCapability`.
- **Level line**: stays parallel to the horizon, turns green within 1 degree
- Rule-of-thirds grid (toggle at top left)
- Portrait only for now

## What Phase 2 adds

- **Frame analysis** on every camera frame: faces and objects are found on a background thread, and
  the scene is labeled about once a second (ML Kit models are bundled in the app, so it works offline).
- **Scene guess** (top left): Portrait, Landscape, Food, Architecture or General. Below it is a debug
  line with the strongest ML label and the **milliseconds per frame**. That number is the Gate A check:
  it should stay under 300.
- **Composition score** (top right, 0 to 100): rewards subject placement on thirds, sensible subject
  size, space around the edges and a level horizon.
- **Guidance**: a thin white box around the main subject, a dashed box showing where it should sit, and
  (since Phase 3) a line from one to the other. The message at the bottom says
  it in words ("Pan left and tilt up", "Step back or zoom out", "Great framing, hold steady").
  The dashed box and line are amber while you need to adjust and turn green when framing is good.
- **Guide: on/off** button to hide all of this and use the plain camera.

How the arrows work: if the dashed box is to the right of the subject, the subject needs to move right
in the frame, which means turning the phone left, so you see "Pan left". The advice applies to the main
subject: the largest face if there is one, otherwise the largest object.

## Phase 2 test checklist

Try each of these and tell me what you see (a screenshot of the overlay is ideal):

- [ ] Point at a person's face (or yours with the back camera): scene says Portrait, a face box appears
- [ ] Move the face off the thirds lines: the dashed box and an arrow appear; follow the arrow and it turns green
- [ ] Hold the phone very close, then far away: "Step back" and "Move closer" appear sensibly
- [ ] Point at a landscape or the sky: scene says Landscape (or tell me what the label shows)
- [ ] Point at food on a plate: scene says Food
- [ ] Point at a building: scene says Architecture
- [ ] Look at the ms number in the debug line: note the typical value (for example "120 ms")
- [ ] The preview stays smooth (no stutter) while the overlay updates
- [ ] Guide: off hides everything except the grid and level line; photo capture still works
- [ ] Arrows do not flicker on and off rapidly when you hold still

## Open and run it

1. Install the current stable **Android Studio** and let it download the Android SDK when it asks.
2. **File > Open**, choose this `CameraProV1` folder (the one containing `settings.gradle.kts`).
3. Wait for the first **Gradle sync** (a few minutes; the bar at the bottom shows progress).
   - If Android Studio offers to **update the Android Gradle Plugin**, accept it.
   - If it says an SDK platform is missing, click the link in the message to install it (API 36).
4. On your phone: **Settings > About phone**, tap **Build number** seven times, then
   **Developer options > USB debugging** on. Connect with a USB cable and accept the prompt on the phone.
5. At the top of Android Studio, pick your phone in the device dropdown and press the green **Run** button.
6. Allow the camera permission when the app asks.

## Phase 1 test checklist

Tick these on your phone and tell me what fails:

- [ ] App opens, camera preview appears, no crash
- [ ] Tap the screen: focus follows the tap. Pinch: zoom works
- [ ] Shutter button saves a photo; it shows up in the gallery under `CameraProV1`
- [ ] Photo taken with the phone held sideways is not rotated wrongly in the gallery
- [ ] Level line stays level when you tilt the phone and turns green when flat to the horizon
- [ ] Grid toggle works
- [ ] **Camera info** opens and shows plausible numbers (note them down or copy from Logcat)
- [ ] Deny the permission once: the permission screen appears, and **Allow camera** works

## If something breaks

- **Gradle sync or build error:** copy the red text from the **Build** tab at the bottom and send it to me.
- **App crashes on launch:** open **Logcat**, set the filter to `package:mine`, and send the lines
  around the word `FATAL EXCEPTION`.
- **Camera info looks wrong:** send a screenshot of the dialog.

## Project layout

```
app/src/main/java/com/camerapro/v1/
  MainActivity.kt                 entry point, edge-to-edge, sensor on/off
  camera/CameraCapabilities.kt    reads supported camera controls (used by Phase 3)
  camera/LevelSensor.kt           accelerometer-based horizon angle
  camera/SubjectMetering.kt       focus + exposure metering on a locked subject or a face
  camera/ExposureAutopilot.kt     steps exposure compensation toward a good brightness
  camera/ZoomPlanner.kt           zoom ratio that makes the subject a comfortable size
  camera/MotionSensor.kt          gyroscope speed, for the steady-shutter wait and shake warning
  camera/Sensors.kt               starts and stops the level and motion sensors together
  quality/QualityMonitor.kt       brightness and motion -> warnings without flicker
  analysis/FrameAnalyzer.kt       ML Kit faces, objects and scene labels per frame, plus a brightness grid
  analysis/SceneClassifier.kt     labels + faces -> scene type (rules, easy to tune)
  analysis/Models.kt              scene, subject and result data types
  composition/CompositionEngine.kt  placement, size, edges, tilt -> score and advice
  composition/GuidanceTracker.kt    smoothing so arrows do not jitter
  ui/CameraScreen.kt              permission, preview, overlays, shutter, info dialog
  ui/Theme.kt                     dark theme
gradle/libs.versions.toml         all dependency versions in one place
```

## Next

Next is Phase 5b: live pose cues with ML Kit pose detection.
Direct ISO, shutter and white-balance control (Phase 3.5) depends on what your phone's Camera info shows.
