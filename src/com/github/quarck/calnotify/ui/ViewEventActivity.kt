//
//   Calendar Notifications Plus  
//   Copyright (C) 2016 Sergey Parshin (s.parshin.sc@gmail.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
// 
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with this program; if not, write to the Free Software Foundation,
//   Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
//

package com.github.quarck.calnotify.ui

//import com.github.quarck.calnotify.utils.logs.Logger

import android.app.DatePickerDialog
import android.content.ContentUris
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.*
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.android.calendar.CalendarController
import com.android.calendar.DeleteEventHelper
import com.android.calendar.DynamicTheme
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.CalNotifyController
import com.github.quarck.calnotify.app.SnoozeResult
import com.github.quarck.calnotify.app.SnoozeType
import com.github.quarck.calnotify.app.toast
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitor
import com.github.quarck.calnotify.calendarmonitor.CalendarReloadManager
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.utils.*
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.maps.MapsIntents
import com.github.quarck.calnotify.utils.textutils.EventFormatter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.qrck.seshat.R
import java.util.*


// TODO: add repeating rule and calendar name somewhere on the snooze activity

open class ViewEventActivity : AppCompatActivity() {

    lateinit var event: EventAlertRecord

    lateinit var calendar: CalendarRecord

    lateinit var snoozePresets: LongArray

    lateinit var settings: Settings

    lateinit var formatter: EventFormatter

    private val calendarReloadManager = CalendarReloadManager
    private val calendarProvider = CalendarProvider

    var hasEventInDB = false

    lateinit var calendarNameTextView: TextView
    lateinit var calendarAccountTextView: TextView

    var isUpcoming = false

    val dynamicTheme = DynamicTheme()

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        if (!PermissionsManager.hasAllPermissions(this)) {
            finish()
            return
        }

        dynamicTheme.onCreate(this)

        setContentView(R.layout.activity_view)

        setSupportActionBar(findViewById<Toolbar?>(R.id.toolbar))
        supportActionBar?.let{
            it.setDisplayHomeAsUpEnabled(true)
            it.setHomeAsUpIndicator(R.drawable.ic_clear_white_24dp)
            it.setDisplayShowHomeEnabled(true)
        }
        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black)

        val currentTime = System.currentTimeMillis()

        settings = Settings(this)
        formatter = EventFormatter(this)

        // Populate event details
        var eventId = intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1)
        var instanceStartTime = intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1L)
        var instanceEndTime = -1L
        val alertTime = intent.getLongExtra(Consts.INTENT_ALERT_TIME, 0L)
        var attendeeResponse = Attendees.ATTENDEE_STATUS_NONE

        if (intent != null && Intent.ACTION_VIEW == intent.action) {
            instanceStartTime = intent.getLongExtra(EXTRA_EVENT_BEGIN_TIME, 0L)
            instanceEndTime = intent.getLongExtra(EXTRA_EVENT_END_TIME, 0L)
            attendeeResponse = intent.getIntExtra(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_NONE)

            val data: Uri? = intent.data
            if (data != null) {
                try {
                    val pathSegments: List<String> = data.getPathSegments()
                    val size = pathSegments.size
                    if (size > 2 && "EventTime" == pathSegments[2]) {
                        // Support non-standard VIEW intent format:
                        //dat = content://com.android.calendar/events/[id]/EventTime/[start]/[end]
                        eventId = pathSegments[1].toLong()
                        if (size > 4) {
                            instanceStartTime = pathSegments[3].toLong()
                            instanceEndTime = pathSegments[4].toLong()
                        }
                    } else {
                        eventId = data.getLastPathSegment()?.toLong() ?: -1L
                    }
                } catch (e: NumberFormatException) {
//                    eventId = -1L
                    instanceStartTime = 0
                    instanceEndTime = 0
                }
            }

            hasEventInDB = false
        }
        else {
            hasEventInDB = !isUpcoming
        }

        if (hasEventInDB) {
            EventsStorage(this).use { db ->

                var dbEvent = db.getEvent(eventId, instanceStartTime)

                if (dbEvent != null) {
                    val eventDidChange = calendarReloadManager.reloadSingleEvent(this, db, dbEvent, calendarProvider, noAutoDismiss = true)
                    if (eventDidChange) {
                        val newDbEvent = db.getEvent(eventId, instanceStartTime)
                        if (newDbEvent != null) {
                            dbEvent = newDbEvent
                        } else {
                            DevLog.error(LOG_TAG, "ViewActivity: cannot find event after calendar reload, event $eventId, inst $instanceStartTime")
                        }
                    }
                }

                if (dbEvent == null) {
                    DevLog.error(LOG_TAG, "ViewActivity started for non-existing eveng id $eventId, st $instanceStartTime")
                    finish()
                    return
                }

                event = dbEvent
            }
        } else {
            var calEvent = CalendarProvider.getEventAlertsForInstanceAt(this, instanceStartTime, eventId)
                    .firstOrNull { alertTime == 0L || it.alertTime == alertTime }
            if (calEvent == null) {
                calEvent = CalendarProvider.getInstancesInRange(this, instanceStartTime, instanceStartTime + 100L, eventId)
                        .firstOrNull()
            }
            if (calEvent == null) {
                DevLog.error(LOG_TAG, "ViewActivity started for non-existing eveng id $eventId, st $instanceStartTime")
                finish()
                return
            }
            event = calEvent
        }

        calendar = calendarProvider.getCalendarById(this, event.calendarId)
                ?: calendarProvider.createCalendarNotFoundCal(this)

        calendarNameTextView = findViewById<TextView>(R.id.view_event_calendar_name)
        calendarNameTextView.text = calendar.displayName

        calendarAccountTextView = findViewById<TextView>(R.id.view_event_calendar_account)
        calendarAccountTextView.text = calendar.accountName

        snoozePresets = Consts.DEFAULT_SNOOZE_PRESETS

        // remove "MM minutes before event" snooze presents for "Snooze All"
        // and when event time has passed already
        if (event.displayedStartTime < currentTime)
            snoozePresets = snoozePresets.filter { it > 0L }.toLongArray()

        val presetRoot = findViewById<LinearLayout>(R.id.event_view_snooze_sub_layout)

        for (p in snoozePresets) {
            val preset = p
            val childLayout = layoutInflater.inflate(R.layout.snooze_preset_layout_template, null)
            val textView = childLayout.findViewById<TextView>(R.id.event_view_snooze_template)
            textView.text = formatSnoozePreset(this, preset)
            textView.setOnClickListener { snoozeEvent(preset) }
            presetRoot.addView(childLayout)
        }

        val location = event.location;
        if (location != "") {
            findViewById<View>(R.id.event_view_location_layout).visibility = View.VISIBLE;
            val locationView = findViewById<TextView>(R.id.event_view_location)
            locationView.text = location;
            locationView.setOnClickListener { MapsIntents.openLocation(this, event.location) }
        }

        // title
        val title = findViewById<TextView>(R.id.event_view_title)
        title.text = if (event.title.isNotEmpty()) event.title else this.resources.getString(R.string.empty_title);
        title.setTextIsSelectable(true)

        findViewById<View>(R.id.event_view_event_color_view).setBackgroundColor(event.color.adjustCalendarColor(darker = false))

        // date
        val (line1, line2) = formatter.formatDateTimeTwoLines(event)

        findViewById<TextView>(R.id.event_view_date_line1).apply {
            text = line1
        }

        findViewById<TextView>(R.id.event_view_date_line2).apply {
            text = line2
            visibility = if (line2.isNotEmpty()) View.VISIBLE else View.GONE
        }

        var eventTimeZoneOffset = 0
        if (event.timeZone.isNotBlank()) {
            try {
                val eventTimeZone = java.util.TimeZone.getTimeZone(event.timeZone)
                eventTimeZoneOffset = eventTimeZone.getOffset(event.instanceStartTime)
               // val deviceTimeZone = java.util.TimeZone.getDefault()
                ///deviceTimeZoneOffset = deviceTimeZone.getOffset(event.instanceStartTime)
            }
            catch (ex: Exception) {
            }
        }

        findViewById<TextView>(R.id.event_view_timezone).apply {
            visibility = View.GONE
        }

        // recurrence
        findViewById<TextView>(R.id.event_view_recurrence).apply {
            if (event.rRule.isNotBlank() || event.rDate.isNotBlank()) {

                val recurrence = CalendarRecurrence.tryInterpretRecurrence(
                        event.instanceStartTime,
                        event.timeZone,
                        event.rRule,
                        event.rDate,
                        event.exRRule,
                        event.exRDate
                )

                if (recurrence != null) {
                    text = recurrence.toString()
                }
                else {
                    text = "Failed to parse: ${event.rRule} / ${event.rDate}"
                }
                visibility = View.VISIBLE
            }
            else {
                visibility = View.GONE
            }
        }

        if (event.desc.isNotEmpty()) {
            // Show the event desc
            findViewById<RelativeLayout>(R.id.layout_event_description).visibility = View.VISIBLE
            findViewById<TextView>(R.id.event_view_description).text = event.desc
        }

        if (hasEventInDB) {
            findViewById<RelativeLayout>(R.id.snooze_layout).visibility = View.VISIBLE
        } else {
            findViewById<RelativeLayout>(R.id.snooze_layout).visibility = View.GONE
        }

        if (event.snoozedUntil != 0L) {
            findViewById<TextView>(R.id.snooze_snooze_for)?.text = resources.getString(R.string.change_snooze_to)
        }


        val reminders: List<EventReminderRecord> = calendarProvider.getEventReminders(this@ViewEventActivity, event.eventId)
        if (reminders.isNotEmpty()) {
            findViewById<RelativeLayout>(R.id.event_view_reminders_layout).visibility = View.VISIBLE

            findViewById<TextView>(R.id.event_view_reminders).text =
                    reminders.joinToString(separator = "\n") { it.toLocalizedString(this, event.isAllDay) }

            val nextReminder = calendarProvider.getNextEventReminderTime(this, event)
            if (nextReminder != 0L) {
                findViewById<TextView>(R.id.label_next).visibility = View.VISIBLE
                findViewById<TextView>(R.id.event_view_next_reminder).apply {
                    visibility = View.VISIBLE
                    text = formatter.formatTimePoint(nextReminder)
                }
            }
            else {
                findViewById<TextView>(R.id.label_next).visibility = View.GONE
                findViewById<TextView>(R.id.event_view_next_reminder).visibility = View.GONE
            }
        }
        else {
            findViewById<RelativeLayout>(R.id.event_view_reminders_layout).visibility = View.GONE
        }

        val fabMoveButton = findViewById<FloatingActionButton>(R.id.floating_move_button)

        val fabColorStateList =  ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf(android.R.attr.state_pressed)),
                intArrayOf(event.color.adjustCalendarColor(false), event.color.adjustCalendarColor(true)))

        fabMoveButton.backgroundTintList = fabColorStateList

        if ((isUpcoming || hasEventInDB) && !calendar.isReadOnly) {
            fabMoveButton.setOnClickListener(this::showMoveMenu)
        }
        else {
            fabMoveButton.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        dynamicTheme.onResume(this)
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.view_event_menu, menu)

        val allowEdit = !calendar.isReadOnly
        if (!allowEdit) {
            menu.findItem(R.id.action_edit)?.isVisible = false
            menu.findItem(R.id.action_delete_event)?.isVisible = false
        }

        if (!hasEventInDB && event.alertTime != 0L ) {
            menu.findItem(R.id.action_dismiss)?.isVisible = false
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.action_edit -> {
                editEvent(this, event) { finish() }
            }

            R.id.action_delete_event -> {
                confirmAndDelete()
            }

            R.id.action_dismiss -> {
                CalNotifyController.dismissEvent(this, EventFinishType.ManuallyInTheApp, event)
                finish()
            }
        }

        return super.onOptionsItemSelected(item)
    }

    fun showMoveMenu(v: View) {
        val popup = PopupMenu(this, v)
        val inflater = popup.menuInflater
        inflater.inflate(R.menu.move_options, popup.menu)

        val eventStart = if (event.isRepeating) event.instanceStartTime else event.startTime

        val nextDayStart = eventStart + 1 * Consts.DAY_IN_SECONDS * 1000L
        val nextWeekStart = eventStart + 7 * Consts.DAY_IN_SECONDS * 1000L
        val nextMonthStart = eventStart + 30 * Consts.DAY_IN_SECONDS * 1000L

        if (event.isRepeating) {
            popup.menu.findItem(R.id.action_move_next_day)?.isVisible = false
            popup.menu.findItem(R.id.action_move_next_week)?.isVisible = false
            popup.menu.findItem(R.id.action_move_next_month_30d)?.isVisible = false

            popup.menu.findItem(R.id.action_move_copy_next_day)?.isVisible = shouldOfferMove(nextDayStart)
            popup.menu.findItem(R.id.action_move_copy_next_week)?.isVisible = shouldOfferMove(nextWeekStart)
            popup.menu.findItem(R.id.action_move_copy_next_month_30d)?.isVisible = shouldOfferMove(nextMonthStart)
        }
        else {
            popup.menu.findItem(R.id.action_move_copy_next_day)?.isVisible = false
            popup.menu.findItem(R.id.action_move_copy_next_week)?.isVisible = false
            popup.menu.findItem(R.id.action_move_copy_next_month_30d)?.isVisible = false

            popup.menu.findItem(R.id.action_move_next_day)?.isVisible = shouldOfferMove(nextDayStart)
            popup.menu.findItem(R.id.action_move_next_week)?.isVisible = shouldOfferMove(nextWeekStart)
            popup.menu.findItem(R.id.action_move_next_month_30d)?.isVisible = shouldOfferMove(nextMonthStart)
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_move_next_day, R.id.action_move_copy_next_day -> {
                    reschedule(this, event, calendar, nextDayStart, onSuccess = { finish() })
                    true
                }
                R.id.action_move_next_week, R.id.action_move_copy_next_week -> {
                    reschedule(this, event, calendar, nextWeekStart, onSuccess = { finish() })
                    true
                }
                R.id.action_move_next_month_30d, R.id.action_move_copy_next_month_30d -> {
                    reschedule(this, event, calendar, nextMonthStart, onSuccess = { finish() })
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun snoozeEvent(snoozeDelay: Long) {
        DevLog.debug(LOG_TAG, "Snoozing event id ${event.eventId}, snoozeDelay=${snoozeDelay / 1000L}")

        val result = CalNotifyController.snoozeEvent(this, event.eventId, event.instanceStartTime, snoozeDelay);
        result?.toast(this)
        finish()
    }

//    private fun confirmAndReschedule(addDays: Long) {
//
//        AlertDialog.Builder(this)
//                .setMessage(getString(R.string.move_event_confirm).format(addDays))
//                .setCancelable(true)
//                .setPositiveButton(R.string.yes) { _, _ ->
//                    reschedule(addTime = addDays * Consts.DAY_IN_SECONDS * 1000L)
//                }
//                .setNegativeButton(R.string.cancel) { _, _ ->
//                }
//                .create()
//                .show()
//    }
//

    private fun confirmAndDelete() {

        if (calendar.isReadOnly) {
            return
        }
        val mDeleteHelper = DeleteEventHelper(this, this, true);
        mDeleteHelper.setDeleteNotificationListener {}
        mDeleteHelper.setOnDismissListener{}
        mDeleteHelper.delete(event.instanceStartTime, event.instanceEndTime, event.eventId, -1) {
            if (hasEventInDB && event.alertTime != 0L) {
                CalNotifyController.dismissEvent(this, EventFinishType.DeletedInTheApp, event)
            }
            finish()
        }
    }

    companion object {
        private const val LOG_TAG = "ActivitySnooze"

        private fun shouldOfferMove(newStartTime: Long): Boolean
            = newStartTime > System.currentTimeMillis() + Consts.MIN_MOVE_GAP_THRESHOLD

        private fun reschedule(ctx: Context, event: EventAlertRecord, calendar: CalendarRecord?, newStartTime: Long, onSuccess: ()->Unit) {

            DevLog.info(LOG_TAG, "Moving event ${event.eventId} to the new start ${newStartTime}, isRepeating = ${event.isRepeating}");

            if (!event.isRepeating) {
                val moved = CalNotifyController.moveEventForward(ctx, event, newStartTime)

                if (moved != null) {
                    SnoozeResult(SnoozeType.Moved, moved.startTime).toast(ctx) // Show
                    onSuccess()
                } else {
                    DevLog.info(LOG_TAG, "snooze: Failed to move event ${event.eventId} to ${newStartTime}")
                }
            }
            else {
                val cal = calendar ?: CalendarProvider.getCalendarById(ctx, event.calendarId)
                if (cal != null) {
                    val moved = CalNotifyController.moveRepeatingForwardAsCopy(ctx, cal, event, newStartTime)
                    if (moved != null) {
                        SnoozeResult(SnoozeType.Moved, moved.startTime).toast(ctx) // Show
                        onSuccess()
                    } else {
                        DevLog.info(LOG_TAG, "snooze: Failed to move event ${event.eventId} to ${newStartTime}")
                    }
                } else {
                    DevLog.info(LOG_TAG, "snooze: Failed to move event ${event.eventId} to ${newStartTime} - no calendar ${event.calendarId} found")
                }
            }
        }

        private fun pickADateReschedule(ctx: Context, event: EventAlertRecord, onSuccess: () -> Unit) {

            val cal = DateTimeUtils.createCalendarTime(System.currentTimeMillis())

            val dialog = DatePickerDialog(
                    ctx,
                    {
                        _, year, month, day ->

                        cal.year = year
                        cal.month = month
                        cal.dayOfMonth = day

                        val newStart = cal.timeInMillis
                        if (shouldOfferMove(newStart))
                            reschedule(ctx, event, null, newStart, onSuccess)
                    },
                    cal.year,
                    cal.month,
                    cal.dayOfMonth
            )

            dialog.datePicker.firstDayOfWeek = Calendar.MONDAY

            dialog.show()
        }

        fun rescheduleEvent(ctx: Context, event: EventAlertRecord, onSuccess: () -> Unit) {

            val eventStart = if (event.isRepeating) event.instanceStartTime else event.startTime

            val nextDayStart = eventStart + 1 * Consts.DAY_IN_SECONDS * 1000L
            val nextWeekStart = eventStart + 7 * Consts.DAY_IN_SECONDS * 1000L
            val nextMonthStart = eventStart + 30 * Consts.DAY_IN_SECONDS * 1000L

            val res = ctx.resources

            val listValues = MutableList<Int>(0, {0})
            val listNames = MutableList<String>(0, {""})

            if (shouldOfferMove(nextDayStart)) {
                listValues.add(R.id.action_move_next_day)
                listNames.add(if (event.isRepeating) res.getString(R.string.copy_next_day) else res.getString(R.string.next_day))
            }
            if (shouldOfferMove(nextWeekStart)) {
                listValues.add(R.id.action_move_next_week)
                listNames.add(if (event.isRepeating) res.getString(R.string.copy_next_week) else res.getString(R.string.next_week))
            }
            if (shouldOfferMove(nextMonthStart)) {
                listValues.add(R.id.action_move_next_month_30d)
                listNames.add(if (event.isRepeating) res.getString(R.string.copy_next_month_30d) else res.getString(R.string.next_month_30d))
            }

            listValues.add(R.id.action_move_pick_a_date)
            listNames.add(if (event.isRepeating) res.getString(R.string.pick_a_date_copy_inst) else res.getString(R.string.pick_a_date))

            val adapter = ArrayAdapter<String>(ctx, R.layout.simple_list_item_medium)
            adapter.addAll(listNames)

            val builder = AlertDialog.Builder(ctx)
            builder.setTitle(res.getString(R.string.reschedule_event_title))
            builder.setCancelable(true)
            builder.setAdapter(adapter) {
                _, which ->
                if (which in 0..listValues.size-1) {
                    val itemId = listValues[which]
                    when (itemId) {
                        R.id.action_move_next_day -> {
                            reschedule(ctx, event, null, nextDayStart, onSuccess)
                        }
                        R.id.action_move_next_week -> {
                            reschedule(ctx, event, null, nextWeekStart, onSuccess)
                        }
                        R.id.action_move_next_month_30d -> {
                            reschedule(ctx, event, null, nextMonthStart, onSuccess)
                        }
                        R.id.action_move_pick_a_date -> {
                            pickADateReschedule(ctx, event, onSuccess)
                        }
                    }
                }
            }

            builder.setNegativeButton(android.R.string.cancel) {
                _: DialogInterface?, _: Int ->
            }

            builder.show()

        }

        fun editEvent(ctx: Context, event: EventAlertRecord, onComplete: () -> Unit) {
            val uri = ContentUris.withAppendedId(Events.CONTENT_URI, event.eventId)
            val intent = Intent(Intent.ACTION_EDIT, uri)
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.instanceStartTime)
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.instanceEndTime)
            intent.setClass(ctx, com.android.calendar.event.EditEventActivity::class.java)
            intent.putExtra(CalendarController.EVENT_EDIT_ON_LAUNCH, true)
            ctx.startActivity(intent)
            onComplete()
        }
    }

}

class ViewEventActivityUpcoming: ViewEventActivity() {
    init {
        isUpcoming = true
    }
}

class ViewEventActivityLog: ViewEventActivity() {}

