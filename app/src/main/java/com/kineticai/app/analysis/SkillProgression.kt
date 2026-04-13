package com.kineticai.app.analysis

/**
 * Comprehensive skill progression system from beginner to expert.
 *
 * Sources:
 *   - U.S. Ski & Snowboard SkillsQuest Assessment (Phases 2–6)
 *   - PSIA/CSIA Teaching Progression (4 fundamental skills)
 *   - Stomp It Tutorials drill library
 *   - professional ski coaching methodology
 *   - Professional ski instructor curriculum
 *
 * Each drill defines:
 *   - Technique description and how-to instructions
 *   - Reference metrics (what "good" looks like at this level)
 *   - IMU-measurable pass criteria
 *   - Coaching tips from professional sources
 *
 * The 4 skill categories (PSIA framework): Balance, Edging, Rotary, Pressure
 */

enum class SkillLevel(val order: Int, val displayName: String) {
    BEGINNER(0, "Beginner"),
    INTERMEDIATE(1, "Intermediate"),
    ADVANCED(2, "Advanced"),
    EXPERT(3, "Expert"),
}

enum class SkillCategory { BALANCE, EDGING, ROTARY, PRESSURE, MIXED }

data class ReferenceMetrics(
    val edgeAngleMin: Float, val edgeAngleMax: Float,
    val earlyEdgingMin: Float, val progressiveEdgeMin: Float,
    val turnShapeMin: Float,
    val gForceMin: Float, val gForceMax: Float,
    val speedMinKmh: Float, val speedMaxKmh: Float,
    val weightReleaseMin: Float, val turnSymmetryMin: Float,
    val centeredBalanceMin: Float,
    val skiIQMin: Int, val skiIQMax: Int,
)

data class SkillDrill(
    val id: String,
    val level: SkillLevel,
    val category: SkillCategory,
    val title: String,
    val description: String,
    val howTo: String,
    val terrain: String,
    val reference: ReferenceMetrics,
    val passCriteria: String,
    val tips: List<String>,
    val source: String,
)

object SkillProgressionData {

    val drills: List<SkillDrill> = listOf(

        // ════════════════════════════════════════════
        //  BEGINNER DRILLS (Green terrain, Kinetic Score <90)
        // ════════════════════════════════════════════

        SkillDrill(
            id = "b01_snowplow_glide", level = SkillLevel.BEGINNER, category = SkillCategory.BALANCE,
            title = "Snowplow Glide",
            description = "Glide in a wedge (pizza) position on a gentle slope. Focus on maintaining balance " +
                "and controlling speed with the width of your wedge.",
            howTo = "On a gentle green slope, push your heels apart to form a V-shape with tips close together. " +
                "Keep hands forward and visible. Look ahead, not at your skis. Wider wedge = more braking.",
            terrain = "Green — gentle, groomed",
            reference = ReferenceMetrics(2f, 15f, 0f, 0f, 0f, 0f, 0.5f, 3f, 12f, 0f, 0f, 25f, 0, 60),
            passCriteria = "Maintain controlled glide < 12 km/h for 20+ seconds",
            tips = listOf(
                "Arms forward like you're holding a serving tray",
                "Feel equal pressure on both feet",
                "Look where you want to go, not at the snow",
                "Relax your ankles — don't lock your knees",
            ),
            source = "PSIA Beginner Progression",
        ),
        SkillDrill(
            id = "b02_snowplow_turns", level = SkillLevel.BEGINNER, category = SkillCategory.ROTARY,
            title = "Snowplow Turns",
            description = "Make linked turns in a wedge position by shifting weight to the outside ski. " +
                "Left foot pressure = turn right, right foot pressure = turn left.",
            howTo = "Start in a wedge glide. To turn left, press more on your right foot and steer it slightly. " +
                "Let the ski guide you around. Alternate left and right. Finish each turn across the hill.",
            terrain = "Green — gentle, groomed",
            reference = ReferenceMetrics(5f, 18f, 5f, 0f, 15f, 0.1f, 0.8f, 5f, 18f, 0f, 0.35f, 30f, 20, 70),
            passCriteria = "6+ linked turns with turn symmetry ≥ 35%",
            tips = listOf(
                "Steer with your feet, not your upper body",
                "Finish each turn across the hill before starting the next",
                "Keep a consistent wedge size throughout",
                "Try to make round turns, not sharp zigzags",
            ),
            source = "PSIA Beginner Progression",
        ),
        SkillDrill(
            id = "b03_straight_run_stop", level = SkillLevel.BEGINNER, category = SkillCategory.PRESSURE,
            title = "Straight Run to Stop",
            description = "Glide straight down a gentle slope, then make a controlled stop using a snowplow. " +
                "This builds confidence in speed control.",
            howTo = "Point skis straight down a gentle green slope. Let yourself glide for 5-10 meters, then " +
                "gradually push your heels out into a wider and wider wedge until you stop completely.",
            terrain = "Green — gentle, groomed",
            reference = ReferenceMetrics(2f, 20f, 0f, 0f, 0f, 0f, 0.6f, 5f, 15f, 0f, 0f, 25f, 0, 55),
            passCriteria = "Complete stop within 15 meters from a straight glide",
            tips = listOf(
                "Start with a narrow wedge, gradually widen",
                "Keep your weight centered — don't lean back",
                "Press evenly on both feet to stop straight",
                "Practice until stopping feels automatic",
            ),
            source = "PSIA Beginner Progression",
        ),
        SkillDrill(
            id = "b04_falling_leaf", level = SkillLevel.BEGINNER, category = SkillCategory.EDGING,
            title = "Falling Leaf",
            description = "Slide sideways across the slope, then reverse direction — like a leaf falling from a tree. " +
                "Develops edge awareness and fore-aft balance.",
            howTo = "Stand sideways across the slope. Rock forward onto the balls of your feet to slide forward-sideways. " +
                "Then rock back onto your heels to slide backward-sideways. Repeat 10 times, then switch sides.",
            terrain = "Green — gentle, groomed",
            reference = ReferenceMetrics(5f, 15f, 0f, 0f, 0f, 0f, 0.5f, 2f, 8f, 0f, 0f, 35f, 10, 60),
            passCriteria = "10 direction changes with smooth transitions",
            tips = listOf(
                "Keep your upper body facing downhill",
                "Subtle ankle movements control the direction",
                "Stay relaxed — tension makes this harder",
                "Think of it as surfing the snow sideways",
            ),
            source = "SkierLab / Effective Skiing",
        ),
        SkillDrill(
            id = "b05_fan_progression", level = SkillLevel.BEGINNER, category = SkillCategory.MIXED,
            title = "Fan Progression",
            description = "Gradually increase the angle you point your skis downhill with each turn. " +
                "Starts almost across the hill, progressively aiming more down the fall line.",
            howTo = "Start traversing across the slope. Make a turn back uphill (a garland). " +
                "Each time, point your skis a little more downhill before turning. Eventually you'll be " +
                "making full turns from the fall line.",
            terrain = "Green — gentle, groomed",
            reference = ReferenceMetrics(5f, 20f, 5f, 0f, 20f, 0.1f, 0.8f, 5f, 18f, 0f, 0.3f, 30f, 20, 65),
            passCriteria = "Complete 8 turns with progressively steeper entry angles",
            tips = listOf(
                "There's no rush — build comfort gradually",
                "Each turn should feel just slightly more challenging",
                "Keep balanced on your downhill foot",
                "This is how you build confidence for steeper terrain",
            ),
            source = "SkierLab Beginner Progression",
        ),

        // ════════════════════════════════════════════
        //  INTERMEDIATE DRILLS (Blue terrain, Kinetic Score 70–120)
        // ════════════════════════════════════════════

        SkillDrill(
            id = "i01_stem_christie", level = SkillLevel.INTERMEDIATE, category = SkillCategory.ROTARY,
            title = "Stem Christie",
            description = "Bridge from snowplow to parallel. Start each turn with a small wedge opening, " +
                "then match your inside ski parallel for the second half of the turn.",
            howTo = "Begin each turn with a small stem (wedge push of the uphill ski). As you cross the fall " +
                "line, match the inside ski parallel to the outside ski. The finish of each turn should be fully parallel.",
            terrain = "Blue — moderate, groomed",
            reference = ReferenceMetrics(10f, 28f, 15f, 10f, 30f, 0.3f, 1.2f, 10f, 28f, 10f, 0.45f, 38f, 55, 90),
            passCriteria = "8+ turns with turn shape ≥ 30, symmetry ≥ 45%",
            tips = listOf(
                "The stem is just the initiation — match skis as early as possible",
                "Focus on steering with your outside ski",
                "Keep your upper body facing downhill (separation)",
                "Each run, try to make the stem smaller",
            ),
            source = "PSIA Intermediate Progression",
        ),
        SkillDrill(
            id = "i02_hockey_stop", level = SkillLevel.INTERMEDIATE, category = SkillCategory.EDGING,
            title = "Hockey Stop",
            description = "A quick, aggressive stop by pivoting both skis sideways and setting edges. " +
                "Builds edge control and confidence.",
            howTo = "Build moderate speed in a straight run. Pivot both feet simultaneously to turn skis " +
                "perpendicular to your direction. Set edges firmly — shoulders stay facing downhill. " +
                "Practice both sides equally.",
            terrain = "Blue — moderate, groomed",
            reference = ReferenceMetrics(15f, 40f, 20f, 0f, 0f, 0.3f, 1.5f, 15f, 35f, 15f, 0.6f, 40f, 55, 95),
            passCriteria = "Stop within 3 meters, equal both sides, symmetry ≥ 60%",
            tips = listOf(
                "Turn your feet, not your upper body",
                "Tip skis on edge first to avoid catching an edge",
                "Shoulders and hips face downhill throughout",
                "Start slow, build speed as you gain confidence",
            ),
            source = "Effective Skiing / US Ski & Snowboard",
        ),
        SkillDrill(
            id = "i03_sideslip_edge_set", level = SkillLevel.INTERMEDIATE, category = SkillCategory.PRESSURE,
            title = "Sideslip with Edge Set",
            description = "Slide sideways down the fall line, controlling speed with edge angle. " +
                "Finish with a crisp edge set and pole plant. From the US Ski & Snowboard SkillsQuest.",
            howTo = "From a straight run, pivot both skis across the fall line simultaneously. Sideslip straight " +
                "down for 6 meters in a narrow corridor. Stop with a sharp edge set timed with a pole plant. " +
                "Hold still for 3 seconds. Repeat to both sides.",
            terrain = "Blue — moderate, groomed, consistent fall line",
            reference = ReferenceMetrics(10f, 30f, 10f, 0f, 0f, 0.2f, 1.0f, 5f, 20f, 10f, 0.5f, 40f, 50, 85),
            passCriteria = "Clean sideslip in corridor, edge set with pole plant, hold 3 seconds",
            tips = listOf(
                "Pivot both skis simultaneously — no stepping",
                "Stay in a ski-width corridor",
                "Pole plant must coincide with edge set",
                "Upper body stays quiet and faces downhill",
            ),
            source = "US Ski & Snowboard SkillsQuest Phase 2",
        ),
        SkillDrill(
            id = "i04_basic_parallel", level = SkillLevel.INTERMEDIATE, category = SkillCategory.MIXED,
            title = "Basic Parallel Turns",
            description = "Both skis stay parallel throughout the entire turn — no wedge at any point. " +
                "The gateway to real skiing.",
            howTo = "On a blue slope, roll both ankles simultaneously to tip skis on edge and initiate the turn. " +
                "Keep skis shoulder-width apart and parallel throughout. Use a pole plant to time each turn.",
            terrain = "Blue — moderate, groomed",
            reference = ReferenceMetrics(15f, 35f, 25f, 20f, 40f, 0.5f, 1.5f, 15f, 38f, 15f, 0.55f, 45f, 75, 110),
            passCriteria = "10+ parallel turns, edge angle ≥ 15°, turn shape ≥ 40, symmetry ≥ 55%",
            tips = listOf(
                "Tip both skis together — not one at a time",
                "Speed is your friend — too slow makes parallel harder",
                "Plant your pole to trigger each turn",
                "Keep your upper body quiet and facing downhill",
            ),
            source = "PSIA Intermediate Progression",
        ),
        SkillDrill(
            id = "i05_outside_ski_turns", level = SkillLevel.INTERMEDIATE, category = SkillCategory.EDGING,
            title = "Outside Ski Turns",
            description = "Ski medium-radius carved turns with all weight on the outside ski. " +
                "Inside ski lifted off the snow for the entire turn. From SkillsQuest Phase 2.",
            howTo = "Make 8 carved GS-radius turns with weight entirely on the outside ski. Lift the inside ski " +
                "completely off the snow. Between turns, step onto the new outside ski and traverse for 2 ski " +
                "lengths before initiating the next turn.",
            terrain = "Blue — moderate, groomed",
            reference = ReferenceMetrics(15f, 35f, 20f, 15f, 45f, 0.5f, 1.5f, 15f, 35f, 20f, 0.5f, 50f, 70, 110),
            passCriteria = "8 turns with inside ski off snow, carved arcs, consistent speed",
            tips = listOf(
                "Commit fully to the outside ski — trust it",
                "A deliberate weight transfer between turns",
                "Turn shape should be round, not Z-shaped",
                "Poles for planting only — no dragging for balance",
            ),
            source = "US Ski & Snowboard SkillsQuest Phase 2",
        ),
        SkillDrill(
            id = "i06_thousand_steps", level = SkillLevel.INTERMEDIATE, category = SkillCategory.BALANCE,
            title = "Thousand Steps",
            description = "Make turns by taking many small steps, transferring weight from ski to ski. " +
                "Develops balance, independent leg action, and dynamic movement.",
            howTo = "While skiing down a moderate slope, take 5-10 small steps per turn. Each step is a " +
                "tiny weight transfer from one foot to the other. Keep moving downhill while stepping. " +
                "Gradually reduce steps per turn as you improve.",
            terrain = "Blue — moderate, groomed",
            reference = ReferenceMetrics(10f, 25f, 15f, 10f, 30f, 0.3f, 1.0f, 10f, 25f, 15f, 0.5f, 45f, 65, 100),
            passCriteria = "8+ turns with continuous stepping, maintained speed",
            tips = listOf(
                "Small, quick steps — not big lunges",
                "Keep your upper body stable while legs work",
                "Works all 4 skills: balance, edging, rotary, and pressure",
                "This drill is used at every level — even Olympians do it",
            ),
            source = "US Ski & Snowboard Alpine Guide / SkillsQuest",
        ),
        SkillDrill(
            id = "i07_pivot_slips", level = SkillLevel.INTERMEDIATE, category = SkillCategory.ROTARY,
            title = "Pivot Slips",
            description = "From a straight run, pivot both skis 90° across the hill, sideslip, then pivot to " +
                "the other side — 4 sideslips total without stopping. SkillsQuest Phase 3 rotary drill.",
            howTo = "Start straight down the fall line for 15 meters. Pivot both skis sideways, sideslip for 6 " +
                "meters, then pivot to the other side. Complete 4 sideslips alternating direction. Finish " +
                "with an edge set and pole plant, hold 3 seconds.",
            terrain = "Blue — moderate, groomed, consistent fall line",
            reference = ReferenceMetrics(10f, 30f, 15f, 0f, 25f, 0.3f, 1.2f, 10f, 30f, 10f, 0.5f, 45f, 65, 100),
            passCriteria = "4 pivot slips in corridor, minimal speed loss, simultaneous pivot",
            tips = listOf(
                "Pivot both skis simultaneously — never step",
                "Stay in a ski-width corridor the whole time",
                "Lose minimal speed during sideslips",
                "Upper body stays quiet — only legs rotate",
            ),
            source = "US Ski & Snowboard SkillsQuest Phase 3",
        ),

        // ════════════════════════════════════════════
        //  ADVANCED DRILLS (Red/Blue terrain, Kinetic Score 100–145)
        // ════════════════════════════════════════════

        SkillDrill(
            id = "a01_dynamic_parallel", level = SkillLevel.ADVANCED, category = SkillCategory.PRESSURE,
            title = "Dynamic Parallel Turns",
            description = "Parallel turns with active flexion-extension, pole plants, and rhythmic transitions. " +
                "The turn has bounce — you feel the rebound.",
            howTo = "On a red slope, use active flexion (compress) at the end of each turn and extension (stretch) " +
                "at the transition. Plant your pole crisply at each initiation. Feel the rhythm.",
            terrain = "Red — steep, groomed",
            reference = ReferenceMetrics(20f, 45f, 35f, 30f, 50f, 0.8f, 2.0f, 20f, 50f, 30f, 0.6f, 52f, 95, 130),
            passCriteria = "10+ turns, edge ≥ 20°, weight release ≥ 30, G-force ≥ 0.8G",
            tips = listOf(
                "Flex your legs at turn end — feel compression",
                "Extend into the new turn — feel lightness",
                "Pole plant triggers each turn — make it crisp",
                "The rhythm should feel like bouncing on a trampoline",
            ),
            source = "PSIA Advanced Progression",
        ),
        SkillDrill(
            id = "a02_railroad_tracks", level = SkillLevel.ADVANCED, category = SkillCategory.EDGING,
            title = "Railroad Tracks (Carving Introduction)",
            description = "Leave clean, parallel arcs in the snow — two lines like railroad tracks. " +
                "No skidding or snow spray. Pure edge-to-edge carving.",
            howTo = "On a groomed blue slope at moderate speed, gradually roll your ankles and knees to " +
                "tip skis on edge. Let the sidecut of the ski do the turning. Look behind you — you should " +
                "see two clean lines, no skid marks.",
            terrain = "Blue/Red — groomed",
            reference = ReferenceMetrics(25f, 45f, 40f, 35f, 55f, 1.0f, 2.2f, 20f, 50f, 25f, 0.6f, 55f, 100, 135),
            passCriteria = "8+ turns leaving clean arcs, edge angle ≥ 25°, turn shape ≥ 55",
            tips = listOf(
                "Roll ankles, don't twist feet — tipping not steering",
                "Speed is required — carving needs centripetal force",
                "Check your tracks: clean lines = carving, spray = skidding",
                "Think 'motorcycle lean' — incline your whole body into the turn",
            ),
            source = "SKI Magazine / PSIA Carving Progression",
        ),
        SkillDrill(
            id = "a03_javelin_turns", level = SkillLevel.ADVANCED, category = SkillCategory.ROTARY,
            title = "Javelin Turns",
            description = "Lift the inside ski and point it forward like a javelin during each turn. " +
                "Forces commitment to the outside ski and develops hip angulation.",
            howTo = "At turn initiation, lift the inside ski and hold it forward and to the inside at an angle. " +
                "Balance entirely on the outside ski. Steer the outside ski underneath the lifted ski. " +
                "Set down at transition and switch.",
            terrain = "Blue/Red — groomed",
            reference = ReferenceMetrics(20f, 45f, 30f, 25f, 45f, 0.7f, 1.8f, 15f, 40f, 25f, 0.55f, 55f, 90, 130),
            passCriteria = "8+ turns, inside ski fully off snow, round carved turns",
            tips = listOf(
                "The lifted ski acts like a training wheel — shows your angulation",
                "Tap inside pole to help initiate the turn",
                "Focus on steering the outside ski, not the lifted one",
                "Hip angulation naturally develops from this drill",
            ),
            source = "Snowelab / PSIA Assessment",
        ),
        SkillDrill(
            id = "a04_one_ski_skiing", level = SkillLevel.ADVANCED, category = SkillCategory.BALANCE,
            title = "One Ski Skiing",
            description = "Remove one ski entirely and make 8 linked medium-radius turns on a single ski. " +
                "The ultimate balance and edging test. From SkillsQuest Phase 3.",
            howTo = "Wear only one ski. Make 8 linked 15-18m radius turns. Keep the free foot completely off " +
                "the snow, no swinging. Poles for planting only, never for balance. Repeat on the other leg.",
            terrain = "Blue — moderate, groomed",
            reference = ReferenceMetrics(15f, 40f, 25f, 20f, 40f, 0.5f, 1.5f, 10f, 30f, 15f, 0.5f, 55f, 80, 125),
            passCriteria = "8 turns each leg, free foot never touches snow, carved round turns",
            tips = listOf(
                "Free boot stays still — no swinging for balance",
                "Poles are for timing, not support",
                "Speed should be consistent throughout",
                "This drill eliminates the crutch of your inside ski",
            ),
            source = "US Ski & Snowboard SkillsQuest Phase 3",
        ),
        SkillDrill(
            id = "a05_lane_changes", level = SkillLevel.ADVANCED, category = SkillCategory.MIXED,
            title = "Lane Changes (Freeski)",
            description = "Ski 3 short turns in one corridor, traverse to a parallel corridor, " +
                "and repeat — 6 sets total. Tests rhythm, precision, and adaptability. SkillsQuest Phase 3.",
            howTo = "In a 5m-wide corridor, make 3 short carved turns with consistent rhythm and speed. " +
                "Then traverse across to a parallel 5m corridor and repeat. 6 sections of 3 turns, " +
                "connected by 5 traverses. Pole plant on every turn.",
            terrain = "Blue — moderate, groomed",
            reference = ReferenceMetrics(18f, 38f, 30f, 25f, 50f, 0.6f, 1.5f, 15f, 35f, 20f, 0.6f, 50f, 85, 125),
            passCriteria = "6 sections, consistent rhythm, carved round turns, lane discipline",
            tips = listOf(
                "Same turn shape in every corridor",
                "Lane change turn should match the radius of corridor turns",
                "Consistent speed throughout — no acceleration in corridors",
                "Pole swing coincides with edge release",
            ),
            source = "US Ski & Snowboard SkillsQuest Phase 3",
        ),
        SkillDrill(
            id = "a06_hop_turns", level = SkillLevel.ADVANCED, category = SkillCategory.ROTARY,
            title = "Hop Turns",
            description = "Jump and pivot both skis in alternating directions, landing on edge each time. " +
                "15 consecutive hops. Develops explosive rotary skill and edge precision. SkillsQuest Phase 5.",
            howTo = "From a traverse, jump with both skis, pivot them in the air to the opposite direction, " +
                "and land on edge. Immediately jump again to the other side. 15 hops total. " +
                "The track in the snow looks like a series of Z's. Finish with an edge set.",
            terrain = "Blue — moderate, groomed",
            reference = ReferenceMetrics(15f, 35f, 20f, 0f, 30f, 0.5f, 1.8f, 8f, 25f, 30f, 0.65f, 50f, 85, 125),
            passCriteria = "15 hops, consistent rhythm, land on edge, Z pattern in snow",
            tips = listOf(
                "Skis parallel to snow surface in the air — no tips or tails up",
                "Land on edge immediately — no flat landing",
                "Consistent rhythm with no pauses",
                "Upper body stays facing downhill — only legs rotate",
            ),
            source = "US Ski & Snowboard SkillsQuest Phase 5",
        ),
        SkillDrill(
            id = "a07_carving_medium", level = SkillLevel.ADVANCED, category = SkillCategory.EDGING,
            title = "Medium Radius Carving",
            description = "Clean carved turns at GS radius with high edge angles. " +
                "Focus on early edging, progressive edge build, and edge similarity.",
            howTo = "On a red groomed slope, make GS-radius carved turns (15-20m). Focus on toppling " +
                "into each turn early, building edge angle progressively through the turn, and matching " +
                "edge angles between left and right turns.",
            terrain = "Red — steep, groomed",
            reference = ReferenceMetrics(30f, 55f, 45f, 40f, 55f, 1.2f, 2.5f, 25f, 55f, 35f, 0.65f, 58f, 110, 140),
            passCriteria = "8+ turns, edge ≥ 30°, early edging ≥ 45, progressive edge ≥ 40",
            tips = listOf(
                "Trust the ski — lean in and let it carve",
                "Topple into the turn like a motorcycle rider",
                "Don't park and ride — keep building edge through the turn",
                "Check tracks: clean arcs, no skid marks",
            ),
            source = "PSIA Advanced Carving",
        ),
        SkillDrill(
            id = "a08_compression_turns", level = SkillLevel.ADVANCED, category = SkillCategory.PRESSURE,
            title = "Compression Turns",
            description = "Absorb terrain changes by compressing (flexing legs) over bumps and extending " +
                "in troughs. Simulates mogul skiing and develops pressure management.",
            howTo = "On variable terrain or a mogul field, flex your legs dramatically as you go over each bump " +
                "(pull knees to chest) and extend in the troughs. Keep your upper body at a constant height " +
                "above the snow — only your legs move up and down.",
            terrain = "Blue/Red — variable terrain or small moguls",
            reference = ReferenceMetrics(15f, 35f, 20f, 15f, 35f, 0.5f, 2.0f, 15f, 40f, 35f, 0.55f, 50f, 90, 130),
            passCriteria = "8+ turns over terrain, upper body stable, consistent speed",
            tips = listOf(
                "Your legs are shock absorbers — head stays level",
                "Absorb on the way up, extend on the way down",
                "Keep hands forward and pole plants timed",
                "Speed control comes from turn shape, not braking",
            ),
            source = "Stomp It Tutorials / PSIA Mogul Progression",
        ),

        // ════════════════════════════════════════════
        //  EXPERT DRILLS (Black terrain, Kinetic Score 130+)
        // ════════════════════════════════════════════

        SkillDrill(
            id = "e01_short_radius_carve", level = SkillLevel.EXPERT, category = SkillCategory.EDGING,
            title = "Short Radius Carving",
            description = "Tight, quick carved turns with high edge angles and strong G-forces. " +
                "Quick edge-to-edge transitions using rebound energy.",
            howTo = "On a steep groomed slope, make short-radius carved turns. Get on edge early, " +
                "build edge angle progressively through the turn, and use the rebound to power the next turn. " +
                "Leave tight, clean arcs in the snow.",
            terrain = "Black — steep, groomed",
            reference = ReferenceMetrics(35f, 65f, 55f, 50f, 60f, 1.5f, 3.5f, 30f, 65f, 45f, 0.7f, 62f, 125, 160),
            passCriteria = "10+ turns, edge ≥ 35°, early edging ≥ 55, G-force ≥ 1.5G",
            tips = listOf(
                "Early edge engagement is critical — topple into each turn",
                "Progressive edge build: dimmer switch, not light switch",
                "Strong pole plant triggers the quick transition",
                "Inside hand leads down the fall line",
            ),
            source = "Ted Ligety / PSIA Expert Methodology",
        ),
        SkillDrill(
            id = "e02_dynamic_carving", level = SkillLevel.EXPERT, category = SkillCategory.MIXED,
            title = "Dynamic Carving — The Full Package",
            description = "Expert-level carving with maximum edge angles, explosive transitions, high G-forces, " +
                "and complete turn closure. This is what turns heads under the chairlift.",
            howTo = "On steep groomers, fully commit. High edge angles, progressive edge build through the " +
                "entire turn, dynamic unweighting at transitions, and complete S-shaped turn closure. " +
                "You should feel like you're on a rollercoaster.",
            terrain = "Black — steep, groomed",
            reference = ReferenceMetrics(45f, 78f, 65f, 60f, 65f, 2.0f, 4.5f, 35f, 80f, 55f, 0.75f, 68f, 140, 200),
            passCriteria = "8+ turns, edge ≥ 45°, G-force ≥ 2.0G, progressive edge ≥ 60, Kinetic Score ≥ 140",
            tips = listOf(
                "Sustain and increase edge angle through the turn",
                "Use rebound energy to explode into the next turn",
                "Both skis on matching edge angles (edge similarity)",
                "Body creates a C-shape: hips inside, shoulders outside",
            ),
            source = "Ted Ligety / PSIA Expert Methodology",
        ),
        SkillDrill(
            id = "e03_one_ski_hourglass", level = SkillLevel.EXPERT, category = SkillCategory.BALANCE,
            title = "One Ski Hourglass",
            description = "On one ski, gradually decrease turn radius from GS to slalom at the midpoint, " +
                "then gradually increase back to GS. The ultimate balance and edging test. SkillsQuest Phase 6.",
            howTo = "Wearing one ski on expert terrain, start with GS-sized turns. Gradually shrink the radius " +
                "over 5 turns until you reach slalom-sized turns at the midpoint. Then gradually increase back " +
                "to GS over 5 more turns. 10 turns total. Repeat on each leg.",
            terrain = "Black — steep, groomed",
            reference = ReferenceMetrics(20f, 50f, 35f, 30f, 55f, 0.8f, 2.5f, 15f, 45f, 30f, 0.6f, 60f, 120, 160),
            passCriteria = "10 turns each leg, symmetric hourglass, carved arcs, no boot touches",
            tips = listOf(
                "Free boot never touches the snow",
                "Gradual radius changes — no abrupt jumps",
                "Hourglass symmetric left-to-right and top-to-bottom",
                "This is the drill that separates experts from the rest",
            ),
            source = "US Ski & Snowboard SkillsQuest Phase 6",
        ),
        SkillDrill(
            id = "e04_mogul_v_corridor", level = SkillLevel.EXPERT, category = SkillCategory.PRESSURE,
            title = "Moguls — V Corridor",
            description = "In a mogul field, start with large 20-24m turns and gradually decrease radius over " +
                "10 turns until matching individual mogul-sized turns. SkillsQuest Phase 6.",
            howTo = "In expert mogul terrain, begin with long-radius turns (20-24m). Gradually decrease " +
                "turn radius with each turn. By turn 10, your turns should match the size of individual moguls. " +
                "Maintain high speed and round turn shape throughout.",
            terrain = "Black — moguls",
            reference = ReferenceMetrics(15f, 40f, 25f, 20f, 45f, 0.8f, 3.0f, 20f, 50f, 45f, 0.6f, 55f, 125, 165),
            passCriteria = "10 turns, gradual radius decrease, high speed, balanced",
            tips = listOf(
                "Speed is crucial — don't slow down too much",
                "Balance in all three planes (fore-aft, lateral, rotational)",
                "Turns linked without traverse",
                "Round, symmetric turns left-to-right",
            ),
            source = "US Ski & Snowboard SkillsQuest Phase 6",
        ),
        SkillDrill(
            id = "e05_varied_terrain_freeski", level = SkillLevel.EXPERT, category = SkillCategory.BALANCE,
            title = "Variable Terrain Freeski",
            description = "Ski 10 turns of consistent 20-22m radius on expert terrain with variable slope angle, " +
                "side hills, and non-groomed snow. SkillsQuest Phase 5.",
            howTo = "On expert terrain (ungroomed, variable pitch, side hills), maintain a consistent 20-22m " +
                "turn radius for 10 turns. Link turns with minimal traversing. Any air time should be controlled " +
                "with skis paralleling the slope angle.",
            terrain = "Black — variable, ungroomed",
            reference = ReferenceMetrics(20f, 45f, 30f, 25f, 50f, 1.0f, 3.0f, 25f, 55f, 35f, 0.65f, 55f, 120, 160),
            passCriteria = "10 turns, consistent radius, high speed, controlled air time",
            tips = listOf(
                "Consistent turn size regardless of terrain changes",
                "Minimal traversing — stay in the fall line",
                "Any air time: skis parallel to landing slope",
                "High rate of speed throughout",
            ),
            source = "US Ski & Snowboard SkillsQuest Phase 5",
        ),
        SkillDrill(
            id = "e06_white_pass_turns", level = SkillLevel.EXPERT, category = SkillCategory.EDGING,
            title = "White Pass Turns",
            description = "Linked large-radius turns done primarily on one ski, with the outside ski lifted at " +
                "initiation and completion, and edge change performed on the inside ski. PSIA Level 3 drill.",
            howTo = "Lift the outside ski at turn initiation (inside ski on its inside edge). Set the outside ski " +
                "down during the shaping phase. Lift it again before the finish. Perform edge change while " +
                "on the new inside ski. Carved, not steered.",
            terrain = "Blue/Red — groomed",
            reference = ReferenceMetrics(25f, 50f, 35f, 30f, 55f, 0.8f, 2.2f, 20f, 45f, 30f, 0.6f, 60f, 115, 155),
            passCriteria = "8+ turns, proper lift timing, carved arcs, pressure and edge control",
            tips = listOf(
                "Outside ski lifts at initiation and completion phases",
                "Edge change happens on the inside ski",
                "Track should be more carved than steered",
                "Develops advanced pressure control and timing",
            ),
            source = "PSIA-C / Level 3 Assessment",
        ),
    )

    fun getDrill(id: String): SkillDrill = drills.first { it.id == id }

    fun getDrillsByLevel(level: SkillLevel): List<SkillDrill> = drills.filter { it.level == level }

    /**
     * Compare actual run metrics against a drill's reference to produce
     * per-metric grades and an overall pass/fail.
     */
    fun evaluateRun(metrics: SkiMetrics, drill: SkillDrill): DrillResult {
        val ref = drill.reference
        val grades = mutableListOf<MetricGrade>()

        grades.add(gradeRange("Edge Angle", metrics.edgeAngle, ref.edgeAngleMin, ref.edgeAngleMax, "°"))
        grades.add(gradeMin("Early Edging", metrics.earlyEdging, ref.earlyEdgingMin))
        grades.add(gradeMin("Progressive Edge", metrics.progressiveEdgeBuild, ref.progressiveEdgeMin))
        grades.add(gradeMin("Turn Shape", metrics.turnShape, ref.turnShapeMin))
        grades.add(gradeRange("G-Force", metrics.gForce, ref.gForceMin, ref.gForceMax, "G"))
        grades.add(gradeRange("Speed", metrics.maxSpeedKmh, ref.speedMinKmh, ref.speedMaxKmh, "km/h"))
        grades.add(gradeMin("Weight Release", metrics.transitionWeightRelease, ref.weightReleaseMin))
        grades.add(gradeMin("Turn Symmetry", metrics.turnSymmetry * 100f, ref.turnSymmetryMin * 100f))
        grades.add(gradeMin("Centered Balance", metrics.centeredBalance, ref.centeredBalanceMin))

        val passCount = grades.count { it.status == GradeStatus.PASS || it.status == GradeStatus.EXCELLENT }
        val overallScore = (passCount.toFloat() / grades.size * 100f).toInt()
        val passed = overallScore >= 70

        return DrillResult(
            drill = drill,
            grades = grades,
            overallScore = overallScore,
            passed = passed,
        )
    }

    private fun gradeMin(name: String, actual: Float, min: Float): MetricGrade {
        val status = when {
            min <= 0f -> GradeStatus.PASS
            actual >= min * 1.2f -> GradeStatus.EXCELLENT
            actual >= min -> GradeStatus.PASS
            actual >= min * 0.7f -> GradeStatus.CLOSE
            else -> GradeStatus.NEEDS_WORK
        }
        return MetricGrade(name, actual, min, null, status)
    }

    private fun gradeRange(name: String, actual: Float, min: Float, max: Float, unit: String): MetricGrade {
        val status = when {
            actual in min..max -> GradeStatus.PASS
            actual > max && actual <= max * 1.3f -> GradeStatus.EXCELLENT
            actual >= min * 0.7f && actual < min -> GradeStatus.CLOSE
            actual > max * 1.3f -> GradeStatus.EXCELLENT
            else -> GradeStatus.NEEDS_WORK
        }
        return MetricGrade(name, actual, min, max, status, unit)
    }
}

enum class GradeStatus(val label: String, val emoji: String) {
    EXCELLENT("Excellent", "★"),
    PASS("Pass", "✓"),
    CLOSE("Almost", "◐"),
    NEEDS_WORK("Needs Work", "✗"),
}

data class MetricGrade(
    val metricName: String,
    val actual: Float,
    val referenceMin: Float,
    val referenceMax: Float?,
    val status: GradeStatus,
    val unit: String = "",
)

data class DrillResult(
    val drill: SkillDrill,
    val grades: List<MetricGrade>,
    val overallScore: Int,
    val passed: Boolean,
)
