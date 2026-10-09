package com.septuary.app.data

import android.content.Context
import com.septuary.app.alarm.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/** Human-readable form of a medication's `days` field, e.g. "Daily" or "Every Sun". */
fun scheduleLabel(days: String): String {
    if (days == "daily") return "Daily"
    if (days.startsWith("weekly:")) {
        val dow = days.substringAfter(":").toIntOrNull()
        val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        return "Every " + (names.getOrNull(dow ?: -1) ?: "week")
    }
    return days
}

/** Thin wrapper around the encrypted DB. All calls are suspend + run on the IO dispatcher. */
class Repository(private val db: AppDatabase, val appContext: Context) {

    private val dayFmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFmt get() = SimpleDateFormat("HH:mm", Locale.US)

    // --- Schedule -------------------------------------------------------------------------

    /**
     * Brings the schedule up to date and arms every reminder. On a fresh install the full seed is
     * written; on an existing install it is re-applied only when [SeedData.VERSION] increases —
     * keeping any time the user changed on purpose, deactivating items that were removed from
     * the plan, and never touching logged history.
     */
    suspend fun prepare() = withContext(Dispatchers.IO) {
        val prefs = appContext.getSharedPreferences("septuary_app", Context.MODE_PRIVATE)
        val dao = db.medicationDao()
        val existing = dao.getAllIncludingInactive().associateBy { it.id }
        val appliedVersion = prefs.getInt("seed_version", if (existing.isEmpty()) 0 else 1)

        if (existing.isEmpty() || appliedVersion < SeedData.VERSION) {
            val merged = SeedData.medications.map { seed ->
                val current = existing[seed.id]
                if (current != null && current.timeEdited) seed.copy(time = current.time, timeEdited = true) else seed
            }
            dao.insertAll(merged)
            dao.deactivateAllExcept(merged.map { it.id })
            prefs.edit().putInt("seed_version", SeedData.VERSION).apply()
        }
        if (db.weightDao().count() == 0) db.weightDao().insertAll(SeedData.weightLog)
        if (db.glucoseDao().count() == 0) db.glucoseDao().insertAll(SeedData.glucoseLog)
        if (db.goalDao().count() == 0) db.goalDao().insertAll(SeedData.goals)
        if (db.flagDao().count() == 0) db.flagDao().insertAll(SeedData.flags)

        AlarmScheduler.rescheduleAll(appContext, dao.getAll())
        applyPendingActions()
        mirrorDoneToday()
    }

    suspend fun medications(): List<MedicationEntity> = withContext(Dispatchers.IO) { db.medicationDao().getAll() }

    /**
     * Deliberate, user-initiated time change. Today's "done" state follows the item to its new
     * time, the old alarm is cancelled and the new one armed, and the family view updates.
     */
    suspend fun updateTime(medId: String, newTime: String) = withContext(Dispatchers.IO) {
        val before = db.medicationDao().getAll().find { it.id == medId } ?: return@withContext
        if (before.time == newTime) return@withContext
        db.medicationDao().updateTime(medId, newTime)
        db.doseLogDao().rekey(before.id + "@" + before.time, before.id + "@" + newTime, todayKey())
        AlarmScheduler.rescheduleAll(appContext, db.medicationDao().getAll())
        mirrorDoneToday()
        SyncRepository.schedulePush(this@Repository)
    }

    fun todayKey(): String = dayFmt.format(Date())

    fun isMedActiveOnDate(med: MedicationEntity, dateStr: String): Boolean {
        if (med.startDate != null && dateStr < med.startDate) return false
        if (med.endDate != null && dateStr > med.endDate) return false
        if (med.days == "daily") return true
        if (med.days.startsWith("weekly:")) {
            val dow = med.days.substringAfter(":").toIntOrNull() ?: return false // 0=Sunday
            val cal = Calendar.getInstance()
            dayFmt.parse(dateStr)?.let { cal.time = it }
            return cal.get(Calendar.DAY_OF_WEEK) - 1 == dow
        }
        return true
    }

    fun dateDaysAgo(n: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -n)
        return dayFmt.format(cal.time)
    }

    // --- Today's items (medicine + food share one model) -------------------------------------

    data class Dose(
        val doseKey: String, val medId: String, val name: String, val detail: String,
        val time: String, val instruction: String, val category: String
    )

    suspend fun todayDoses(): List<Dose> = withContext(Dispatchers.IO) {
        val today = todayKey()
        db.medicationDao().getAll()
            .filter { isMedActiveOnDate(it, today) }
            .map { Dose(it.id + "@" + it.time, it.id, it.name, it.detail, it.time, it.instruction, it.category) }
            .sortedWith(compareBy({ it.time }, { it.name }))
    }

    /** doseKey -> ISO time it was marked done today. */
    suspend fun todayLog(): Map<String, String> = withContext(Dispatchers.IO) {
        db.doseLogDao().getForDate(todayKey()).associate { it.doseKey to it.takenAtIso }
    }

    suspend fun setDone(doseKeys: Collection<String>, done: Boolean) = withContext(Dispatchers.IO) {
        val date = todayKey()
        val now = java.time.Instant.now().toString()
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        doseKeys.forEach { key ->
            if (done) {
                db.doseLogDao().markIfAbsent(DoseLogEntity(key, date, now))
                // Ticked in-app: clear its reminder from the shade and drop any pending snooze.
                nm.cancel(key.hashCode())
                AlarmScheduler.cancelSnoozeOnly(appContext, key)
            } else {
                db.doseLogDao().unmark(key, date)
            }
        }
        mirrorDoneToday()
        SyncRepository.schedulePush(this@Repository)
    }

    /** Merges notification "Taken" taps made while locked. Returns true if anything changed. */
    suspend fun applyPendingActions(): Boolean = withContext(Dispatchers.IO) {
        val actions = PendingActions.read(appContext)
        if (actions.isEmpty()) return@withContext false
        val meds = db.medicationDao().getAll().associateBy { it.id }
        val applied = mutableSetOf<String>()
        actions.forEach { a ->
            val med = meds[a.medId]
            if (med != null) db.doseLogDao().markIfAbsent(DoseLogEntity(med.id + "@" + med.time, a.date, a.atIso))
            applied += a.id
        }
        PendingActions.remove(appContext, applied)
        mirrorDoneToday()
        true
    }

    private suspend fun mirrorDoneToday() {
        val date = todayKey()
        DoneMirror.set(appContext, date, db.doseLogDao().getForDate(date).map { it.doseKey }.toSet())
    }

    // --- Optional food photo -------------------------------------------------------------------

    fun newDosePhotoFile(doseKey: String): File {
        val dir = File(appContext.filesDir, "dose_photos").apply { mkdirs() }
        val safeName = doseKey.replace(":", "-").replace("@", "_")
        return File(dir, "${safeName}_${todayKey()}.jpg")
    }

    suspend fun attachDosePhoto(doseKey: String, photoPath: String) = withContext(Dispatchers.IO) {
        db.doseLogDao().setPhoto(doseKey, todayKey(), photoPath)
    }

    suspend fun todayPhotoPaths(): Map<String, String> = withContext(Dispatchers.IO) {
        db.doseLogDao().getForDate(todayKey())
            .mapNotNull { entry -> entry.photoPath?.let { entry.doseKey to it } }
            .toMap()
    }

    // --- Exercise ------------------------------------------------------------------------------

    val exerciseTypes = listOf("Walking", "Running", "Cycling", "Swimming", "Yoga", "Strength")

    suspend fun addExercise(type: String, minutes: Int): Long = withContext(Dispatchers.IO) {
        val now = Date()
        val id = db.exerciseDao().insert(
            ExerciseLogEntity(date = dayFmt.format(now), time = timeFmt.format(now), type = type, minutes = minutes)
        )
        SyncRepository.schedulePush(this@Repository)
        id
    }

    /** Undo for a removed session: puts it back exactly as it was (same date and time). */
    suspend fun restoreExercise(entry: ExerciseLogEntity) = withContext(Dispatchers.IO) {
        db.exerciseDao().insert(entry.copy(id = 0))
        SyncRepository.schedulePush(this@Repository)
    }

    suspend fun deleteExercise(id: Long) = withContext(Dispatchers.IO) {
        db.exerciseDao().delete(id)
        SyncRepository.schedulePush(this@Repository)
    }

    suspend fun todayExercise(): List<ExerciseLogEntity> = withContext(Dispatchers.IO) { db.exerciseDao().getForDate(todayKey()) }

    /** Last 7 days (today inclusive), newest first. */
    suspend fun weekExercise(): List<ExerciseLogEntity> = withContext(Dispatchers.IO) { db.exerciseDao().since(dateDaysAgo(6)) }

    // --- Entries imported from the chat inbox (no in-app UI; kept so nothing sent is lost) ---

    suspend fun addExerciseOn(type: String, minutes: Int, note: String = "", onDate: String? = null) = withContext(Dispatchers.IO) {
        val now = Date()
        db.exerciseDao().insert(
            ExerciseLogEntity(date = onDate ?: dayFmt.format(now), time = timeFmt.format(now), type = type, minutes = minutes, note = note)
        )
        Unit
    }

    suspend fun weightLog(): List<WeightEntity> = withContext(Dispatchers.IO) { db.weightDao().recent() }

    suspend fun addWeight(kg: Double, onDate: String? = null, note: String = "") = withContext(Dispatchers.IO) {
        val now = Date()
        db.weightDao().insert(WeightEntity(date = onDate ?: dayFmt.format(now), time = timeFmt.format(now), kg = kg, note = note))
    }

    suspend fun addGlucose(value: Int, type: String, onDate: String? = null) = withContext(Dispatchers.IO) {
        val now = Date()
        db.glucoseDao().insert(GlucoseEntity(date = onDate ?: dayFmt.format(now), time = timeFmt.format(now), type = type, value = value))
    }

    suspend fun addFlag(text: String) = withContext(Dispatchers.IO) {
        db.flagDao().insertAll(listOf(FlagEntity(text = text, resolved = false)))
    }

    suspend fun addSleep(bedTime: String, wakeTime: String, quality: Int, note: String = "") = withContext(Dispatchers.IO) {
        val (bh, bm) = bedTime.split(":").map { it.toInt() }
        val (wh, wm) = wakeTime.split(":").map { it.toInt() }
        var wake = wh * 60 + wm
        val bed = bh * 60 + bm
        if (wake <= bed) wake += 24 * 60
        db.sleepDao().insert(
            SleepEntity(date = todayKey(), bedTime = bedTime, wakeTime = wakeTime, hours = (wake - bed) / 60.0, quality = quality, note = note)
        )
    }

    fun close() = db.close()
}
