package com.septuary.app.data

import android.content.Context
import com.septuary.app.alarm.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/** Thin wrapper around the encrypted DB. All calls are suspend + run on IO dispatcher. */
class Repository(private val db: AppDatabase, private val appContext: Context) {

    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        if (db.medicationDao().count() == 0) db.medicationDao().insertAll(SeedData.medications)
        if (db.weightDao().count() == 0) db.weightDao().insertAll(SeedData.weightLog)
        if (db.glucoseDao().count() == 0) db.glucoseDao().insertAll(SeedData.glucoseLog)
        if (db.goalDao().count() == 0) db.goalDao().insertAll(SeedData.goals)
        if (db.flagDao().count() == 0) db.flagDao().insertAll(SeedData.flags)
        // Build the plaintext alarm-scheduling cache from the (now-decrypted) medication list
        // and arm the exact alarms. See ScheduleCache.kt for what this cache does and doesn't hold.
        AlarmScheduler.rescheduleAll(appContext, db.medicationDao().getAll())
    }

    suspend fun medications(): List<MedicationEntity> = withContext(Dispatchers.IO) { db.medicationDao().getAll() }

    fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun isMedActiveToday(med: MedicationEntity): Boolean = isMedActiveOnDate(med, todayKey())

    /** Same rule as [isMedActiveToday] but for an arbitrary date — used by Trends to score past days. */
    fun isMedActiveOnDate(med: MedicationEntity, dateStr: String): Boolean {
        if (med.startDate != null && dateStr < med.startDate) return false
        if (med.endDate != null && dateStr > med.endDate) return false
        if (med.days == "daily") return true
        if (med.days.startsWith("weekly:")) {
            val dow = med.days.substringAfter(":").toInt() // 0=Sunday
            val cal = Calendar.getInstance()
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)
            if (parsed != null) cal.time = parsed
            return cal.get(Calendar.DAY_OF_WEEK) - 1 == dow
        }
        return true
    }

    /** "YYYY-MM-DD" for N days before today (0 = today). Used to build Trends windows. */
    fun dateDaysAgo(n: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -n)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    data class Dose(
        val doseKey: String, val medId: String, val name: String, val detail: String,
        val time: String, val instruction: String, val category: String
    )

    suspend fun todayDoses(): List<Dose> = withContext(Dispatchers.IO) {
        db.medicationDao().getAll()
            .filter { isMedActiveToday(it) }
            .map { Dose(it.id + "@" + it.time, it.id, it.name, it.detail, it.time, it.instruction, it.category) }
            .sortedBy { it.time }
    }

    suspend fun todayLog(): Set<String> = withContext(Dispatchers.IO) {
        db.doseLogDao().getForDate(todayKey()).map { it.doseKey }.toSet()
    }

    suspend fun toggleDose(doseKey: String, currentlyDone: Boolean) = withContext(Dispatchers.IO) {
        val date = todayKey()
        if (currentlyDone) {
            db.doseLogDao().unmark(doseKey, date)
        } else {
            db.doseLogDao().mark(DoseLogEntity(doseKey, date, java.time.Instant.now().toString()))
        }
    }

    suspend fun weightLog(): List<WeightEntity> = withContext(Dispatchers.IO) { db.weightDao().recent() }
    suspend fun glucoseLog(): List<GlucoseEntity> = withContext(Dispatchers.IO) { db.glucoseDao().recent() }

    suspend fun addWeight(kg: Double) = withContext(Dispatchers.IO) {
        val now = Date()
        db.weightDao().insert(
            WeightEntity(
                date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now),
                time = SimpleDateFormat("HH:mm", Locale.US).format(now),
                kg = kg, note = ""
            )
        )
    }

    suspend fun addGlucose(value: Int, type: String) = withContext(Dispatchers.IO) {
        val now = Date()
        db.glucoseDao().insert(
            GlucoseEntity(
                date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now),
                time = SimpleDateFormat("HH:mm", Locale.US).format(now),
                type = type, value = value
            )
        )
    }

    suspend fun goals(): List<GoalEntity> = withContext(Dispatchers.IO) { db.goalDao().getAll() }
    suspend fun flags(): List<FlagEntity> = withContext(Dispatchers.IO) { db.flagDao().getAll() }

    val exerciseTypes = listOf("Swimming", "Cycling", "Walking", "Yoga")

    suspend fun addExercise(type: String, minutes: Int, note: String = "") = withContext(Dispatchers.IO) {
        val now = Date()
        db.exerciseDao().insert(
            ExerciseLogEntity(
                date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now),
                time = SimpleDateFormat("HH:mm", Locale.US).format(now),
                type = type, minutes = minutes, note = note
            )
        )
    }

    suspend fun todayExercise(): List<ExerciseLogEntity> = withContext(Dispatchers.IO) { db.exerciseDao().getForDate(todayKey()) }
    suspend fun exerciseLog(): List<ExerciseLogEntity> = withContext(Dispatchers.IO) { db.exerciseDao().recent() }
    suspend fun deleteExercise(id: Long) = withContext(Dispatchers.IO) { db.exerciseDao().delete(id) }

    // --- Sleep ---

    val sleepQualityLabels = listOf("Poor", "Fair", "Good", "Great", "Excellent") // index 0 -> quality 1

    /** Computes hours between bed/wake "HH:MM" strings, handling the overnight wrap (wake next day). */
    private fun sleepHours(bedTime: String, wakeTime: String): Double {
        val (bh, bm) = bedTime.split(":").map { it.toInt() }
        val (wh, wm) = wakeTime.split(":").map { it.toInt() }
        val bedMinutes = bh * 60 + bm
        var wakeMinutes = wh * 60 + wm
        if (wakeMinutes <= bedMinutes) wakeMinutes += 24 * 60
        return (wakeMinutes - bedMinutes) / 60.0
    }

    suspend fun addSleep(bedTime: String, wakeTime: String, quality: Int, note: String = "") = withContext(Dispatchers.IO) {
        db.sleepDao().insert(
            SleepEntity(
                date = todayKey(), // logged the morning you wake up, for last night's sleep
                bedTime = bedTime, wakeTime = wakeTime,
                hours = sleepHours(bedTime, wakeTime),
                quality = quality, note = note
            )
        )
    }

    suspend fun sleepLog(): List<SleepEntity> = withContext(Dispatchers.IO) { db.sleepDao().recent() }

    // --- Trends (Daily/Weekly/Monthly rollups across Medicine, Food, Exercise, Sleep) ---

    /** Everything Trends needs to score the last [days] days, fetched in one batch. */
    suspend fun trendsData(days: Int): Trends.RawData = withContext(Dispatchers.IO) {
        val from = dateDaysAgo(days - 1)
        Trends.RawData(
            medications = db.medicationDao().getAll(),
            doseLog = db.doseLogDao().since(from),
            exerciseLog = db.exerciseDao().since(from),
            sleepLog = db.sleepDao().since(from)
        )
    }

    fun close() = db.close()
}
