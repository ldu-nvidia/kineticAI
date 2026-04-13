package com.kineticai.app.xcski

/**
 * Cross-country skiing drill library — 20 drills across classic and skate,
 * beginner through expert, sourced from CXC (Central Cross Country),
 * US Ski & Snowboard Nordic, and PSIA Nordic certification materials.
 */
data class XCDrill(
    val id: String,
    val title: String,
    val description: String,
    val howTo: String,
    val style: XCDrillStyle,
    val level: XCDrillLevel,
    val category: XCDrillCategory,
    val targetCycleRate: IntRange?,
    val targetGlideRatio: Float?,
    val tips: List<String>,
    val source: String,
)

enum class XCDrillStyle { CLASSIC, SKATE, BOTH }
enum class XCDrillLevel(val displayName: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"),
    EXPERT("Expert")
}
enum class XCDrillCategory(val displayName: String) {
    GLIDE("Glide & Balance"),
    POWER("Power & Cadence"),
    TECHNIQUE("Technique"),
    ENDURANCE("Endurance & Pacing"),
}

object XCSkiDrills {

    val allDrills: List<XCDrill> = listOf(
        // ────────────────────────────────────────────────────
        // CLASSIC — BEGINNER
        // ────────────────────────────────────────────────────
        XCDrill(
            id = "xc_c01", title = "Straight Glide (No Poles)",
            description = "Glide on one ski at a time on flat terrain without using poles. Builds fundamental single-ski balance.",
            howTo = "On a flat groomed trail, push off gently and glide on one ski for as long as possible. Alternate feet. Arms swing naturally but hold no poles. Focus on balancing over the gliding ski with hips directly above the foot. Try to hold each glide for 3+ seconds.",
            style = XCDrillStyle.CLASSIC, level = XCDrillLevel.BEGINNER, category = XCDrillCategory.GLIDE,
            targetCycleRate = null, targetGlideRatio = 0.6f,
            tips = listOf("Hips over the gliding foot", "Look ahead, not at your feet", "Relax your ankles", "Long, slow glides — don't rush"),
            source = "CXC / US Ski & Snowboard Nordic Fundamentals",
        ),
        XCDrill(
            id = "xc_c02", title = "Diagonal Stride on Flat",
            description = "The fundamental classic technique: alternating arm and leg in a natural walking/running rhythm on flat terrain.",
            howTo = "On a flat trail, stride forward with alternating arm and leg (left arm forward with right leg). Plant the pole at your binding and push. Kick the wax pocket into the snow, then glide forward on the other ski. Maintain a steady rhythm of 50-60 strides/min.",
            style = XCDrillStyle.CLASSIC, level = XCDrillLevel.BEGINNER, category = XCDrillCategory.TECHNIQUE,
            targetCycleRate = 50..60, targetGlideRatio = 0.45f,
            tips = listOf("Opposite arm and leg work together", "Kick DOWN into the snow, not backward", "Commit your weight fully to the gliding ski", "Poles plant at binding height, push past hip"),
            source = "PSIA Nordic Level I / CXC",
        ),
        XCDrill(
            id = "xc_c03", title = "Double Poling on Flat",
            description = "Simultaneous pole push using upper body strength. The primary technique for fast flat sections.",
            howTo = "On flat groomed terrain, stand with both poles planted ahead. Crunch forward from the hips, driving poles into the snow. Push past your hips using core and triceps. Let momentum carry you forward in the glide phase. Arms swing forward to reset. Target 60-70 cycles/min.",
            style = XCDrillStyle.CLASSIC, level = XCDrillLevel.BEGINNER, category = XCDrillCategory.POWER,
            targetCycleRate = 60..70, targetGlideRatio = 0.5f,
            tips = listOf("Power comes from core, not just arms", "Crunch forward — engage your abs", "Follow through past your hips", "Relax on the recovery — let arms swing naturally"),
            source = "PSIA Nordic / CXC Double Poling Progression",
        ),
        XCDrill(
            id = "xc_c04", title = "Kick Double Pole",
            description = "Combines a single leg kick with double poling for moderate terrain. Adds leg power to the DP motion.",
            howTo = "Start with a double pole setup. As you plant poles, add a kick with one leg — push the wax pocket into the snow. The kick and pole push happen simultaneously. Glide, then repeat with the same or alternating leg. This is the bridge between DP and diagonal stride.",
            style = XCDrillStyle.CLASSIC, level = XCDrillLevel.BEGINNER, category = XCDrillCategory.TECHNIQUE,
            targetCycleRate = 50..65, targetGlideRatio = 0.45f,
            tips = listOf("Kick and pole push are simultaneous", "The kick adds power — don't just tap the snow", "Try both same-leg and alternating patterns", "Use on gentle uphills where DP alone isn't enough"),
            source = "PSIA Nordic Level I",
        ),
        XCDrill(
            id = "xc_c05", title = "Herringbone Climb",
            description = "V-step climbing technique for steep uphills. Skis angled outward with edges set for grip.",
            howTo = "On a steep uphill, point ski tips outward in a V-shape. Edge both skis by rolling ankles inward. Step up alternating feet with poles planted behind for support. Keep hips forward over your feet. The steeper the hill, the wider the V-angle.",
            style = XCDrillStyle.CLASSIC, level = XCDrillLevel.BEGINNER, category = XCDrillCategory.TECHNIQUE,
            targetCycleRate = null, targetGlideRatio = null,
            tips = listOf("Wider V for steeper hills", "Edge the skis — don't let them slip", "Hips forward, don't lean back", "Short quick steps, not long strides"),
            source = "CXC Nordic Fundamentals",
        ),

        // ────────────────────────────────────────────────────
        // CLASSIC — INTERMEDIATE
        // ────────────────────────────────────────────────────
        XCDrill(
            id = "xc_c06", title = "One-Ski Balance Glide",
            description = "Extended single-ski glide for 5+ seconds. Advanced balance drill that builds efficient weight transfer.",
            howTo = "On flat terrain, push off and balance on one ski for as long as possible — target 5+ seconds per glide. The free foot hovers just above the snow. Arms hang naturally. Focus on stacking your entire body over the glide ski: ankle, knee, hip, shoulder all aligned.",
            style = XCDrillStyle.CLASSIC, level = XCDrillLevel.INTERMEDIATE, category = XCDrillCategory.GLIDE,
            targetCycleRate = null, targetGlideRatio = 0.7f,
            tips = listOf("5+ seconds per glide is the goal", "Stack your body: ankle → knee → hip → shoulder", "Quiet upper body — no arm waving for balance", "The free foot stays close to the snow, not lifted high"),
            source = "CXC / US Ski & Snowboard Talent ID Drills",
        ),
        XCDrill(
            id = "xc_c07", title = "Double Poling Uphill",
            description = "Maintain double poling on moderate uphills. Builds upper body power endurance and core strength.",
            howTo = "On a gentle uphill (3-5% grade), maintain double poling technique instead of switching to diagonal stride. This requires stronger pole pushes and deeper core engagement. Focus on a powerful crunch and long follow-through. Your speed will drop but maintain rhythm.",
            style = XCDrillStyle.CLASSIC, level = XCDrillLevel.INTERMEDIATE, category = XCDrillCategory.POWER,
            targetCycleRate = 55..70, targetGlideRatio = 0.35f,
            tips = listOf("Engage core harder on the uphill", "Accept slower speed — focus on power per stroke", "Deeper forward crunch than on flat", "This builds race-specific upper body endurance"),
            source = "PSIA Nordic Level II / Race Prep",
        ),
        XCDrill(
            id = "xc_c08", title = "Tempo Diagonal Stride",
            description = "Maintain 60+ strides/min in diagonal stride. Develops race-pace cadence and rhythm.",
            howTo = "On flat to gently rolling terrain, ski diagonal stride at a tempo of 60-70 strides per minute. This is faster than comfortable practice pace. Focus on quick, snappy kicks and fast arm turnover while maintaining glide. The app will display your live cadence.",
            style = XCDrillStyle.CLASSIC, level = XCDrillLevel.INTERMEDIATE, category = XCDrillCategory.ENDURANCE,
            targetCycleRate = 60..70, targetGlideRatio = 0.4f,
            tips = listOf("Quick kicks, not long glides", "Arms match leg tempo", "Stay relaxed — tension kills cadence", "Breathe rhythmically with your stride"),
            source = "CXC Race Prep / USSA Development",
        ),
        XCDrill(
            id = "xc_c09", title = "Downhill Tuck",
            description = "Aerodynamic tuck position for maximizing speed on downhill sections. Minimize air resistance.",
            howTo = "On a moderate downhill, assume a low tuck position: knees bent deeply, torso nearly horizontal, arms forward with poles tucked under armpits pointing backward. Maintain balance with a low center of gravity. Hold the tuck for the full descent.",
            style = XCDrillStyle.BOTH, level = XCDrillLevel.INTERMEDIATE, category = XCDrillCategory.TECHNIQUE,
            targetCycleRate = null, targetGlideRatio = null,
            tips = listOf("Knees bent 90+ degrees", "Flat back — don't round your shoulders", "Poles under armpits, tips pointing back", "Look ahead through the top of your eyes"),
            source = "PSIA Nordic / FIS Technique Guidelines",
        ),
        XCDrill(
            id = "xc_c10", title = "Technique Transitions",
            description = "Smoothly switch between DS → DP → tuck as terrain changes. The mark of an experienced classic skier.",
            howTo = "On a course with varied terrain, practice smooth transitions: diagonal stride on uphills, kick DP on transitions, double poling on flats, and tuck on downhills. The key is seamless switching without losing rhythm or speed. The app will track your technique changes and grade the transitions.",
            style = XCDrillStyle.CLASSIC, level = XCDrillLevel.INTERMEDIATE, category = XCDrillCategory.TECHNIQUE,
            targetCycleRate = null, targetGlideRatio = null,
            tips = listOf("Anticipate terrain changes — start switching early", "The last stride of one technique flows into the first of the next", "Speed should not dip during transitions", "Match your gear to the grade like shifting a bicycle"),
            source = "PSIA Nordic Level II / CXC Competition Skills",
        ),

        // ────────────────────────────────────────────────────
        // SKATE — BEGINNER
        // ────────────────────────────────────────────────────
        XCDrill(
            id = "xc_s01", title = "V2 One-Skate on Flat",
            description = "The fundamental skate technique: pole push on every stride. Symmetric, powerful, and versatile.",
            howTo = "On flat groomed trail, push off to one side with your leg while simultaneously planting both poles. As you glide, swing arms forward and push off the other leg with another pole plant. Every stride gets a pole push. Target 55-65 cycles/min with even left-right push.",
            style = XCDrillStyle.SKATE, level = XCDrillLevel.BEGINNER, category = XCDrillCategory.TECHNIQUE,
            targetCycleRate = 55..65, targetGlideRatio = 0.45f,
            tips = listOf("Pole push coincides with leg push", "Weight fully commits to the glide ski", "Push to the SIDE, not backward", "Equal power left and right"),
            source = "PSIA Nordic Skate Level I / CXC",
        ),
        XCDrill(
            id = "xc_s02", title = "V1 Offset Uphill",
            description = "Asymmetric skate technique for uphills. Poles planted on the same side each cycle for maximum climbing power.",
            howTo = "On a moderate uphill, skate with poles planted on the same side (e.g., always pole on the right push). This creates an offset rhythm: pole-push on right, glide on left, push on left, glide on right. The poling side generates more power. Switch sides periodically to avoid asymmetry.",
            style = XCDrillStyle.SKATE, level = XCDrillLevel.BEGINNER, category = XCDrillCategory.TECHNIQUE,
            targetCycleRate = 50..60, targetGlideRatio = 0.35f,
            tips = listOf("Pick your strong side for steep sections", "The pole push happens with one specific leg push", "Switch sides every 30 seconds to train both", "Shorter strides than V2 — quick and powerful"),
            source = "PSIA Nordic Skate / CXC Uphill Progression",
        ),
        XCDrill(
            id = "xc_s03", title = "Free Skate (No Poles)",
            description = "Skate without poles to develop leg power, balance, and edge control.",
            howTo = "On flat to gentle terrain, skate without poles. Arms swing naturally for balance. All propulsion comes from leg pushes. Focus on full weight commitment to each glide ski, strong edge push, and smooth weight transfer. This drill exposes balance weaknesses.",
            style = XCDrillStyle.SKATE, level = XCDrillLevel.BEGINNER, category = XCDrillCategory.GLIDE,
            targetCycleRate = 45..55, targetGlideRatio = 0.5f,
            tips = listOf("Arms swing for balance, not propulsion", "Feel the edge push — push through the whole foot", "Long glides — if you can't balance, slow down", "This drill reveals your weaker side"),
            source = "CXC Skate Fundamentals / USSA Development",
        ),
        XCDrill(
            id = "xc_s04", title = "Skate Tuck",
            description = "Aerodynamic tuck on skate skis for downhill sections. Same principle as classic tuck.",
            howTo = "On downhill, bring feet parallel (not V-shape), bend knees deeply, torso horizontal, poles under arms. Maintain a straight line down the fall line. The wider skate ski stance requires a slightly wider tuck than classic.",
            style = XCDrillStyle.SKATE, level = XCDrillLevel.BEGINNER, category = XCDrillCategory.TECHNIQUE,
            targetCycleRate = null, targetGlideRatio = null,
            tips = listOf("Bring feet parallel — stop skating before tucking", "Lower is faster — get your back flat", "Weight centered — not too far forward or back", "Practice on gentle hills first"),
            source = "PSIA Nordic Skate / FIS Guidelines",
        ),

        // ────────────────────────────────────────────────────
        // SKATE — INTERMEDIATE
        // ────────────────────────────────────────────────────
        XCDrill(
            id = "xc_s05", title = "V2 Alternate",
            description = "Pole push on every OTHER stride. More efficient than V2 on moderate terrain where full poling is unnecessary.",
            howTo = "On moderate flat to gentle terrain, perform V2 skating but only plant poles on every second stride. The non-poling stride relies purely on leg push and glide. This develops an efficient gear that's less exhausting than full V2 while maintaining good speed.",
            style = XCDrillStyle.SKATE, level = XCDrillLevel.INTERMEDIATE, category = XCDrillCategory.TECHNIQUE,
            targetCycleRate = 50..60, targetGlideRatio = 0.5f,
            tips = listOf("The non-poling stride must still be powerful", "Glide longer on the non-poling side", "Think of it as a 'gear' between V2 and free skate", "Great for flat sections where V2 is overkill"),
            source = "PSIA Nordic Skate Level II",
        ),
        XCDrill(
            id = "xc_s06", title = "Speed V2",
            description = "High-cadence V2 skating at 70+ strides/min. Race-pace drill for speed and power.",
            howTo = "On fast flat terrain, perform V2 one-skate at maximum sustainable cadence — target 70+ strides per minute. Short, explosive pushes with quick recovery. This is race pace. Maintain for 2-3 minutes, then recover. The app tracks your live cadence.",
            style = XCDrillStyle.SKATE, level = XCDrillLevel.INTERMEDIATE, category = XCDrillCategory.ENDURANCE,
            targetCycleRate = 70..85, targetGlideRatio = 0.35f,
            tips = listOf("Quick, snappy pushes — not long glides", "Arms match the high tempo", "Stay relaxed through shoulders and neck", "Breathe: exhale on push, inhale on recovery"),
            source = "CXC Race Prep / USSA Sprint Training",
        ),
        XCDrill(
            id = "xc_s07", title = "V1 to V2 Transition",
            description = "Seamlessly switch between V1 and V2 as terrain changes. The skate equivalent of gear shifting.",
            howTo = "On rolling terrain, use V1 on uphills and transition to V2 on flats and moderate downhills. The transition should be smooth — the last V1 stride flows into the first V2 stride without a pause. Practice both directions: V1→V2 (cresting a hill) and V2→V1 (starting a climb).",
            style = XCDrillStyle.SKATE, level = XCDrillLevel.INTERMEDIATE, category = XCDrillCategory.TECHNIQUE,
            targetCycleRate = null, targetGlideRatio = null,
            tips = listOf("Start transitioning before the terrain changes", "V1→V2: add the second pole push as the grade eases", "V2→V1: drop one pole push as the grade steepens", "Speed should not dip during transitions"),
            source = "PSIA Nordic Skate Level II / CXC",
        ),

        // ────────────────────────────────────────────────────
        // ADVANCED / EXPERT
        // ────────────────────────────────────────────────────
        XCDrill(
            id = "xc_a01", title = "Interval Session",
            description = "Alternate hard and easy effort segments by cadence. Builds race fitness and pacing discipline.",
            howTo = "Ski for 3 minutes at high cadence (70+ strides/min), then 2 minutes at recovery pace (45-50). Repeat 4-6 times. Use any technique appropriate for the terrain. The app monitors your cadence transitions and grades the consistency of your high/low intervals.",
            style = XCDrillStyle.BOTH, level = XCDrillLevel.ADVANCED, category = XCDrillCategory.ENDURANCE,
            targetCycleRate = null, targetGlideRatio = null,
            tips = listOf("High interval: 80-90% max effort", "Recovery: truly easy, let HR drop", "Consistent pacing — don't start too fast", "Great pre-race workout 2-3 days before competition"),
            source = "CXC Training Plans / USSA Sprint Development",
        ),
        XCDrill(
            id = "xc_a02", title = "Full Technique Selection",
            description = "Ski a full varied-terrain loop using the optimal technique for every section. The app grades your technique-terrain matching.",
            howTo = "On a course with flats, uphills, and downhills: use DP or V2 on flats, DS or V1 on moderate uphills, herringbone or V1 on steep uphills, KDP on transitions, and tuck on downhills. The app's sub-technique classifier tracks what you use where and scores how well you matched technique to terrain.",
            style = XCDrillStyle.BOTH, level = XCDrillLevel.ADVANCED, category = XCDrillCategory.TECHNIQUE,
            targetCycleRate = null, targetGlideRatio = null,
            tips = listOf("Anticipate terrain — switch early, not late", "Efficiency matters more than speed for this drill", "The goal is 90%+ technique-terrain match score", "Think like a cyclist choosing gears"),
            source = "PSIA Nordic Level III / Race Strategy",
        ),
        XCDrill(
            id = "xc_a03", title = "Race Pace Simulation",
            description = "Maintain target speed across mixed terrain for 15+ minutes. Race-specific endurance and pacing.",
            howTo = "Set a target average speed (e.g., 15 km/h for intermediate, 20+ km/h for advanced). Ski a mixed-terrain loop maintaining this average. The app shows your live vs target speed. Adjust technique and effort to keep speed consistent across flats, uphills, and downhills.",
            style = XCDrillStyle.BOTH, level = XCDrillLevel.EXPERT, category = XCDrillCategory.ENDURANCE,
            targetCycleRate = null, targetGlideRatio = null,
            tips = listOf("Don't blow up on the first uphill", "Bank speed on downhills to offset uphills", "Steady effort, not steady speed (effort varies with terrain)", "This is a mental and physical discipline drill"),
            source = "CXC / FIS Race Preparation",
        ),
    )

    fun getDrillsByLevel(level: XCDrillLevel): List<XCDrill> =
        allDrills.filter { it.level == level }

    fun getDrillsByStyle(style: XCDrillStyle): List<XCDrill> =
        allDrills.filter { it.style == style || it.style == XCDrillStyle.BOTH }

    fun getDrillById(id: String): XCDrill? =
        allDrills.find { it.id == id }
}
