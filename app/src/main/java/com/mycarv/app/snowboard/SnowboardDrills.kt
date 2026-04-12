package com.mycarv.app.snowboard

/**
 * 24 snowboard training drills from beginner to expert.
 *
 * Sources:
 *   - AASI (American Association of Snowboard Instructors) progression
 *   - CASI (Canadian Association of Snowboard Instructors)
 *   - Stomp It Tutorials snowboard curriculum
 *   - Professional snowboard coaching methodology
 *
 * AASI 6 core competencies:
 *   1. Fore-aft pressure along the board
 *   2. Lateral pressure across the board
 *   3. Pressure magnitude
 *   4. Board tilt (edge angle)
 *   5. Board pivot (rotation)
 *   6. Board twist (torsional flex)
 */

enum class SnowboardLevel(val displayName: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"),
    EXPERT("Expert"),
}

enum class SnowboardSkillCategory { EDGE_CONTROL, BOARD_CONTROL, PRESSURE, BALANCE, FREESTYLE }

data class SnowboardDrill(
    val id: String,
    val level: SnowboardLevel,
    val category: SnowboardSkillCategory,
    val title: String,
    val description: String,
    val howTo: String,
    val terrain: String,
    val rideIQMin: Int,
    val rideIQMax: Int,
    val tips: List<String>,
    val source: String,
)

object SnowboardDrillsData {

    val drills: List<SnowboardDrill> = listOf(

        // ════════════════════════════════════════
        //  BEGINNER (6 drills)
        // ════════════════════════════════════════

        SnowboardDrill("sb_b01", SnowboardLevel.BEGINNER, SnowboardSkillCategory.BALANCE,
            "Skating & Gliding",
            "Move across flat terrain with one foot strapped in. Push with your free foot like a skateboard.",
            "Strap in your lead foot only. Push with your back foot to glide. Practice stopping by pointing the board uphill.",
            "Flat — gentle area near base", 0, 40,
            listOf("Keep weight over your lead foot", "Look where you want to go, not at the board", "Small pushes, build confidence"),
            "AASI Level I Progression",
        ),
        SnowboardDrill("sb_b02", SnowboardLevel.BEGINNER, SnowboardSkillCategory.EDGE_CONTROL,
            "Heelside Sideslip",
            "Slide sideways down the slope on your heelside edge, controlling speed by adjusting edge angle.",
            "Face uphill with the board perpendicular to the fall line. Slowly flatten the board to slide, then re-engage the heelside edge to stop. Control your speed with edge pressure.",
            "Green — gentle, groomed", 10, 50,
            listOf("Keep your weight centered over the board", "Lift your toes slightly to engage the heelside", "Look over your downhill shoulder", "Knees bent, hips forward"),
            "AASI Level I Progression",
        ),
        SnowboardDrill("sb_b03", SnowboardLevel.BEGINNER, SnowboardSkillCategory.EDGE_CONTROL,
            "Toeside Sideslip",
            "Slide sideways down the slope on your toeside edge. Harder than heelside because you face uphill.",
            "Face downhill with the board perpendicular. Press your toes and shins into the boot to hold the edge. Slowly release to slide, then re-engage to stop.",
            "Green — gentle, groomed", 10, 50,
            listOf("Press shins into the front of your boots", "Keep your head up — don't look at the board", "Hands forward for balance", "This feels scarier but builds essential skill"),
            "AASI Level I Progression",
        ),
        SnowboardDrill("sb_b04", SnowboardLevel.BEGINNER, SnowboardSkillCategory.BOARD_CONTROL,
            "Falling Leaf",
            "Slide diagonally across the slope on one edge, like a leaf falling from a tree. Switch direction without changing edges.",
            "On your heelside edge, shift weight to your lead foot to slide forward-diagonal. Then shift to your back foot to slide backward-diagonal. Repeat across the slope. Then do the same on toeside.",
            "Green — gentle, groomed", 20, 55,
            listOf("Weight shift controls direction — no edge change needed", "Keep the edge engaged the whole time", "Small weight shifts, big results", "Master this on both heelside and toeside"),
            "AASI/CASI Beginner Progression",
        ),
        SnowboardDrill("sb_b05", SnowboardLevel.BEGINNER, SnowboardSkillCategory.BOARD_CONTROL,
            "J-Turns (Heelside)",
            "Start pointing downhill, make a single turn to stop on your heelside edge. The track looks like a J.",
            "Point the board straight down a gentle slope. As you gain speed, shift weight to your heels and rotate your lead shoulder uphill. The board will arc into a heelside turn and you'll stop across the hill.",
            "Green — gentle, groomed", 20, 55,
            listOf("Start slow — you need barely any speed", "Lead with your front shoulder", "The board does the turning if you commit to the edge", "Practice until stopping feels automatic"),
            "AASI Level I Progression",
        ),
        SnowboardDrill("sb_b06", SnowboardLevel.BEGINNER, SnowboardSkillCategory.BOARD_CONTROL,
            "J-Turns (Toeside)",
            "Same as heelside J-turn but finishing on the toeside edge. Harder because you end up facing uphill.",
            "Point downhill, shift weight to your toes, press shins forward, and rotate your lead shoulder to turn across the hill on your toeside edge.",
            "Green — gentle, groomed", 25, 60,
            listOf("Commit to the toeside — press those shins!", "Look where you want to go (uphill)", "Keep your back straight, don't bend at the waist", "This is the foundation for linked turns"),
            "AASI Level I Progression",
        ),

        // ════════════════════════════════════════
        //  INTERMEDIATE (6 drills)
        // ════════════════════════════════════════

        SnowboardDrill("sb_i01", SnowboardLevel.INTERMEDIATE, SnowboardSkillCategory.BOARD_CONTROL,
            "Linked Skidded Turns",
            "Connect heelside and toeside turns without stopping. The board skids through each turn.",
            "Make a heelside J-turn, then as you cross the fall line, flatten the board and transition to a toeside turn. Keep linking turns down the slope. Speed control comes from completing each turn across the hill.",
            "Blue — moderate, groomed", 50, 90,
            listOf("The transition (flat board) is the scary moment — commit through it", "Speed is controlled by turn shape, not braking", "Consistent rhythm — don't rush", "Look ahead two turns, not at your feet"),
            "AASI Level I-II Progression",
        ),
        SnowboardDrill("sb_i02", SnowboardLevel.INTERMEDIATE, SnowboardSkillCategory.EDGE_CONTROL,
            "Garlands (Heelside)",
            "Make a series of partial turns on one edge without fully crossing the fall line. Builds edge confidence.",
            "Traverse on your heelside edge. Flatten the board slightly to dip toward the fall line, then re-engage the edge to arc back across the hill. Repeat 5-6 times across the slope.",
            "Blue — moderate, groomed", 55, 90,
            listOf("Never fully cross the fall line — stay on one edge", "Each dip should be a little deeper than the last", "Feel how flattening the board increases speed", "This teaches edge pressure modulation"),
            "AASI/CASI Intermediate Progression",
        ),
        SnowboardDrill("sb_i03", SnowboardLevel.INTERMEDIATE, SnowboardSkillCategory.EDGE_CONTROL,
            "Garlands (Toeside)",
            "Same as heelside garlands but on your toeside edge. Builds toeside confidence.",
            "Traverse toeside, dip toward the fall line by relaxing your toes, then re-engage by pressing shins forward. Stay on toeside the whole time.",
            "Blue — moderate, groomed", 55, 90,
            listOf("Keep your head up — tendency is to look down on toeside", "Press shins, not just toes", "Feel the edge grip as you re-engage", "Equal confidence on both edges is the goal"),
            "AASI/CASI Intermediate Progression",
        ),
        SnowboardDrill("sb_i04", SnowboardLevel.INTERMEDIATE, SnowboardSkillCategory.PRESSURE,
            "Speed Check Turns",
            "Make quick, aggressive turns to control speed on steeper terrain. Emphasis on finishing each turn.",
            "On a blue slope, make dynamic linked turns with emphasis on closing each turn across the fall line. Each turn should significantly reduce your speed before you initiate the next.",
            "Blue — moderate to steep, groomed", 60, 100,
            listOf("Complete the turn! Don't bail out early", "Flex and extend through each turn", "Upper body stays quiet, facing downhill", "This is about speed management, not maximum edge"),
            "AASI Level II",
        ),
        SnowboardDrill("sb_i05", SnowboardLevel.INTERMEDIATE, SnowboardSkillCategory.BALANCE,
            "One-Foot Riding",
            "Ride with only your lead foot strapped in. Builds balance and board awareness.",
            "Unstrap your back foot and rest it on the stomp pad. Ride gentle terrain making turns using only your front foot. Your back foot balances on the board but doesn't steer.",
            "Green/Blue — gentle to moderate", 50, 90,
            listOf("Weight stays over the front foot", "Back foot is for balance only — don't push with it", "This reveals how much you rely on your back foot", "Essential for getting on/off lifts smoothly"),
            "AASI/CASI Balance Development",
        ),
        SnowboardDrill("sb_i06", SnowboardLevel.INTERMEDIATE, SnowboardSkillCategory.BOARD_CONTROL,
            "Switch Riding (Basics)",
            "Ride with your non-dominant foot forward. Regular riders go goofy, goofy riders go regular.",
            "On gentle terrain, try linked turns in your non-natural stance. Everything will feel backwards. Start with sideslips, then falling leaf, then linked turns.",
            "Green — gentle, groomed", 40, 85,
            listOf("Go back to basics — sideslip first in switch", "It feels terrible at first, that's normal", "Switch riding doubles your ability and unlocks freestyle", "Practice 10 minutes every session"),
            "AASI/CASI Level II Progression",
        ),

        // ════════════════════════════════════════
        //  ADVANCED (6 drills)
        // ════════════════════════════════════════

        SnowboardDrill("sb_a01", SnowboardLevel.ADVANCED, SnowboardSkillCategory.EDGE_CONTROL,
            "Basic Carving",
            "Pure carved turns — the board rides on its edge without skidding. Clean arcs in the snow.",
            "On a groomed blue slope at moderate speed, roll the board onto its edge and let the sidecut do the turning. No skidding. You should see a single clean line in the snow, not a wide scraped path.",
            "Blue/Red — groomed", 85, 130,
            listOf("Speed is required — carving needs centripetal force", "Tip the board, don't twist it", "Look for clean railroad tracks in the snow behind you", "Inclination: lean your whole body into the turn like a motorcycle"),
            "AASI Level II-III / Carving Progression",
        ),
        SnowboardDrill("sb_a02", SnowboardLevel.ADVANCED, SnowboardSkillCategory.EDGE_CONTROL,
            "Dynamic Carving",
            "Aggressive carved turns with high edge angles, active flex/extend, and cross-through transitions.",
            "On a red slope, carve with commitment. High edge angles, flex into the turn, extend to transition. Feel the board bend under load. The G-forces should press you into the snow.",
            "Red — steep, groomed", 100, 145,
            listOf("Commit! Half-measures don't work in carving", "Flex deep in the turn, extend tall in transition", "Your body should feel G-forces in every turn", "Both edges should feel equally powerful"),
            "AASI Level III / Advanced Carving",
        ),
        SnowboardDrill("sb_a03", SnowboardLevel.ADVANCED, SnowboardSkillCategory.BOARD_CONTROL,
            "Short Radius Turns",
            "Quick, rhythmic turns with fast edge-to-edge transitions. Essential for steep terrain control.",
            "On a steep groomed slope, make tight linked turns with rapid edge changes. The board should pivot quickly through each turn. Upper body stays facing downhill while the board turns underneath.",
            "Red/Black — steep, groomed", 95, 140,
            listOf("Upper body faces downhill — only your lower body turns", "Quick, light feet — don't fight the board", "The rhythm should be consistent and rapid", "Use flexion-extension to lighten the board for quick pivots"),
            "AASI Level III",
        ),
        SnowboardDrill("sb_a04", SnowboardLevel.ADVANCED, SnowboardSkillCategory.FREESTYLE,
            "Ollies & Nollies",
            "Jump off the tail (ollie) or nose (nollie) of the board using board flex as a spring.",
            "Shift weight to your back foot, flex the tail down, then spring up by extending your back leg while pulling your front knee up. For nollies, reverse — load the nose and spring from the front.",
            "Blue — moderate, groomed or park", 80, 130,
            listOf("Load the tail/nose by shifting weight — the board stores energy like a spring", "Pop is about timing, not strength", "Pull your knees up after the pop", "Practice on flat ground first"),
            "Snowboard Park Progression",
        ),
        SnowboardDrill("sb_a05", SnowboardLevel.ADVANCED, SnowboardSkillCategory.FREESTYLE,
            "Butters & Presses",
            "Balance on the nose or tail while the other end lifts off the snow. Foundation for ground tricks.",
            "Shift weight fully to your front foot, lifting the tail off the snow (nose press). Hold the balance. Then try tail press — weight on back foot, nose lifted. For butters, add rotation while pressing.",
            "Blue — moderate, groomed", 80, 125,
            listOf("Core strength is everything for presses", "Start with small lifts, gradually go higher", "Butters = press + rotation = pure style", "Film yourself — small presses look bigger than they feel"),
            "Stomp It / Snowboard Park Progression",
        ),
        SnowboardDrill("sb_a06", SnowboardLevel.ADVANCED, SnowboardSkillCategory.PRESSURE,
            "Mogul Riding",
            "Navigate mogul fields by absorbing bumps with your legs and controlling speed with turn shape.",
            "In a mogul field, flex your legs dramatically over each bump (pull knees to chest) and extend in troughs. Make short turns around the bumps, not over them. Speed control comes from turn shape.",
            "Black — moguls", 90, 140,
            listOf("Legs are shock absorbers — head stays at constant height", "Turn around the bumps, not over them", "Short radius turns are mandatory in moguls", "Start on small moguls and work up"),
            "AASI Level III / All-Mountain",
        ),

        // ════════════════════════════════════════
        //  EXPERT (6 drills)
        // ════════════════════════════════════════

        SnowboardDrill("sb_e01", SnowboardLevel.EXPERT, SnowboardSkillCategory.EDGE_CONTROL,
            "Euro-Carving (Laydown Turns)",
            "Extreme carving where your body nearly touches the snow. Maximum edge angles, maximum G-forces.",
            "On a steep groomed slope, carve with enough speed and edge angle that your hip/hand nearly touches the snow. Full body inclination. This requires massive speed, commitment, and strength.",
            "Red/Black — steep, wide, groomed", 130, 200,
            listOf("You need serious speed for this — don't attempt slowly", "Full body inclination — like laying down into the turn", "Hand drag on the snow = you're doing it right", "Edge angles above 60° — the ski equivalent of laying it over"),
            "Expert Carving / Extreme Carving Community",
        ),
        SnowboardDrill("sb_e02", SnowboardLevel.EXPERT, SnowboardSkillCategory.FREESTYLE,
            "Frontside 360",
            "Full rotation in the air — take off, spin 360°, and land clean riding forward.",
            "Approach a jump or natural feature with moderate speed. Pop off the lip, initiate rotation with your lead shoulder, spot your landing at 270°, and stomp it. Start on small jumps.",
            "Park — medium jump or side hit", 110, 170,
            listOf("Wind up with your shoulders before the lip", "Pop THEN spin — don't spin on the lip", "Spot your landing at 270° — look over your lead shoulder", "Commit to the full rotation — half-spinning is more dangerous"),
            "Park Progression / Freestyle",
        ),
        SnowboardDrill("sb_e03", SnowboardLevel.EXPERT, SnowboardSkillCategory.FREESTYLE,
            "Switch Carving",
            "Carve clean turns while riding switch (non-dominant stance). The true test of board mastery.",
            "Ride switch on groomed terrain and make clean carved turns. No skidding. This requires equal toeside and heelside confidence in your non-natural stance.",
            "Blue/Red — groomed", 120, 160,
            listOf("If you can carve switch, you can ride anything", "Start with wide turns, gradually tighten", "Switch carving unlocks all switch freestyle", "Practice until switch feels 80% as good as regular"),
            "AASI Level III / All-Mountain",
        ),
        SnowboardDrill("sb_e04", SnowboardLevel.EXPERT, SnowboardSkillCategory.BALANCE,
            "Steep Terrain Mastery",
            "Ride confidently on expert terrain with variable conditions — steeps, ice, wind-affected snow.",
            "On black diamond terrain, maintain consistent turn shape and speed control regardless of conditions. Variable snow, wind crust, ice patches — adapt and keep riding smoothly.",
            "Black — steep, variable", 120, 170,
            listOf("Read the terrain ahead — anticipate changes", "Extra knee flex for absorption on variable snow", "Speed control through completed turns, not braking", "Confidence comes from thousands of turns, not from tips"),
            "AASI Level III / Expert All-Mountain",
        ),
        SnowboardDrill("sb_e05", SnowboardLevel.EXPERT, SnowboardSkillCategory.PRESSURE,
            "Powder Riding",
            "Float and surf through deep powder. Different technique from groomed riding.",
            "In deep snow, shift weight slightly back (60/40 rear) to keep the nose up. Use a wider stance. Make flowing, medium-radius turns with continuous motion. Never stop in powder.",
            "Off-piste — deep powder", 110, 160,
            listOf("Weight back! Nose must stay above the snow", "Never stop — momentum is everything in powder", "Bouncing motion — press and release, press and release", "The deepest powder is the most forgiving — commit"),
            "All-Mountain / Powder Riding",
        ),
        SnowboardDrill("sb_e06", SnowboardLevel.EXPERT, SnowboardSkillCategory.FREESTYLE,
            "Freestyle Combo Run",
            "Link tricks, presses, carves, and switch riding into one flowing creative run.",
            "Ride a full run combining: carved turns → ollie → nose press → switch carving → 180 → ride out. The run should flow continuously without stops. Maximum style and creativity.",
            "Blue/Red — groomed or park", 130, 200,
            listOf("The goal is flow — everything connects smoothly", "Use the terrain creatively (side hits, rollers, banks)", "Film it — your best runs deserve to be seen", "This is what snowboarding is all about — expression"),
            "Freestyle / Creative Riding",
        ),
    )

    fun getDrillsByLevel(level: SnowboardLevel) = drills.filter { it.level == level }
    fun getDrill(id: String) = drills.first { it.id == id }
}
