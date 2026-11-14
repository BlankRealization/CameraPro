#!/usr/bin/env python3
"""Builds app/src/main/assets/poses/poses.json: the AI-generated pose library.

Each pose is described by a few angles; forward kinematics turns that into 14 joint positions in
"head units" (head height = 1, origin = head center, x to the viewer's right, y down).
Angles: 0 = pointing down, +90 = toward the viewer's right, -90 = viewer's left, 180 = up.
Run:  python3 tools/generate_poses.py        (add --sheet out.png to also draw a contact sheet)
"""
import json, math, sys, os

NECK_Y = 0.78
SHOULDER_HALF = 0.95
HIP_HALF = 0.55
TORSO = 2.6
UPPER_ARM, FOREARM = 1.5, 1.4
THIGH, SHIN = 2.0, 2.0


def d(a):
    r = math.radians(a)
    return math.sin(r), math.cos(r)


def add(p, v, k=1.0):
    return [p[0] + v[0] * k, p[1] + v[1] * k]


def figure(lean=0, shoulder_tilt=0, hip_tilt=0, la=(-8, -4), ra=(8, 4), ll=(-4, -2), rl=(4, 2),
           thigh_k=1.0, dx=0.0, dy=0.0):
    head = [dx, dy]
    neck = [dx, dy + NECK_Y]
    hip_c = add(neck, d(lean), TORSO)
    sp = (math.cos(math.radians(lean + shoulder_tilt)), -math.sin(math.radians(lean + shoulder_tilt)))
    hp = (math.cos(math.radians(lean + hip_tilt)), -math.sin(math.radians(lean + hip_tilt)))
    j = {"head": head, "neck": neck}
    j["lShoulder"] = add(neck, sp, -SHOULDER_HALF)
    j["rShoulder"] = add(neck, sp, SHOULDER_HALF)
    j["lHip"] = add(hip_c, hp, -HIP_HALF)
    j["rHip"] = add(hip_c, hp, HIP_HALF)
    for side, arm, leg in (("l", la, ll), ("r", ra, rl)):
        j[side + "Elbow"] = add(j[side + "Shoulder"], d(arm[0]), UPPER_ARM)
        j[side + "Wrist"] = add(j[side + "Elbow"], d(arm[1]), FOREARM)
        j[side + "Knee"] = add(j[side + "Hip"], d(leg[0]), THIGH * thigh_k)
        j[side + "Ankle"] = add(j[side + "Knee"], d(leg[1]), SHIN)
    return {k: [round(v[0], 3), round(v[1], 3)] for k, v in j.items()}


ALL = ["PORTRAIT", "LANDSCAPE", "ARCHITECTURE", "GENERAL"]
POSES = []


def pose(id, name, cue, figs, framing="full", camera="rear", scenes=ALL, moods=("casual",), trend=1):
    POSES.append({"id": id, "name": name, "cue": cue, "people": len(figs), "framing": framing,
                  "camera": camera, "scenes": list(scenes), "moods": list(moods), "trend": trend,
                  "figures": figs})


# ---- One person, full or half body ------------------------------------------------------
pose("relaxed", "Relaxed stand", "Stand tall, shoulders loose, arms relaxed. Look just past the camera.",
     [figure()], moods=("casual",), trend=1)
pose("pockets", "Hands in pockets", "Slide both hands into your pockets, elbows slightly out, soft smile.",
     [figure(la=(-20, 30), ra=(20, -30))], moods=("casual", "confident"), trend=2)
pose("hip", "Hand on hip", "Put one hand on your hip, shift your weight onto the other leg.",
     [figure(hip_tilt=-4, la=(-38, 42), ra=(8, 4), ll=(-2, -2), rl=(12, 4))], moods=("confident",), trend=2)
pose("contrapposto", "Weight shift", "Put your weight on one leg, bend the other knee a little, drop one shoulder.",
     [figure(lean=3, shoulder_tilt=4, hip_tilt=-4, ll=(-2, -2), rl=(16, 3))], moods=("confident", "casual"), trend=2)
pose("crossed", "Arms crossed", "Cross your arms loosely, chin slightly up, relaxed face.",
     [figure(la=(-15, 95), ra=(15, -95))], moods=("confident",), trend=1)
pose("behind", "Hands behind back", "Hold your hands together behind you, chest open, stand straight.",
     [figure(la=(-8, 35), ra=(8, -35))], moods=("calm", "confident"), trend=1)
pose("walk", "Walking toward camera", "Walk slowly toward the camera, swing your arms naturally, look ahead.",
     [figure(la=(-14, -8), ra=(14, 8), ll=(-14, -6), rl=(16, 10))], moods=("candid", "casual"), trend=3)
pose("wall", "Lean on a wall", "Lean one shoulder on the wall, cross one foot over the other, look to the side.",
     [figure(lean=8, shoulder_tilt=-4, la=(-10, -4), ra=(25, 35), ll=(-6, -4), rl=(10, -12))],
     scenes=["PORTRAIT", "ARCHITECTURE", "GENERAL"], moods=("candid", "cool"), trend=2)
pose("hair", "Hand in hair", "Run one hand through your hair, other arm relaxed, look away and laugh.",
     [figure(la=(-8, -4), ra=(150, -85))], moods=("playful", "candid"), trend=2)
pose("thinking", "Hand on chin", "Rest your chin on one hand, other arm across your waist, look into the distance.",
     [figure(la=(-20, 105), ra=(-30, 175))], framing="half", moods=("calm", "candid"), trend=1)
pose("wave", "Wave", "Raise one hand and wave at someone off-camera, big smile.",
     [figure(la=(-8, -4), ra=(110, 170))], moods=("playful",), trend=1)
pose("celebrate", "Arms up", "Throw both arms up and laugh. Do it three times and keep the best.",
     [figure(la=(-150, -170), ra=(150, 170))], moods=("playful",), trend=2)
pose("jump", "Jump", "Jump with arms up and knees bent. The photographer counts: one, two, three.",
     [figure(la=(-140, -165), ra=(140, 165), ll=(-28, 14), rl=(28, -14))], moods=("playful",), trend=2)
pose("wide", "Arms wide", "Open your arms wide and take in the view. Tilt your head back a little.",
     [figure(la=(-100, -100), ra=(100, 100))], scenes=["LANDSCAPE", "GENERAL"], moods=("free", "calm"), trend=3)
pose("viewback", "Take in the view", "Hands in pockets, look out at the scenery. A little off-center looks best.",
     [figure(la=(-20, 30), ra=(20, -30))], scenes=["LANDSCAPE", "ARCHITECTURE"], moods=("calm",), trend=2)
pose("shoulder", "Over the shoulder", "Turn your body away, then look back over your shoulder at the camera.",
     [figure(lean=-3, shoulder_tilt=-6, la=(-12, -6), ra=(14, 8))], moods=("candid", "cool"), trend=3)

# ---- Seated ----------------------------------------------------------------------------
pose("steps", "Sit on steps", "Sit with knees apart, forearms resting on your knees, lean in a little.",
     [figure(lean=2, la=(-30, 20), ra=(30, -20), ll=(-22, -4), rl=(22, 4), thigh_k=0.55)],
     framing="half", scenes=["PORTRAIT", "ARCHITECTURE", "GENERAL"], moods=("casual", "candid"), trend=2)
pose("kneeup", "Sit, one knee up", "Sit down, pull one knee up and hug it. Look off to the side.",
     [figure(lean=-4, la=(-12, -6), ra=(28, 80), ll=(-8, -2), rl=(22, -35), thigh_k=0.6)],
     framing="half", moods=("calm", "candid"), trend=2)
pose("sitback", "Sit and lean back", "Sit, lean back on your hands, legs stretched out and relaxed.",
     [figure(lean=-8, la=(-30, -22), ra=(30, 22), ll=(-6, -2), rl=(6, 2), thigh_k=0.5)],
     framing="half", scenes=["LANDSCAPE", "GENERAL"], moods=("calm", "free"), trend=1)

# ---- Selfie (close framing, rear or front camera) --------------------------------------
pose("peace", "Peace sign", "Raise a hand beside your face with a peace sign. Tilt your head toward it.",
     [figure(shoulder_tilt=-4, la=(-8, -4), ra=(40, -165))], framing="close", camera="selfie",
     moods=("playful",), trend=3)
pose("chin", "Hand near cheek", "Touch your fingertips lightly to your cheek or jaw. Chin down, eyes to the lens.",
     [figure(shoulder_tilt=3, la=(-8, -4), ra=(-30, 175))], framing="close", camera="selfie",
     moods=("calm", "cool"), trend=2)
pose("tilt", "Head tilt", "Tilt your head slightly, drop one shoulder, smile with your eyes.",
     [figure(shoulder_tilt=6, la=(-10, -5), ra=(10, 5))], framing="close", camera="selfie",
     moods=("casual",), trend=1)
pose("selfiehair", "Hair flip", "Lift one hand to your hair, look slightly up, relaxed smile.",
     [figure(shoulder_tilt=-4, la=(-8, -4), ra=(150, -85))], framing="close", camera="selfie",
     moods=("playful", "candid"), trend=2)

# ---- Two people ------------------------------------------------------------------------
pose("armaround", "Arm around", "Stand close, one of you puts an arm around the other's shoulder. Lean in.",
     [figure(lean=-3, dx=-1.3, la=(-8, -4), ra=(80, 103)), figure(lean=3, dx=1.3, la=(-8, -4), ra=(8, 4))],
     framing="half", moods=("friendly",), trend=2)
pose("holdhands", "Holding hands", "Hold hands between you and walk or sway slowly. Look at each other, then the camera.",
     [figure(dx=-1.35, la=(-8, -4), ra=(10, 10)), figure(dx=1.35, la=(-10, -10), ra=(8, 4))],
     moods=("romantic", "candid"), trend=3)
pose("leanin", "Lean in", "Lean your heads toward each other until they almost touch. Smile.",
     [figure(lean=-9, dx=-1.5, la=(-8, -4), ra=(8, 4)), figure(lean=9, dx=1.5, la=(-8, -4), ra=(8, 4))],
     framing="half", moods=("friendly", "romantic"), trend=2)
pose("backtoback", "Back to back", "Stand back to back with arms crossed and look at the camera, confident.",
     [figure(dx=-1.5, la=(-15, 95), ra=(15, -95)), figure(dx=1.5, la=(-15, 95), ra=(15, -95))],
     framing="half", moods=("cool", "confident"), trend=2)


def sheet(path):
    from PIL import Image, ImageDraw
    cols, cell, scale = 6, 260, 26
    rows = (len(POSES) + cols - 1) // cols
    img = Image.new("RGB", (cols * cell, rows * cell), "white")
    dr = ImageDraw.Draw(img)
    bones = [("neck", "lShoulder"), ("neck", "rShoulder"), ("lShoulder", "lElbow"), ("lElbow", "lWrist"),
             ("rShoulder", "rElbow"), ("rElbow", "rWrist"), ("lShoulder", "lHip"), ("rShoulder", "rHip"),
             ("lHip", "rHip"), ("lHip", "lKnee"), ("lKnee", "lAnkle"), ("rHip", "rKnee"), ("rKnee", "rAnkle")]
    for i, p in enumerate(POSES):
        ox, oy = (i % cols) * cell + cell // 2, (i // cols) * cell + 40
        dr.text((ox - cell // 2 + 6, oy - 36), p["name"], fill="black")
        for f in p["figures"]:
            pt = lambda k: (ox + f[k][0] * scale, oy + f[k][1] * scale)
            for a, b in bones:
                dr.line([pt(a), pt(b)], fill=(0, 120, 200), width=3)
            hx, hy = pt("head")
            dr.ellipse([hx - scale * .5, hy - scale * .5, hx + scale * .5, hy + scale * .5], outline=(0, 120, 200), width=3)
            for a, b in (("neck", "head"),):
                pass
    img.save(path)


if __name__ == "__main__":
    out = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "poses", "poses.json")
    with open(out, "w") as fh:
        json.dump({"version": 1, "poses": POSES}, fh, indent=1)
    print("wrote", len(POSES), "poses to", os.path.normpath(out))
    if "--sheet" in sys.argv:
        sheet(sys.argv[sys.argv.index("--sheet") + 1])
