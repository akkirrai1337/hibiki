package org.akkirrai.hibiki.core.profile

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round

/**
 * XP, levels, achievement tiers and streaks for the profile screen.
 *
 * A port of the desktop app's rules (hibiki-desktop `src/renderer/src/lib/achievements.ts`,
 * `levelProgress.ts`, `StreakBadge.kt`'s streak math), and deliberately a *checked* one: the same
 * arithmetic living in two languages drifts silently, and the same watch history quietly becoming
 * level 7 on the desktop and level 6 here is the kind of bug nobody can point at. Both sides are
 * held to one generated statement of the rules - `profileRules.vectors.json`, produced by the
 * desktop implementation and run against this one by [ProfileRulesTest]. Change a rule and both
 * sides' tests fail until they agree again.
 *
 * Pure by design: no icons, no strings, no Android. Presentation belongs to the screen, and the
 * vectors carry none of it, so nothing here can disagree with the desktop about something that
 * isn't a rule.
 *
 * Everything is derived. There is no achievements table and no stored XP total.
 */
object ProfileRules {

    /** Watched time is bucketed into episode-sized chunks so films and OVAs still progress the
     * "episodes" family, which is otherwise indistinguishable from raw hours. */
    private const val MINUTES_PER_COUNTED_EPISODE = 20

    private const val XP_PER_WATCH_HOUR = 10
    private const val LEVEL_XP_BASE = 100
    private const val LEVEL_XP_STEP = 50

    /** A single step of a family. XP is set per tier by hand rather than by position: a flat
     * "tier 1 always pays 50" schedule priced 24 hours of watching and adding ten titles to a list
     * the same, which they are not. */
    data class Tier(val id: String, val target: Double, val xp: Int)

    /** One family's current state - the tier in progress, or the last one once all are cleared. */
    data class Achievement(
        val id: String,
        val current: Double,
        val target: Double,
        val unlocked: Boolean,
        val level: Int,
        val maxLevel: Int,
        val xpReward: Int,
        val xpEarned: Int,
    )

    data class LevelProgress(val level: Int, val xpIntoLevel: Int, val xpForLevel: Int, val totalXp: Int)

    data class StreakInfo(val current: Int, val best: Int, val atRisk: Boolean)

    /** The only two fields of a library entry these rules read. */
    data class Entry(val category: String, val genres: List<String>)

    /** A day of watch activity. Only whether it happened matters here. */
    data class ActivityDay(val active: Boolean)

    val FAMILY_TIERS: Map<String, List<Tier>> = mapOf(
        "first_title" to listOf(Tier("first_title", 1.0, 5)),
        "collector" to listOf(
            Tier("collector_10", 10.0, 10),
            Tier("collector_25", 25.0, 20),
            Tier("collector_50", 50.0, 40),
        ),
        "finisher" to listOf(
            Tier("finisher", 1.0, 40),
            Tier("marathoner_10", 10.0, 250),
            Tier("marathoner_50", 50.0, 900),
        ),
        "streak" to listOf(
            Tier("streak_7", 7.0, 60),
            Tier("streak_14", 14.0, 150),
            Tier("streak_30", 30.0, 400),
        ),
        "watch" to listOf(
            Tier("watch_24h", 24.0, 300),
            Tier("watch_100h", 100.0, 900),
            Tier("watch_500h", 500.0, 3000),
        ),
        "episodes" to listOf(
            Tier("episodes_50", 50.0, 200),
            Tier("episodes_100", 100.0, 450),
            Tier("episodes_300", 300.0, 1200),
        ),
        "genres" to listOf(
            Tier("genre_explorer", 5.0, 15),
            Tier("genre_explorer_10", 10.0, 35),
            Tier("genre_explorer_15", 15.0, 80),
        ),
    )

    /**
     * Lifetime hours, rounded to one decimal.
     *
     * The rounding is part of the rule rather than presentation: 23.94 hours reads as 23.9 and does
     * *not* clear the 24-hour tier, while 23.96 reads as 24.0 and does. Written in the same order as
     * the desktop expression (`round(ms / 3_600_000 * 10) / 10`) on purpose - IEEE 754 gives both
     * languages identical results for identical operation orders, and a rearrangement here would
     * quietly disagree at exactly these boundaries.
     */
    fun watchedHoursFrom(lifetimeWatchedMs: Long): Double =
        round(lifetimeWatchedMs / 3_600_000.0 * 10) / 10

    /**
     * One entry per family, each showing whichever tier is currently in progress - or the last one,
     * fully cleared, once every tier is done. Clearing a tier moves the card on to the next rather
     * than leaving a finished card behind.
     */
    fun computeAchievements(entries: List<Entry>, lifetimeWatchedMs: Long, bestStreak: Int): List<Achievement> {
        val completedCount = entries.count { it.category == "completed" }
        val genreCount = entries.flatMap { it.genres }.toSet().size
        val watchedHours = watchedHoursFrom(lifetimeWatchedMs)
        val watchedEpisodeEquivalent =
            floor(lifetimeWatchedMs / (MINUTES_PER_COUNTED_EPISODE * 60_000).toDouble())

        return listOf(
            leveled("first_title", entries.size.toDouble()),
            leveled("collector", entries.size.toDouble()),
            leveled("finisher", completedCount.toDouble()),
            leveled("streak", bestStreak.toDouble()),
            leveled("watch", watchedHours),
            leveled("episodes", watchedEpisodeEquivalent),
            leveled("genres", genreCount.toDouble()),
        )
    }

    private fun leveled(familyId: String, current: Double): Achievement {
        val tiers = FAMILY_TIERS.getValue(familyId)
        var level = 0
        for (tier in tiers) {
            if (current >= tier.target) level++ else break
        }
        val maxed = level >= tiers.size
        val activeTier = tiers[if (maxed) tiers.size - 1 else level]
        return Achievement(
            id = familyId,
            current = min(current, activeTier.target),
            target = activeTier.target,
            unlocked = maxed,
            level = level,
            maxLevel = tiers.size,
            xpReward = activeTier.xp,
            xpEarned = tiers.take(level).sumOf { it.xp },
        )
    }

    /** Ten XP per watched hour is the steady drip; achievement tiers are the occasional bumps. */
    fun totalXpEarned(achievements: List<Achievement>, lifetimeWatchedMs: Long): Int {
        val fromWatching = floor(watchedHoursFrom(lifetimeWatchedMs) * XP_PER_WATCH_HOUR).toInt()
        return fromWatching + achievements.sumOf { it.xpEarned }
    }

    private fun xpToClearLevel(level: Int): Int = LEVEL_XP_BASE + (level - 1) * LEVEL_XP_STEP

    /** Levels start at 1 and get gradually more expensive: 100, then 150, then 200. */
    fun computeLevelProgress(totalXp: Int): LevelProgress {
        var level = 1
        var remaining = totalXp
        while (remaining >= xpToClearLevel(level)) {
            remaining -= xpToClearLevel(level)
            level++
        }
        return LevelProgress(level = level, xpIntoLevel = remaining, xpForLevel = xpToClearLevel(level), totalXp = totalXp)
    }

    /**
     * Consecutive active days ending today, with one day of grace.
     *
     * Three rules that are each easy to get subtly wrong, and each pinned by a vector:
     *  - a single missed day does not zero the run, it consumes the grace day;
     *  - the grace day is repaid by the next active day, so it is available again for the next gap
     *    rather than being once-ever;
     *  - today never counts against the run and never consumes the grace day, because it hasn't
     *    finished yet. It only sets [StreakInfo.atRisk].
     *
     * @param days oldest first; the last entry is today.
     */
    fun computeStreaks(days: List<ActivityDay>): StreakInfo {
        var best = 0
        var run = 0
        var graceUsed = false
        for (index in days.indices) {
            val active = days[index].active
            val isToday = index == days.size - 1
            when {
                active -> {
                    run++
                    graceUsed = false
                    best = maxOf(best, run)
                }
                isToday -> Unit
                !graceUsed -> graceUsed = true
                else -> {
                    run = 0
                    graceUsed = false
                }
            }
        }
        val todayActive = days.isNotEmpty() && days.last().active
        return StreakInfo(current = run, best = best, atRisk = run > 0 && !todayActive)
    }
}
