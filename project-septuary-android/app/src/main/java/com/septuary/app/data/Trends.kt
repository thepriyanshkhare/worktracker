package com.septuary.app.data

import kotlin.math.abs

/**
 * Pure scoring logic for the Trends tab — no Compose/Android imports here on purpose, so the
 * math is easy to read and change independently of the chart UI. Every score is 0-100, where
 * 100 = the best possible outcome for that day in that category:
 *   Medicine / Food -> % of that day's scheduled items actually marked taken
 *   Exercise        -> minutes logged vs. a daily target, capped at 100
 *   Sleep            -> blend of "hours close to target" and self-rated quality
 * A category is `null` for a day only when nothing was scheduled/logged for it at all (e.g. no
 * sleep entry that day) — the UI renders that as "no data" rather than as a failing score.
 */
object Trends {

    const val EXERCISE_DAILY_TARGET_MIN = 45
    const val SLEEP_TARGET_HOURS = 7.5

    data class RawData(
        val medications: List<MedicationEntity>,
        val doseLog: List<DoseLogEntity>,
        val exerciseLog: List<ExerciseLogEntity>,
        val sleepLog: List<SleepEntity>
    )

    data class DayScore(
        val date: String,
        val medicine: Int?,
        val food: Int?,
        val exercise: Int,
        val sleep: Int?,
        val overall: Int
    )

    /** [dates] must be "YYYY-MM-DD" strings, any order; results come back in the same order. */
    fun computeDailyScores(
        dates: List<String>,
        data: RawData,
        isActiveOn: (MedicationEntity, String) -> Boolean
    ): List<DayScore> {
        val takenByDate: Map<String, Set<String>> =
            data.doseLog.groupBy { it.date }.mapValues { e -> e.value.map { it.doseKey }.toSet() }
        val exerciseByDate = data.exerciseLog.groupBy { it.date }
        val sleepByDate = data.sleepLog.groupBy { it.date }

        return dates.map { date ->
            val takenKeys = takenByDate[date] ?: emptySet()

            val medsToday = data.medications.filter { it.category == "medication" && isActiveOn(it, date) }
            val medScore = if (medsToday.isEmpty()) null
            else (medsToday.count { (it.id + "@" + it.time) in takenKeys } * 100) / medsToday.size

            val foodToday = data.medications.filter { it.category != "medication" && isActiveOn(it, date) }
            val foodScore = if (foodToday.isEmpty()) null
            else (foodToday.count { (it.id + "@" + it.time) in takenKeys } * 100) / foodToday.size

            val minutes = exerciseByDate[date]?.sumOf { it.minutes } ?: 0
            val exerciseScore = ((minutes * 100) / EXERCISE_DAILY_TARGET_MIN).coerceIn(0, 100)

            val sleepEntry = sleepByDate[date]?.firstOrNull()
            val sleepScore = sleepEntry?.let { s ->
                val hoursScore = (100.0 - abs(s.hours - SLEEP_TARGET_HOURS) * 20.0).coerceIn(0.0, 100.0)
                val qualityScore = (s.quality * 100.0 / 5.0).coerceIn(0.0, 100.0)
                ((hoursScore + qualityScore) / 2.0).toInt()
            }

            val parts = listOfNotNull(medScore, foodScore, exerciseScore, sleepScore)
            val overall = if (parts.isEmpty()) 0 else parts.sum() / parts.size

            DayScore(date, medScore, foodScore, exerciseScore, sleepScore, overall)
        }
    }

    /** Average of the non-null scores in [scores] for one category selector; null if none had data. */
    fun average(scores: List<DayScore>, pick: (DayScore) -> Int?): Int? {
        val vals = scores.mapNotNull(pick)
        return if (vals.isEmpty()) null else vals.sum() / vals.size
    }
}
