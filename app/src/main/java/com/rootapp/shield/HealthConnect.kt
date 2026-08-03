package com.rootapp.shield

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Real body signals from Health Connect (steps + sleep), used when the app is installed and the
 * user grants read access. Everything degrades gracefully: if Health Connect is missing,
 * unpermitted, or errors, callers fall back to Root's own estimators (usage-gap sleep, on-device
 * step sensor). Read-only - Root never writes health data back.
 */
object HealthConnect {
    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    /** True only when a usable Health Connect provider is installed on the device. */
    fun available(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun client(context: Context): HealthConnectClient? =
        if (available(context)) runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() else null

    /** The ActivityResultContract to request our read permissions from Compose. */
    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun granted(context: Context): Set<String> =
        runCatching { client(context)?.permissionController?.getGrantedPermissions() }.getOrNull().orEmpty()

    suspend fun hasAll(context: Context): Boolean = granted(context).containsAll(PERMISSIONS)

    /** Today's step total, or null if unavailable / unpermitted / no data. */
    suspend fun todaySteps(context: Context): Int? {
        val c = client(context) ?: return null
        return runCatching {
            val zone = ZoneId.systemDefault()
            val start = LocalDate.now().atStartOfDay(zone).toInstant()
            val res = c.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, Instant.now()),
                ),
            )
            res[StepsRecord.COUNT_TOTAL]?.toInt()
        }.getOrNull()
    }

    /**
     * Last night's sleep as a [SleepEstimator.Night] (start = when they fell asleep, span = total
     * measured sleep), or null if none/unavailable. Window: yesterday 6pm to today 2pm.
     */
    suspend fun lastNightSleep(context: Context): SleepEstimator.Night? {
        val c = client(context) ?: return null
        return runCatching {
            val zone = ZoneId.systemDefault()
            val start = LocalDate.now().minusDays(1).atTime(18, 0).atZone(zone).toInstant()
            val end = LocalDate.now().atTime(14, 0).atZone(zone).toInstant()
            val records = c.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            ).records
            if (records.isEmpty()) {
                null
            } else {
                val totalMin = records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
                val firstStart = records.minOf { it.startTime.toEpochMilli() }
                if (totalMin < 60) null else SleepEstimator.Night(firstStart, firstStart + totalMin * 60_000L)
            }
        }.getOrNull()
    }
}
