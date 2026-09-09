package org.akkirrai.hibiki.core.profile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Holds [ProfileRules] to the same generated statement of the rules the desktop app is held to.
 *
 * `profileRules.vectors.json` is produced by the desktop implementation
 * (hibiki-desktop `scripts/generateProfileVectors.ts`) and copied here verbatim. It exists because
 * this arithmetic now lives in two languages, and two implementations drift silently: the same
 * watch history quietly becomes level 7 there and level 6 here, with nothing to point at. This is
 * the thing to point at.
 *
 * A failure means either a rule changed on purpose - in which case the desktop regenerates the
 * vectors, the file is re-copied, and both sides move together - or it changed by accident, which
 * is the entire point.
 */
class ProfileRulesTest {

    private val vectors: JsonObject by lazy {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("profileRules.vectors.json")) {
            "profileRules.vectors.json is missing from test resources - copy it from hibiki-desktop/src/shared/"
        }
        Json.parseToJsonElement(stream.reader().readText()).jsonObject
    }

    @Test
    fun `the vectors still cover every family`() {
        // A guard on the vectors themselves. They are worth something only while they exercise the
        // rules, and a family added later with no case would otherwise pass unnoticed on both sides.
        val families = vectors["cases"]!!.jsonArray
            .flatMap { case -> case.jsonObject["expect"]!!.jsonObject["achievements"]!!.jsonArray }
            .map { it.jsonObject["id"]!!.jsonPrimitive.content }
            .toSortedSet()
        assertEquals(
            sortedSetOf("collector", "episodes", "finisher", "first_title", "genres", "streak", "watch"),
            families,
        )
        assertTrue("expected at least 15 rule cases", vectors["cases"]!!.jsonArray.size >= 15)
        assertTrue("expected at least 6 streak cases", vectors["streakCases"]!!.jsonArray.size >= 6)
    }

    @Test
    fun `constants match the shared definition`() {
        // Cheap, and it names the disagreement directly instead of leaving it to be inferred from
        // twenty failing XP totals.
        val constants = vectors["constants"]!!.jsonObject
        assertEquals(10, constants["xpPerWatchHour"]!!.jsonPrimitive.int)
        assertEquals(100, constants["levelXpBase"]!!.jsonPrimitive.int)
        assertEquals(50, constants["levelXpStep"]!!.jsonPrimitive.int)
        assertEquals(20, constants["minutesPerCountedEpisode"]!!.jsonPrimitive.int)
    }

    @Test
    fun `achievements and levels match the vectors`() {
        for (case in vectors["cases"]!!.jsonArray) {
            val name = case.jsonObject["name"]!!.jsonPrimitive.content
            val input = case.jsonObject["input"]!!.jsonObject
            val expected = case.jsonObject["expect"]!!.jsonObject

            val entries = input["entries"]!!.jsonArray.map { entry ->
                ProfileRules.Entry(
                    category = entry.jsonObject["category"]!!.jsonPrimitive.content,
                    genres = entry.jsonObject["genres"]!!.jsonArray.map { it.jsonPrimitive.content },
                )
            }
            val lifetimeWatchedMs = input["lifetimeWatchedMs"]!!.jsonPrimitive.long
            val bestStreak = input["bestStreak"]!!.jsonPrimitive.int

            val achievements = ProfileRules.computeAchievements(entries, lifetimeWatchedMs, bestStreak)
            val expectedAchievements = expected["achievements"]!!.jsonArray

            assertEquals("[$name] achievement count", expectedAchievements.size, achievements.size)
            for ((index, achievement) in achievements.withIndex()) {
                val want = expectedAchievements[index].jsonObject
                val where = "[$name] ${achievement.id}"
                assertEquals("$where id", want["id"]!!.jsonPrimitive.content, achievement.id)
                assertEquals("$where current", want["current"]!!.jsonPrimitive.double, achievement.current, 0.0)
                assertEquals("$where target", want["target"]!!.jsonPrimitive.double, achievement.target, 0.0)
                assertEquals("$where unlocked", want["unlocked"]!!.jsonPrimitive.boolean, achievement.unlocked)
                assertEquals("$where level", want["level"]!!.jsonPrimitive.int, achievement.level)
                assertEquals("$where maxLevel", want["maxLevel"]!!.jsonPrimitive.int, achievement.maxLevel)
                assertEquals("$where xpReward", want["xpReward"]!!.jsonPrimitive.int, achievement.xpReward)
                assertEquals("$where xpEarned", want["xpEarned"]!!.jsonPrimitive.int, achievement.xpEarned)
            }

            val totalXp = ProfileRules.totalXpEarned(achievements, lifetimeWatchedMs)
            assertEquals("[$name] totalXp", expected["totalXp"]!!.jsonPrimitive.int, totalXp)

            val progress = ProfileRules.computeLevelProgress(totalXp)
            assertEquals("[$name] level", expected["level"]!!.jsonPrimitive.int, progress.level)
            assertEquals("[$name] xpIntoLevel", expected["xpIntoLevel"]!!.jsonPrimitive.int, progress.xpIntoLevel)
            assertEquals("[$name] xpForLevel", expected["xpForLevel"]!!.jsonPrimitive.int, progress.xpForLevel)
        }
    }

    @Test
    fun `streaks match the vectors`() {
        for (case in vectors["streakCases"]!!.jsonArray) {
            val name = case.jsonObject["name"]!!.jsonPrimitive.content
            val days = case.jsonObject["days"]!!.jsonArray.map {
                ProfileRules.ActivityDay(active = it.jsonPrimitive.int == 1)
            }
            val expected = case.jsonObject["expect"]!!.jsonObject
            val actual = ProfileRules.computeStreaks(days)
            assertEquals("[$name] current", expected["current"]!!.jsonPrimitive.int, actual.current)
            assertEquals("[$name] best", expected["best"]!!.jsonPrimitive.int, actual.best)
            assertEquals("[$name] atRisk", expected["atRisk"]!!.jsonPrimitive.boolean, actual.atRisk)
        }
    }
}
