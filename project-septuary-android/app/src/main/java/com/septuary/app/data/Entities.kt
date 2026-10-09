package com.septuary.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val detail: String,
    val instruction: String,
    val time: String,        // "HH:MM", 24h
    val days: String,        // "daily" or "weekly:0".."weekly:6" (0=Sunday)
    val startDate: String?,  // "YYYY-MM-DD" or null
    val endDate: String?,    // "YYYY-MM-DD" or null
    val active: Boolean = true,
    // "medication" | "coffee" | "tea" | "meal" — same scheduling/alarm/dose-log machinery
    // powers all four; category only decides which Today section and which Trends bucket
    // (Medicine vs Food) an item counts toward.
    val category: String = "medication",
    // True once the user deliberately changes this item's time in-app; a later schedule update
    // shipped with the app then keeps the user's time instead of overwriting it.
    val timeEdited: Boolean = false
)

@Entity(tableName = "dose_log", primaryKeys = ["doseKey", "date"])
data class DoseLogEntity(
    val doseKey: String,     // medicationId + "@" + time
    val date: String,        // "YYYY-MM-DD"
    val takenAtIso: String,
    // Relative path under filesDir (e.g. "dose_photos/xxx.jpg"), set only when the user
    // optionally attaches a photo to a food/coffee/tea/meal entry. Null for everything else.
    val photoPath: String? = null
)

@Entity(tableName = "weight_log")
data class WeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val time: String,
    val kg: Double,
    val note: String,
    // "manual" (logged in-app) | "health_connect" (auto-synced). Health Connect only ever
    // fills a date that has no row yet — a manual entry always wins and is never overwritten.
    val source: String = "manual"
)

@Entity(tableName = "steps_log")
data class StepsEntity(
    @PrimaryKey val date: String,   // "YYYY-MM-DD" — one row per day, upserted as the count changes
    val stepCount: Int,
    val syncedAtIso: String
)

@Entity(tableName = "glucose_log")
data class GlucoseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val time: String,
    val type: String,
    val value: Int
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metric: String,
    val baseline: String,
    val phase1Target: String,
    val longTerm: String
)

@Entity(tableName = "flags")
data class FlagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val resolved: Boolean
)

@Entity(tableName = "exercise_log")
data class ExerciseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,        // "YYYY-MM-DD"
    val time: String,        // "HH:MM", 24h
    val type: String,        // "Swimming" | "Cycling" | "Walking" | "Yoga"
    val minutes: Int,
    val note: String = ""
)

@Entity(tableName = "sleep_log")
data class SleepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,        // "YYYY-MM-DD" of the morning this entry is logged for
    val bedTime: String,     // "HH:MM", 24h
    val wakeTime: String,    // "HH:MM", 24h
    val hours: Double,       // derived from bedTime/wakeTime at log time, stored for cheap querying
    val quality: Int,        // 1..5 (Poor..Excellent)
    val note: String = ""
)
