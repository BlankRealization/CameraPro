# Camera Pro V1

Android camera app that analyses the live frame on-device and coaches the user to a better shot.
Product documentation (scope, specs, architecture, roadmap):
https://claude.ai/artifact/6Q1U74JQLRXJRpMJj6wyGx

## Decisions already made

- Kotlin, Jetpack Compose, CameraX (+ Camera2 interop for ISO/shutter/focus/WB). Package `com.camerapro.v1`.
- Fully on-device. No network permission, no cloud calls, everything works in airplane mode.
- ML: free pre-trained models first (LiteRT/TFLite, MediaPipe, ML Kit); tune later.
- Pose library is AI-generated before release, bundled as keypoints + outlines, selected offline at runtime.
- minSdk 29, targetSdk/compileSdk 36. Test phone: vivo X200T (Android 16).
- Post-capture editor, monetization and accounts are out of scope for V1.
- Selfie (front camera) and landscape are part of Phase 4, before the pose phase.

## Rules

- Preview stays at 30 fps. ML analysis runs on its own executor and drops frames when behind.
- Always read supported ranges from `CameraCapabilityChecker` and clamp requests to them. If a control is
  unsupported, fall back silently to the phone's auto mode.
- Change one camera setting at a time and smooth changes over frames.
- Keep all dependency versions in `gradle/libs.versions.toml`.

## Phases and gates

1. Foundation (done, tested on the vivo X200T)
2. Composition (code written, needs on-device testing): `analysis/` (ML Kit faces, objects, scene labels; rule-based
   `SceneClassifier`), `composition/` (`CompositionEngine` score and advice, `GuidanceTracker` smoothing), overlay in
   `ui/CameraScreen.kt`. The analysis image and preview are both 3:4 portrait, so normalized boxes map directly onto the
   preview (rear camera only; revisit for the front camera). **Gate A:** overlay under 300 ms on a mid-range phone;
   the on-screen debug line shows ms per frame.
3. Control (code written, needs on-device testing): `SubjectMetering` (tap-to-lock subject, face metering, tap-to-focus),
   `ExposureAutopilot` (steps exposure compensation, dead band, 1.5 s interval), `ZoomPlanner` (tap-to-zoom),
   `QualityMonitor` + `MotionSensor` (warnings, steady-shutter wait), guide line from subject to target box.
   Not done yet: direct ISO / shutter / white-balance control (needs Camera2 interop; check Camera info first),
   automatic zoom, a real light-sensor or exposure-data low-light signal (currently frame brightness only).
4. Orientation, rules and selfie (code written, needs on-device testing): landscape layout (one layout tree so the
   preview is never rebuilt on rotation; analyzer and level sensor use the display rotation), sticky target
   (`TargetSelector`, ghost smoothing) so the target does not jump between corners, composition rules
   (`CompositionRule`: thirds, golden ratio, center; `RuleChoice` Auto or manual) with a grid per rule, and a
   front-camera flip (mirrored boxes). Not done: selfie camera capabilities in Camera info, mirrored saved selfies.
5. Poses. **Gate B:** poses work offline.
   - 5a (code written, needs on-device testing): AI-generated library `assets/poses/poses.json` (27 stick-figure
     poses, built by `tools/generate_poses.py` from joint angles; rerun it to change the library), `pose/PoseModels.kt`
     (loader, org.json), `pose/PoseMatcher.kt` (ranks by people count, selfie vs rear, face size, scene, trend),
     `pose/PoseOverlay.kt` (outline anchored to the face), Pose button + card in `ui/CameraScreen.kt`.
   - 5b (not started): live cues using ML Kit pose detection (compare the person's joints with the target and say
     what to change), more poses, pose-aware composition target.
6. Beta: device testing across Samsung, vivo, OPPO, Redmi; thermal and battery. **Gate C:** stable beta.

## Build and run

`./gradlew assembleDebug` builds the APK; `./gradlew installDebug` installs it on a connected phone
(USB debugging on). Logcat tags: `CameraCapability`, `CameraScreen`.
