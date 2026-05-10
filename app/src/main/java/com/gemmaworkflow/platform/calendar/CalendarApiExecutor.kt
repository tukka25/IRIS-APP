package com.gemmaworkflow.platform.calendar

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gemmaworkflow.domain.model.ExecutionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Silently creates calendar events via ContentResolver.insert() instead of
 * launching the calendar app with Intent.ACTION_INSERT.
 *
 * Requires android.permission.WRITE_CALENDAR (runtime grant).
 * The calendar must be synced/visible (has an account).
 */
class CalendarApiExecutor(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    companion object {
        private const val TAG = "CalendarApiExecutor"
        private const val DEFAULT_EVENT_DURATION_MILLIS = 60 * 60 * 1000L
        const val PERMISSION_READ_CALENDAR = Manifest.permission.READ_CALENDAR
        const val PERMISSION_WRITE_CALENDAR = Manifest.permission.WRITE_CALENDAR
    }

    /**
     * Returns true if calendar permissions are already granted.
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
            PERMISSION_WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns true if WRITE_CALENDAR can be requested from the provided Activity.
     */
    fun canRequestPermission(activity: Activity): Boolean {
        return !hasPermission() && !activity.isFinishing
    }

    /**
     * Returns true when the UI should show a rationale before requesting permission.
     */
    fun shouldShowPermissionRationale(activity: Activity): Boolean {
        return !hasPermission() &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, PERMISSION_WRITE_CALENDAR)
    }

    /**
     * Execute the calendar.create_event action with the given params.
     * Returns an ExecutionResult with the inserted event URI on success,
     * or a failure result if no writable calendar exists or insert fails.
     */
    fun execute(params: JsonObject): ExecutionResult {
        if (!hasPermission()) {
            return ExecutionResult(
                stepId = "calendar.create_event",
                success = false,
                message = "WRITE_CALENDAR permission not granted"
            )
        }

        val calendarId = findWritableCalendarId()
        if (calendarId == null) {
            return ExecutionResult(
                stepId = "calendar.create_event",
                success = false,
                message = "No writable synced calendar found"
            )
        }

        val values = buildContentValues(params, calendarId)
        return try {
            val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) {
                Log.i(TAG, "Event inserted: $uri")
                ExecutionResult(
                    stepId = "calendar.create_event",
                    success = true,
                    message = "Event created: $uri"
                )
            } else {
                ExecutionResult(
                    stepId = "calendar.create_event",
                    success = false,
                    message = "Insert returned null URI"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert event", e)
            ExecutionResult(
                stepId = "calendar.create_event",
                success = false,
                message = e.message ?: "Failed to create event"
            )
        }
    }

    /**
     * Find the first synced (visible) calendar that the account can write to.
     * Returns the calendar ID as a Long, or null if none found.
     */
    fun findWritableCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        // Must be visible and have at least contributor-level access
        val selection = (
            "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= " +
                "${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
            )

        return try {
            contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                    cursor.getLong(idIdx)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query writable calendar", e)
            null
        }
    }

    /**
     * Build ContentValues for CalendarContract.Events insert.
     * Maps JSON params to calendar event fields.
     */
    private fun buildContentValues(params: JsonObject, calendarId: Long): ContentValues {
        val beginTimeMillis = params["begin_time_millis"]
            ?.let { it as? JsonPrimitive }
            ?.longOrNull
        val requestedEndTimeMillis = params["end_time_millis"]
            ?.let { it as? JsonPrimitive }
            ?.longOrNull
        val endTimeMillis = when {
            beginTimeMillis == null -> null
            requestedEndTimeMillis == null -> beginTimeMillis + DEFAULT_EVENT_DURATION_MILLIS
            requestedEndTimeMillis <= beginTimeMillis -> beginTimeMillis + DEFAULT_EVENT_DURATION_MILLIS
            else -> requestedEndTimeMillis
        }

        return ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)

            // Title (required)
            params["title"]?.let { v ->
                (v as? JsonPrimitive)?.contentOrNull?.let { put(CalendarContract.Events.TITLE, it) }
            }

            // begin_time_millis -> EVENT_BEGIN
            beginTimeMillis?.let {
                put(CalendarContract.Events.DTSTART, it)
            }

            // end_time_millis -> EVENT_END. CalendarProvider needs an end
            // time or duration, so default to a one-hour event when absent.
            endTimeMillis?.let {
                put(CalendarContract.Events.DTEND, it)
            }

            // location
            params["location"]?.let { v ->
                (v as? JsonPrimitive)?.contentOrNull?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
            }

            // description
            params["description"]?.let { v ->
                (v as? JsonPrimitive)?.contentOrNull?.let { put(CalendarContract.Events.DESCRIPTION, it) }
            }
        }
    }
}
