package com.septuary.app.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Best-effort, silently-fail bridge to Android's Health Connect — auto-fills Weight and Steps
 * so Priyansh doesn't have to log those by hand if he already weighs in / tracks steps elsewhere.
 * Mirrors [SyncRepository]'s posture exactly: nothing here should ever block or crash the
 * patient-facing unlock flow, whether Health Connect is missing, permission was never granted,
 * or a call simply fails. Sleep and heart rate are deliberately not read — not part of this ask.
 */
object HealthConnectRepository {

    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun isAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun requiredPermissions(): Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    suspend fun hasAllPermissions(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable(context)) return@withContext false
            val granted = HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
            granted.containsAll(requiredPermissions())
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Gap-fills weight for the last [days] days (never overwrites a date that already has a
     * row — manual or previously synced) and upserts today's step total. Safe to call
     * unconditionally on every unlock: no-ops if Health Connect isn't installed or permission
     * was never granted, and never throws past this boundary.
     */
    suspend fun sync(context: Context, repo: Repository, days: Int = 14) = withContext(Dispatchers.IO) {
        try {
            if (!hasAllPermissions(context)) return@withContext
            val client = HealthConnectClient.getOrCreate(context)
            val zone = ZoneId.systemDefault()
            val end = Instant.now()
            val start = end.minus(days.toLong(), ChronoUnit.DAYS)

            // --- Weight: fill only the dates that don't already have a row ---
            try {
                val weightRecords = client.readRecords(
                    ReadRecordsRequest(
                        recordType = WeightRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, end)
                    )
                ).records

                val latestPerDate = weightRecords
                    .groupBy { ZonedDateTime.ofInstant(it.time, zone).format(DATE_FMT) }
                    .mapValues { (_, records) -> records.maxByOrNull { it.time }!! }

                for ((date, record) in latestPerDate) {
                    if (!repo.weightExistsForDate(date)) {
                        repo.addWeight(kg = record.weight.inKilograms, onDate = date, source = "health_connect")
                    }
                }
            } catch (_: Exception) {
                // Weight sync failing shouldn't block steps sync below.
            }

            // --- Steps: today's running total via aggregate (avoids double-counting across
            // multiple source apps, which readRecords on individual StepsRecords would risk) ---
            try {
                val todayStart = ZonedDateTime.now(zone).toLocalDate().atStartOfDay(zone).toInstant()
                val response = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(todayStart, end)
                    )
                )
                val total = response[StepsRecord.COUNT_TOTAL] ?: 0L
                repo.upsertSteps(repo.todayKey(), total.toInt())
            } catch (_: Exception) {
                // Steps sync is independent of weight — same best-effort posture.
            }
        } catch (_: Exception) {
            // Health Connect unavailable, permission revoked mid-call, etc. — never surface to the UI.
        }
    }
}
