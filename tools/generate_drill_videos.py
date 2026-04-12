#!/usr/bin/env python3
"""
Generate drill demonstration videos using Google Veo 3.1 on Vertex AI.

All 46 drills (22 ski + 24 snowboard) with precise biomechanical prompts.
Each video is 4-8 seconds, 720p, 16:9.

Usage:
  1. gcloud auth application-default login
  2. export GCP_PROJECT=your-project-id
  3. python generate_drill_videos.py
"""

import json
import os
import time
import base64
import requests
from pathlib import Path

PROJECT_ID = os.environ.get("GCP_PROJECT", "")
LOCATION = "us-central1"
MODEL = "veo-3.1-generate-001"
OUTPUT_DIR = Path(__file__).parent.parent / "app" / "src" / "main" / "assets" / "drill_videos"

ENDPOINT = f"https://{LOCATION}-aiplatform.googleapis.com/v1/projects/{PROJECT_ID}/locations/{LOCATION}/publishers/google/models/{MODEL}:predict"


# ═══════════════════════════════════════════════
#  SKI DRILL PROMPTS (22 drills)
#  Each prompt describes the exact ideal movement
# ═══════════════════════════════════════════════

SKI_DRILLS = {
    # ── BEGINNER ──
    "b01_snowplow_glide": {
        "prompt": "Side view of a skier performing a snowplow glide on a gentle green slope. The skier maintains a stable V-shape wedge with ski tips close together and tails pushed apart about shoulder width. Knees are flexed and pushed slightly inward. Arms are forward at waist height holding poles. The skier glides steadily downhill at slow speed without turning. Groomed snow surface, clear day, mountain trees in background. Smooth steady camera.",
        "seconds": 5,
    },
    "b02_snowplow_turns": {
        "prompt": "Front-quarter view of a beginner skier making linked snowplow turns on a gentle green slope. The skier maintains a wedge position throughout. To turn left, they press more weight onto the right ski while keeping the wedge shape. To turn right, weight shifts to the left ski. Three complete turns shown. Movements are deliberate and controlled at slow speed. Each turn finishes across the hill. Groomed snow, mountain scenery.",
        "seconds": 7,
    },
    "b03_straight_run_stop": {
        "prompt": "Side view of a beginner skier on a gentle slope. First they point skis straight downhill and glide for 3 seconds building slight speed. Then they progressively push their heels outward into a wider and wider snowplow wedge, decelerating smoothly until they come to a complete stop. Weight stays centered over both feet throughout. Clean groomed snow, gentle pitch.",
        "seconds": 6,
    },
    "b04_falling_leaf": {
        "prompt": "Front view of a skier standing sideways across a gentle slope, performing the falling leaf drill. They rock weight forward onto the balls of their feet and slide diagonally forward-downhill, then shift weight to their heels and slide diagonally backward-downhill, creating a zigzag pattern like a falling leaf. Upper body faces downhill throughout. Skis stay across the slope. Smooth controlled movements. Groomed green slope.",
        "seconds": 7,
    },
    "b05_fan_progression": {
        "prompt": "Overhead-angle view of a beginner skier on a gentle slope performing fan progression. They start traversing almost across the hill, make a small turn back uphill. Then traverse again pointing slightly more downhill, turn back uphill. Each pass aims more steeply down the fall line. Five passes shown from above, creating a fan-shaped pattern in the snow. Groomed green terrain.",
        "seconds": 8,
    },

    # ── INTERMEDIATE ──
    "i01_stem_christie": {
        "prompt": "Side view of an intermediate skier performing stem christie turns on a blue groomed slope. At turn initiation, the uphill ski pushes out slightly into a small wedge stem. As they cross the fall line, the inside ski matches parallel to the outside ski. The finish of each turn is fully parallel. Three linked turns shown. The stem gets smaller with each turn. Medium speed, athletic stance.",
        "seconds": 7,
    },
    "i02_hockey_stop": {
        "prompt": "Front view of a skier performing a hockey stop on a blue groomed slope. The skier builds moderate speed in a straight run, then simultaneously pivots both skis 90 degrees across the direction of travel while setting edges firmly. Snow sprays from the edges as they skid to a complete stop. Shoulders stay facing downhill throughout the stop. Upper body remains stable. Shown from both sides.",
        "seconds": 5,
    },
    "i03_sideslip_edge_set": {
        "prompt": "Front view of a skier performing sideslip with edge set on a blue slope. Starting from a straight run, they pivot both skis simultaneously perpendicular to the fall line. They sideslip straight down the hill for several meters maintaining hip-width stance. Then they sharply set their edges with a pole plant and hold motionless for 3 seconds. Clean controlled movement, groomed blue terrain.",
        "seconds": 7,
    },
    "i04_basic_parallel": {
        "prompt": "Following camera view from behind of a skier making clean basic parallel turns on a blue groomed slope. Both skis remain parallel and shoulder-width apart throughout every turn. The skier rolls both ankles simultaneously to initiate each turn. A crisp pole plant times each turn initiation. Five smooth linked turns at moderate speed. Skis leave parallel tracks in the groomed snow. Mountain scenery.",
        "seconds": 7,
    },
    "i05_outside_ski_turns": {
        "prompt": "Side view of a skier performing outside ski turns on a blue slope. The skier makes medium-radius carved turns with their entire weight on the outside (downhill) ski. The inside ski is lifted completely off the snow during each turn. Between turns, a deliberate weight transfer step to the new outside ski is visible. Eight turns shown with inside ski clearly airborne. Poles used for timing only.",
        "seconds": 8,
    },
    "i06_thousand_steps": {
        "prompt": "Following camera view of a skier performing the thousand steps drill on a blue slope. While skiing down the slope, the skier takes many small rapid steps from one foot to the other, transferring weight with each step. Five to eight small steps per turn. The upper body remains stable while the legs work quickly underneath. The stepping creates a walking-while-turning motion. Groomed terrain.",
        "seconds": 7,
    },
    "i07_pivot_slips": {
        "prompt": "Front view of a skier performing pivot slips on a blue slope with consistent fall line. Starting from a straight run, the skier pivots both skis sideways simultaneously, sideslips for two meters, then pivots to the other direction and sideslips again. Four alternating pivots and sideslips shown. Hip-width stance maintained throughout. Minimal speed loss between pivots. Finishes with an edge set and pole plant.",
        "seconds": 8,
    },

    # ── ADVANCED ──
    "a01_dynamic_parallel": {
        "prompt": "Side view of an advanced skier making dynamic parallel turns on a red steep groomed slope. Active flexion-extension through each turn: the skier compresses deep at the turn completion, then extends tall at the transition with a crisp pole plant. Strong rhythmic bouncing motion between turns. High energy, athletic stance, ten linked turns at speed. Snow spray on each turn. Mountain background.",
        "seconds": 7,
    },
    "a02_railroad_tracks": {
        "prompt": "Rear following camera view of a skier carving clean railroad track turns on a groomed blue-red slope. The camera follows from behind showing two perfectly clean parallel arcs carved into the snow with zero skidding or snow spray. The skier progressively rolls their ankles and knees to tip the skis on edge. The sidecut of the ski creates the turn naturally. Six beautiful clean carved arcs visible in the snow behind the skier.",
        "seconds": 7,
    },
    "a03_javelin_turns": {
        "prompt": "Side view of a skier performing javelin turns on a blue-red groomed slope. At each turn initiation, the skier lifts the inside ski and holds it forward at an angle like a javelin. All weight is on the outside ski which steers underneath the lifted ski. The lifted ski creates visible hip angulation. At the transition, the ski is set down and the other side lifts. Six turns with alternating javelin positions. Athletic dynamic movement.",
        "seconds": 7,
    },
    "a04_one_ski_skiing": {
        "prompt": "Side view of a skier wearing only one ski, making eight linked medium-radius turns on a blue groomed slope. The free foot is held completely off the snow in a stable position without swinging. The skier carves round turns on a single ski with consistent speed. Poles are used for timing only, never for balance support. Both left and right leg shown. Advanced balance drill.",
        "seconds": 8,
    },
    "a05_lane_changes": {
        "prompt": "Overhead view of a skier performing lane changes on a blue slope. The skier makes three short carved turns in a narrow corridor, then traverses across to a parallel corridor and repeats three more short turns. Six sets of three turns connected by five traverses. Consistent turn radius and rhythm in each corridor. The pattern creates a zigzag of corridors down the slope. Groomed terrain.",
        "seconds": 8,
    },
    "a06_hop_turns": {
        "prompt": "Front view of a skier performing hop turns on a blue slope. Starting from a traverse, the skier jumps with both skis, pivots them in the air to face the opposite direction, and lands on edge. Immediately jumps again to the other side. Fifteen consecutive rapid hops shown. Skis remain parallel to the snow surface during flight. Consistent rhythm with no pauses. The track in the snow forms a zigzag Z-pattern.",
        "seconds": 7,
    },
    "a07_carving_medium": {
        "prompt": "Following camera view of an advanced skier performing medium-radius carved turns on a steep red groomed slope. High edge angles above 30 degrees, with the skier inclining their body like a motorcycle rider into each turn. Early edge engagement at turn initiation, progressive edge angle building through the turn, and a high degree of carving. Clean arcs in the snow. Dynamic athletic movement with speed.",
        "seconds": 7,
    },
    "a08_compression_turns": {
        "prompt": "Side view of a skier performing compression turns over moguls on a blue-red slope with bumps. The skier flexes their legs dramatically pulling knees toward chest as they go over each bump, then extends in the troughs. The upper body and head remain at a constant height above the snow while the legs absorb like shock absorbers. Short radius turns around the moguls. Speed control through completed turns.",
        "seconds": 7,
    },

    # ── EXPERT ──
    "e01_short_radius_carve": {
        "prompt": "Side view of an expert skier performing short-radius carved turns on a steep black groomed slope. Extremely tight rapid turns with high edge angles above 40 degrees. Quick edge-to-edge transitions powered by rebound energy from the previous turn. Aggressive carving with significant snow spray. Dynamic flexion-extension at each transition. Powerful pole plants. Maximum speed and commitment.",
        "seconds": 6,
    },
    "e02_dynamic_carving": {
        "prompt": "Following camera view of an expert skier performing full dynamic carving on a steep black groomed slope. Maximum edge angles approaching 60 degrees with hip nearly touching the snow. Explosive transitions between turns with full body extension. Clean railroad-track arcs in the snow. The body creates a C-shape with hips inside and shoulders outside the turn. Beautiful flowing S-shaped track. Ultimate skiing performance.",
        "seconds": 7,
    },
    "e03_one_ski_hourglass": {
        "prompt": "Side view of an expert skier wearing one ski on a steep black slope performing the hourglass drill. Starting with large GS-sized turns, they gradually decrease turn radius over five turns until reaching tight slalom-sized turns at the midpoint. Then gradually increase back to GS radius for five more turns. All on a single ski with the free foot held completely off the snow. Carved round turns throughout.",
        "seconds": 8,
    },
    "e04_mogul_v_corridor": {
        "prompt": "Following camera view of an expert skier in a mogul field on a black slope. Starting with long-radius turns spanning multiple moguls, they gradually decrease turn radius with each successive turn. By the tenth turn, the turn radius matches a single mogul. High speed maintained throughout. Round symmetric turns. Legs absorbing aggressively. Expert mogul skiing with decreasing radius from top to bottom.",
        "seconds": 8,
    },
    "e05_varied_terrain": {
        "prompt": "Following camera view of an expert skier on steep variable terrain with changing slope angles, side hills, and ungroomed snow. The skier maintains consistent 20-meter turn radius for ten turns regardless of terrain changes. High rate of speed. Any air time is controlled with skis paralleling the landing slope. Skis parallel and equidistant throughout. Expert all-mountain skiing.",
        "seconds": 8,
    },
    "e06_white_pass_turns": {
        "prompt": "Side view of an expert skier performing White Pass turns on a blue-red groomed slope. At turn initiation, the outside ski is lifted off the snow while the edge change happens on the inside ski. The outside ski is set down during the shaping phase for carving, then lifted again before the finish. The track is more carved than steered. Advanced pressure control drill showing precise weight management.",
        "seconds": 8,
    },
}

# ═══════════════════════════════════════════════
#  SNOWBOARD DRILL PROMPTS (24 drills)
# ═══════════════════════════════════════════════

SNOWBOARD_DRILLS = {
    # ── BEGINNER ──
    "sb_b01": {
        "prompt": "Side view of a snowboarder skating across flat terrain near a ski lodge. Only the lead foot is strapped into the binding. They push with their free back foot like a skateboard to glide forward. Weight stays over the lead foot. Small controlled pushes. Beginner learning to move with one foot. Flat groomed snow, base area scenery.",
        "seconds": 5,
    },
    "sb_b02": {
        "prompt": "Front view of a beginner snowboarder performing a heelside sideslip on a gentle green slope. They face uphill with the board perpendicular to the fall line. By slowly flattening the board they begin to slide sideways down the slope. Then they re-engage the heelside edge by lifting their toes, stopping the slide. Controlled speed management. Knees bent, weight centered. Groomed snow.",
        "seconds": 6,
    },
    "sb_b03": {
        "prompt": "Front view of a beginner snowboarder performing a toeside sideslip on a gentle green slope. They face downhill with the board perpendicular to the fall line. Pressing toes and shins into the boots to hold the toeside edge. Slowly releasing to slide sideways. Then re-engaging by pressing toes to stop. Head up, not looking at board. Arms forward for balance. Groomed snow.",
        "seconds": 6,
    },
    "sb_b04": {
        "prompt": "Front view of a snowboarder performing the falling leaf drill on a green slope. On their heelside edge, they shift weight to lead foot to slide diagonally forward-downhill. Then shift weight to back foot to slide diagonally backward-downhill. The path creates a zigzag leaf-falling pattern while staying on the heelside edge the entire time. Controlled gentle movements.",
        "seconds": 7,
    },
    "sb_b05": {
        "prompt": "Side view of a beginner snowboarder performing a J-turn on the heelside. Starting pointed straight downhill on a gentle slope, they gain slight speed then shift weight to heels and rotate their lead shoulder uphill. The board arcs into a heelside turn and they come to a stop across the hill. The track in the snow forms a J-shape. Single clean turn from fall line to stop. Groomed green terrain.",
        "seconds": 5,
    },
    "sb_b06": {
        "prompt": "Side view of a beginner snowboarder performing a J-turn on the toeside. Starting pointed downhill, they shift weight to toes, press shins forward, and rotate lead shoulder to turn across the hill on the toeside edge. Coming to a stop facing uphill. The track forms a J. More challenging than heelside. Committed movement with clear edge engagement. Gentle green slope.",
        "seconds": 5,
    },

    # ── INTERMEDIATE ──
    "sb_i01": {
        "prompt": "Front-quarter view of a snowboarder making linked skidded turns on a blue groomed slope. They connect heelside and toeside turns continuously without stopping. Between turns, the board briefly passes through flat as they transition edges. Each turn finishes across the hill for speed control. Consistent rhythm, relaxed athletic stance, looking ahead. Six linked turns shown. Mountain scenery.",
        "seconds": 7,
    },
    "sb_i02": {
        "prompt": "Side view of a snowboarder performing heelside garlands on a blue slope. Traversing on the heelside edge, they flatten the board slightly to dip toward the fall line, then re-engage the heelside edge to arc back across the hill. Five dips and recoveries shown. Never fully crossing the fall line. Staying on heelside the entire time. Smooth controlled edge modulation.",
        "seconds": 7,
    },
    "sb_i03": {
        "prompt": "Side view of a snowboarder performing toeside garlands on a blue slope. Traversing on the toeside edge, they relax toes slightly to dip toward the fall line, then press shins forward to re-engage toeside. Five dips shown. Head stays up throughout. Confident toeside edge control without crossing the fall line. Groomed blue terrain.",
        "seconds": 7,
    },
    "sb_i04": {
        "prompt": "Following camera view of a snowboarder making aggressive speed check turns on a blue-to-red slope. Quick dynamic linked turns with emphasis on completely finishing each turn across the fall line. Strong flex and extend through each turn. Upper body stays quiet facing downhill while the board pivots underneath. Eight decisive turns for speed control on increasingly steep terrain.",
        "seconds": 7,
    },
    "sb_i05": {
        "prompt": "Side view of a snowboarder riding with only the lead foot strapped in on a green-blue slope. The back foot rests on the stomp pad without being strapped. Making gentle turns using only the front foot for steering. Back foot provides balance only. Demonstrates balance and board awareness. Gentle terrain, controlled speed.",
        "seconds": 6,
    },
    "sb_i06": {
        "prompt": "Front view of a snowboarder riding switch (non-dominant foot forward) on a gentle green slope. Making basic linked turns in the reversed stance. Movements are slightly less polished than regular riding. Starting with wide turns, working to maintain rhythm. The rider occasionally looks over the new lead shoulder for direction. Building switch confidence.",
        "seconds": 7,
    },

    # ── ADVANCED ──
    "sb_a01": {
        "prompt": "Following camera view from behind of a snowboarder carving clean turns on a blue-red groomed slope. The board rides purely on its edge without any skidding. A single clean carved line visible in the snow behind the rider. The snowboarder inclines their entire body into each turn like a motorcycle rider. Progressive edge engagement. Four clean carved turns. Groomed corduroy snow.",
        "seconds": 7,
    },
    "sb_a02": {
        "prompt": "Side view of a snowboarder performing dynamic carving on a red steep groomed slope. Aggressive edge angles with active flex-extend movements. The rider compresses deep into each turn and extends tall through transitions. Significant G-forces visible in the body position. Board bending under load. Alternating toeside and heelside with high commitment. Snow spray from carved edges.",
        "seconds": 7,
    },
    "sb_a03": {
        "prompt": "Following camera view of a snowboarder making short-radius turns on a steep red-black groomed slope. Rapid quick turns with fast edge-to-edge transitions. The board pivots quickly through each turn. Upper body stays facing downhill while only the lower body and board rotate. Tight rhythmic turns for steep terrain speed control. Dynamic flexion-extension for quick lightening of the board.",
        "seconds": 6,
    },
    "sb_a04": {
        "prompt": "Side view of a snowboarder performing an ollie on flat groomed terrain. The rider shifts weight to the back foot, pressing the tail down and flexing the board. Then they spring upward by extending the back leg while pulling the front knee up. The board pops off the snow tail-first then levels out in the air. Clean pop with board flex visible. Lands balanced. Then shows a nollie from the nose.",
        "seconds": 6,
    },
    "sb_a05": {
        "prompt": "Side view of a snowboarder performing butter presses on a blue groomed slope. First a nose press: shifting weight fully forward, lifting the tail off the snow while balanced on the nose. Holding the press position for two seconds. Then a tail press: weight shifts back, nose lifts off snow. Adds a slight rotation for a butter. Core strength and balance visible. Smooth controlled movements.",
        "seconds": 7,
    },
    "sb_a06": {
        "prompt": "Side view of a snowboarder riding through a mogul field on a black slope. Legs flex dramatically pulling knees to chest over each bump, then extend in the troughs. Upper body and head remain at constant height. Short radius turns around the bumps. Speed control through completed turns. The legs act as active shock absorbers. Athletic dynamic riding through challenging terrain.",
        "seconds": 7,
    },

    # ── EXPERT ──
    "sb_e01": {
        "prompt": "Side view of an expert snowboarder performing euro-carving laydown turns on a steep groomed slope. Extreme edge angles approaching 70 degrees with the rider's hip and hand nearly touching the snow surface. Maximum speed and full body inclination into each turn. Alternating toeside and heelside with the hand dragging on the snow in each direction. Beautiful clean arcs. Ultimate snowboard carving.",
        "seconds": 7,
    },
    "sb_e02": {
        "prompt": "Side view of a snowboarder performing a frontside 360 off a medium park jump. Approach with moderate speed. Pop off the lip of the jump. Initiate rotation with the lead shoulder spinning frontside. Spot the landing at 270 degrees by looking over the lead shoulder. Complete the full 360 degree rotation and land clean riding forward. Smooth committed spin with controlled body position throughout the air.",
        "seconds": 5,
    },
    "sb_e03": {
        "prompt": "Following camera view of an expert snowboarder carving clean turns while riding switch on a red groomed slope. Clean carved turns in the non-dominant stance. No skidding. Equal confidence toeside and heelside in switch. The riding quality approaches regular stance carving. Advanced board mastery demonstration. Clean arcs in the corduroy snow.",
        "seconds": 7,
    },
    "sb_e04": {
        "prompt": "Following camera view of an expert snowboarder confidently riding steep variable terrain on a black diamond run. Wind-affected snow, ice patches, and variable conditions. The rider maintains consistent turn shape and speed control regardless of changing snow. Extra knee flexion for absorption. Athletic centered stance adapting to every surface change. Expert all-mountain snowboarding.",
        "seconds": 7,
    },
    "sb_e05": {
        "prompt": "Side view of a snowboarder riding through deep powder snow. Weight shifted slightly to the back foot keeping the nose above the snow surface. Flowing medium-radius turns with continuous motion. Never stopping. A bouncing press-and-release rhythm creating float between turns. Powder spraying with each turn. Deep untracked snow in a tree-lined mountain setting. Beautiful powder day riding.",
        "seconds": 7,
    },
    "sb_e06": {
        "prompt": "Following camera view of a snowboarder performing a freestyle combo run on a blue-red slope and park features. Starts with carved turns, hits a side hit for an ollie, lands into a nose press, transitions to switch carving, performs a frontside 180 off a roller, rides out forward. Continuous flowing creative run linking tricks and carves without stopping. Maximum style and expression.",
        "seconds": 8,
    },
}

ALL_DRILLS = {**SKI_DRILLS, **SNOWBOARD_DRILLS}


def get_access_token():
    """Get GCP access token."""
    try:
        import google.auth
        import google.auth.transport.requests
        creds, _ = google.auth.default()
        creds.refresh(google.auth.transport.requests.Request())
        return creds.token
    except Exception:
        import subprocess
        result = subprocess.run(
            ["gcloud", "auth", "application-default", "print-access-token"],
            capture_output=True, text=True,
        )
        return result.stdout.strip()


def generate_video(drill_id, prompt, seconds):
    """Call Veo 3.1 to generate one drill video."""
    token = get_access_token()
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    }
    body = {
        "instances": [{"prompt": prompt}],
        "parameters": {
            "aspectRatio": "16:9",
            "sampleCount": 1,
            "durationSeconds": seconds,
            "resolution": "720p",
        },
    }

    print(f"  [{drill_id}] Generating {seconds}s video...")
    response = requests.post(ENDPOINT, headers=headers, json=body, timeout=300)

    if response.status_code != 200:
        print(f"    ERROR {response.status_code}: {response.text[:200]}")
        return None

    result = response.json()
    predictions = result.get("predictions", [])
    if not predictions:
        print(f"    No predictions")
        return None

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    output_path = OUTPUT_DIR / f"{drill_id}.mp4"

    pred = predictions[0]
    if "bytesBase64Encoded" in pred:
        video_bytes = base64.b64decode(pred["bytesBase64Encoded"])
        output_path.write_bytes(video_bytes)
        size_mb = len(video_bytes) / 1024 / 1024
        print(f"    Saved: {output_path.name} ({size_mb:.1f} MB)")
        return str(output_path)
    elif "videoUri" in pred:
        print(f"    URI: {pred['videoUri']}")
        return pred["videoUri"]
    else:
        print(f"    Unknown format: {list(pred.keys())}")
        return None


def main():
    if not PROJECT_ID or PROJECT_ID == "":
        print("ERROR: Set GCP_PROJECT environment variable")
        print("  export GCP_PROJECT=your-project-id")
        return

    print(f"=== MyCarv Drill Video Generator ===")
    print(f"Model: Veo 3.1 ({MODEL})")
    print(f"Project: {PROJECT_ID}")
    print(f"Drills: {len(ALL_DRILLS)} ({len(SKI_DRILLS)} ski + {len(SNOWBOARD_DRILLS)} snowboard)")
    print(f"Output: {OUTPUT_DIR}")
    print()

    results = {}
    for i, (drill_id, config) in enumerate(ALL_DRILLS.items()):
        print(f"[{i+1}/{len(ALL_DRILLS)}]")
        result = generate_video(drill_id, config["prompt"], config["seconds"])
        results[drill_id] = result
        time.sleep(3)

    manifest = OUTPUT_DIR / "manifest.json"
    with open(manifest, "w") as f:
        json.dump(results, f, indent=2)

    ok = sum(1 for v in results.values() if v)
    print(f"\n=== Done: {ok}/{len(ALL_DRILLS)} videos generated ===")
    print(f"Manifest: {manifest}")


if __name__ == "__main__":
    main()
