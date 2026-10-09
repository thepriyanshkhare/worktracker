package com.septuary.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE active = 1")
    suspend fun getAll(): List<MedicationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(meds: List<MedicationEntity>)

    @Query("SELECT COUNT(*) FROM medications")
    suspend fun count(): Int

    @Query("SELECT * FROM medications")
    suspend fun getAllIncludingInactive(): List<MedicationEntity>

    @Query("UPDATE medications SET active = 0 WHERE id NOT IN (:keepIds)")
    suspend fun deactivateAllExcept(keepIds: List<String>)

    @Query("UPDATE medications SET time = :time, timeEdited = 1 WHERE id = :id")
    suspend fun updateTime(id: String, time: String)
}

@Dao
interface DoseLogDao {
    @Query("SELECT * FROM dose_log WHERE date = :date")
    suspend fun getForDate(date: String): List<DoseLogEntity>

    @Query("SELECT * FROM dose_log WHERE date >= :from")
    suspend fun since(from: String): List<DoseLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun mark(entry: DoseLogEntity)

    /** Adds a log entry only if none exists yet (keeps an attached photo intact). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markIfAbsent(entry: DoseLogEntity)

    /** Moves a day's entry to a new doseKey when an item's time changes, so it stays "done". */
    @Query("UPDATE OR IGNORE dose_log SET doseKey = :newKey WHERE doseKey = :oldKey AND date = :date")
    suspend fun rekey(oldKey: String, newKey: String, date: String)

    @Query("DELETE FROM dose_log WHERE doseKey = :doseKey AND date = :date")
    suspend fun unmark(doseKey: String, date: String)

    @Query("UPDATE dose_log SET photoPath = :photoPath WHERE doseKey = :doseKey AND date = :date")
    suspend fun setPhoto(doseKey: String, date: String, photoPath: String?)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_log ORDER BY date DESC, time DESC LIMIT 30")
    suspend fun recent(): List<WeightEntity>

    @Query("SELECT * FROM weight_log WHERE date >= :from ORDER BY date ASC, time ASC")
    suspend fun since(from: String): List<WeightEntity>

    @Insert
    suspend fun insert(entry: WeightEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<WeightEntity>)

    @Query("SELECT COUNT(*) FROM weight_log")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM weight_log WHERE date = :date)")
    suspend fun existsForDate(date: String): Boolean
}

@Dao
interface StepsDao {
    @Query("SELECT * FROM steps_log WHERE date >= :from ORDER BY date ASC")
    suspend fun since(from: String): List<StepsEntity>

    // REPLACE on the `date` primary key gives upsert-by-day for free — today's running count
    // is re-synced throughout the day and should update in place, never duplicate.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: StepsEntity)

    @Query("SELECT COUNT(*) FROM steps_log")
    suspend fun count(): Int
}

@Dao
interface GlucoseDao {
    @Query("SELECT * FROM glucose_log ORDER BY date DESC, time DESC LIMIT 30")
    suspend fun recent(): List<GlucoseEntity>

    @Query("SELECT * FROM glucose_log WHERE date >= :from ORDER BY date ASC, time ASC")
    suspend fun since(from: String): List<GlucoseEntity>

    @Insert
    suspend fun insert(entry: GlucoseEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<GlucoseEntity>)

    @Query("SELECT COUNT(*) FROM glucose_log")
    suspend fun count(): Int
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals")
    suspend fun getAll(): List<GoalEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<GoalEntity>)

    @Query("SELECT COUNT(*) FROM goals")
    suspend fun count(): Int
}

@Dao
interface FlagDao {
    @Query("SELECT * FROM flags")
    suspend fun getAll(): List<FlagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<FlagEntity>)

    @Query("SELECT COUNT(*) FROM flags")
    suspend fun count(): Int
}

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_log ORDER BY date DESC LIMIT 30")
    suspend fun recent(): List<SleepEntity>

    @Query("SELECT * FROM sleep_log WHERE date >= :from")
    suspend fun since(from: String): List<SleepEntity>

    @Query("SELECT * FROM sleep_log WHERE date = :date LIMIT 1")
    suspend fun getForDate(date: String): SleepEntity?

    @Insert
    suspend fun insert(entry: SleepEntity)

    @Query("SELECT COUNT(*) FROM sleep_log")
    suspend fun count(): Int
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise_log WHERE date = :date ORDER BY time DESC")
    suspend fun getForDate(date: String): List<ExerciseLogEntity>

    @Query("SELECT * FROM exercise_log ORDER BY date DESC, time DESC LIMIT 60")
    suspend fun recent(): List<ExerciseLogEntity>

    @Query("SELECT * FROM exercise_log WHERE date >= :from ORDER BY date DESC, time DESC")
    suspend fun since(from: String): List<ExerciseLogEntity>

    @Insert
    suspend fun insert(entry: ExerciseLogEntity): Long

    @Query("DELETE FROM exercise_log WHERE id = :id")
    suspend fun delete(id: Long)
}
