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
    val active: Boolean = true
)

@Entity(tableName = "dose_log", primaryKeys = ["doseKey", "date"])
data class DoseLogEntity(
    val doseKey: String,     // medicationId + "@" + time
    val date: String,        // "YYYY-MM-DD"
    val takenAtIso: String
)

@Entity(tableName = "weight_log")
data class WeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val time: String,
    val kg: Double,
    val note: String
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
