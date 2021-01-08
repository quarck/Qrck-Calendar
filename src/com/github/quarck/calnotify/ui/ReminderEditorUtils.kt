//
//   Calendar Notifications Plus
//   Copyright (C) 2018 Sergey Parshin (s.parshin.sc@gmail.com)
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

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.*
import android.text.format.DateUtils
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.android.calendar.CalendarEventModel
import com.android.calendar.Utils
import com.github.quarck.calnotify.Consts
import org.qrck.seshat.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.TagsManager
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.calendar.CalendarEditor
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.textutils.EventFormatter
import com.github.quarck.calnotify.utils.textutils.dateToStr
import com.github.quarck.calnotify.utils.*
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.*

interface IReminderEditorListener {
    fun modifyReminder(existingReminderView: View, newReminder: CalendarEventModel.ReminderEntry)
    fun addReminder(reminder: CalendarEventModel.ReminderEntry, isForAllDay: Boolean)
}

object ReminderEditorUtils {
    @JvmStatic
    fun getAllDayReminderDaysBefore(millisecondsBefore: Long): Int =
            ((millisecondsBefore + Consts.DAY_IN_MILLISECONDS) / Consts.DAY_IN_MILLISECONDS).toInt()

    @JvmStatic
    fun getAllDayReminderHourOfDayAndMinute(millisecondsBefore: Long): Pair<Int, Int> {
        val timeOfDayMillis =
                if (millisecondsBefore >= 0L) { // on the day of event
                    Consts.DAY_IN_MILLISECONDS - millisecondsBefore % Consts.DAY_IN_MILLISECONDS
                } else {
                    -millisecondsBefore
                }

        val timeOfDayMinutes = timeOfDayMillis.toInt() / 1000 / 60

        val minute = timeOfDayMinutes % 60
        val hourOfDay = timeOfDayMinutes / 60

        return Pair(hourOfDay, minute)
    }

    @JvmStatic
    fun eventReminderMillisToLocalizedString(ctx: Context, isAllDay: Boolean, millisecondsBefore: Long, method: Int): String {
        val ret = StringBuilder()

        if (!isAllDay) {
            val duration = EventFormatter(ctx).formatTimeDuration(millisecondsBefore, 60L)

            ret.append(
                    ctx.resources.getString(R.string.add_event_fmt_before).format(duration)
            )
        } else {
            val fullDaysBefore = getAllDayReminderDaysBefore(millisecondsBefore)
            val (hr, min) = getAllDayReminderHourOfDayAndMinute(millisecondsBefore)

            val cal = DateTimeUtils.createCalendarTime(System.currentTimeMillis(), hr, min)

            val time = DateUtils.formatDateTime(ctx, cal.timeInMillis, DateUtils.FORMAT_SHOW_TIME)

            when (fullDaysBefore) {
                0 ->
                    ret.append(
                            ctx.resources.getString(R.string.add_event_zero_days_before).format(time)
                    )
                1 ->
                    ret.append(
                            ctx.resources.getString(R.string.add_event_one_day_before).format(time)
                    )
                else ->
                    ret.append(
                            ctx.resources.getString(R.string.add_event_n_days_before).format(fullDaysBefore, time)
                    )
            }
        }

        when (method) {
            CalendarContract.Reminders.METHOD_EMAIL -> {
                ret.append(" ")
                ret.append(ctx.resources.getString(R.string.add_event_as_email_suffix))
            }
            CalendarContract.Reminders.METHOD_SMS -> {
                ret.append(" ")
                ret.append(ctx.resources.getString(R.string.add_event_as_sms_suffix))
            }
            CalendarContract.Reminders.METHOD_ALARM -> {
                ret.append(" ")
                ret.append(ctx.resources.getString(R.string.add_event_as_alarm_suffix))
            }
        }

        return ret.toString()
    }

    @JvmStatic
    fun eventReminderMinutesToLocalizedString(ctx: Context, isAllDay: Boolean, minutesBefore: Int, method: Int): String {
        return eventReminderMillisToLocalizedString(ctx, isAllDay, minutesBefore * Consts.MINUTE_IN_MILLISECONDS, method)
    }


    @JvmStatic
    fun showAddReminderCustomDialog(act: Activity, listener: IReminderEditorListener,
                                    currentReminder: CalendarEventModel.ReminderEntry, existingReminderView: View?) {

        val dialogView = act.layoutInflater.inflate(R.layout.dialog_add_event_notification, null);

        val timeIntervalPicker = TimeIntervalPickerController(dialogView, null,
                Consts.NEW_EVENT_MAX_REMINDER_MILLISECONDS_BEFORE, false)
        timeIntervalPicker.intervalMilliseconds = currentReminder.millisecondsBefore

        val isEmailCb = dialogView.findViewById<CheckBox?>(R.id.checkbox_as_email)

        val builder = AlertDialog.Builder(act)

        builder.setView(dialogView)

        builder.setPositiveButton(android.R.string.ok) {
            _: DialogInterface?, _: Int ->

            var intervalMilliseconds = timeIntervalPicker.intervalMilliseconds
            val isEmail = isEmailCb?.isChecked ?: false

            if (intervalMilliseconds > Consts.NEW_EVENT_MAX_REMINDER_MILLISECONDS_BEFORE) {
                intervalMilliseconds = Consts.NEW_EVENT_MAX_REMINDER_MILLISECONDS_BEFORE
                Toast.makeText(act, R.string.new_event_max_reminder_is_28_days, Toast.LENGTH_LONG).show()
            }

            val reminder = CalendarEventModel.ReminderEntry.valueOf(
                    (intervalMilliseconds / Consts.MINUTE_IN_MILLISECONDS).toInt(),
                    if (isEmail) CalendarContract.Reminders.METHOD_EMAIL
                    else CalendarContract.Reminders.METHOD_DEFAULT
            )

            if (existingReminderView != null)
                listener.modifyReminder(existingReminderView, reminder)
            else
                listener.addReminder(reminder, isForAllDay = false)
        }

        builder.setNegativeButton(android.R.string.cancel) {
            _: DialogInterface?, _: Int ->
        }

        builder.create().show()
    }

    @JvmStatic
    fun showAddReminderListDialog(act: Activity, listener: IReminderEditorListener,
                                  currentReminder: CalendarEventModel.ReminderEntry, existingReminderView: View?) {

        if (currentReminder.method != CalendarContract.Reminders.METHOD_DEFAULT)
            return showAddReminderCustomDialog(act, listener, currentReminder, existingReminderView)

        val intervalNames: Array<String> = act.resources.getStringArray(R.array.reminder_intervals_labels)
        val intervalValues = act.resources.getIntArray(R.array.reminder_intervals_milliseconds_values)

        if (intervalValues.find { it.toLong() == currentReminder.millisecondsBefore } == null) {
            // reminder is not one of standard ones - we have to show custom idalog
            return showAddReminderCustomDialog(act, listener, currentReminder, existingReminderView)
        }

        val builder = AlertDialog.Builder(act)

        val adapter = ArrayAdapter<String>(act, R.layout.simple_list_item_medium)

        adapter.addAll(intervalNames.toMutableList())

        builder.setCancelable(true)

        builder.setAdapter(adapter) {
            _, which ->
            if (which in 0..intervalValues.size-1) {

                val intervalMillis = intervalValues[which].toLong()
                if (intervalMillis != -1L) {
                    if (existingReminderView != null)
                        listener.modifyReminder(existingReminderView, CalendarEventModel.ReminderEntry.valueOf((intervalMillis / Consts.MINUTE_IN_MILLISECONDS).toInt()))
                    else
                        listener.addReminder(CalendarEventModel.ReminderEntry.valueOf((intervalMillis / Consts.MINUTE_IN_MILLISECONDS).toInt()), isForAllDay = false)
                } else {
                    showAddReminderCustomDialog(act, listener, currentReminder, existingReminderView)
                }
            }
        }

        builder.setNegativeButton(android.R.string.cancel) {
            _: DialogInterface?, _: Int ->
        }

        builder.show()
    }

    @JvmStatic
    fun showAddReminderCustomAllDayDialog(act: Activity, listener: IReminderEditorListener,
                                          currentReminder: CalendarEventModel.ReminderEntry, existingReminderView: View?) {

        val dialogView = act.layoutInflater.inflate(R.layout.dialog_add_event_allday_notification, null);

        val numberPicker = dialogView.findViewById<NumberPicker>(R.id.number_picker_days_before)
        val timePicker = dialogView.findViewById<TimePicker>(R.id.time_picker_notification_time_of_day)
        val isEmailCb = dialogView.findViewById<CheckBox>(R.id.checkbox_as_email)

        numberPicker.minValue = 0
        numberPicker.maxValue = Consts.NEW_EVENT_MAX_ALL_DAY_REMINDER_DAYS_BEFORE
        numberPicker.value = ReminderEditorUtils.getAllDayReminderDaysBefore(currentReminder.millisecondsBefore)

        timePicker.setIs24HourView(android.text.format.DateFormat.is24HourFormat(act))

        val (hr, min) = ReminderEditorUtils.getAllDayReminderHourOfDayAndMinute(currentReminder.millisecondsBefore)

        timePicker.hour = hr
        timePicker.minute = min


        val builder = AlertDialog.Builder(act)

        builder.setView(dialogView)

        builder.setPositiveButton(android.R.string.ok) {
            _: DialogInterface?, _: Int ->

            numberPicker.clearFocus()
            timePicker.clearFocus()

            val daysBefore = numberPicker.value
            val pickerHr = timePicker.hour
            val pickerMin = timePicker.minute

            val daysInMilliseconds = daysBefore * Consts.DAY_IN_MILLISECONDS
            val hrMinInMilliseconds = pickerHr * Consts.HOUR_IN_MILLISECONDS + pickerMin * Consts.MINUTE_IN_MILLISECONDS
            val reminderTimeMilliseconds = daysInMilliseconds - hrMinInMilliseconds

            val isEmail = isEmailCb.isChecked

            val reminder = CalendarEventModel.ReminderEntry.valueOf(
                    (reminderTimeMilliseconds / Consts.MINUTE_IN_MILLISECONDS).toInt(),
                    if (isEmail) CalendarContract.Reminders.METHOD_EMAIL
                    else CalendarContract.Reminders.METHOD_DEFAULT
            )

            if (existingReminderView != null)
                listener.modifyReminder(existingReminderView, reminder)
            else
                listener.addReminder(reminder, isForAllDay = true)
        }

        builder.setNegativeButton(android.R.string.cancel) {
            _: DialogInterface?, _: Int ->
        }

        builder.create().show()
    }

    @JvmStatic
    fun showAddReminderListAllDayDialog(act: Activity, listener: IReminderEditorListener,
                                        currentReminder: CalendarEventModel.ReminderEntry, existingReminderView: View?) {

        if (currentReminder.method != CalendarContract.Reminders.METHOD_DEFAULT)
            return showAddReminderCustomAllDayDialog(act, listener, currentReminder, existingReminderView)

        val reminderNames: Array<String> = act.resources.getStringArray(R.array.reminder_intervals_all_day_labels)
        val reminderValues = act.resources.getIntArray(R.array.reminder_intervals_all_day_seconds_values)

        val enterManuallyValue = -2147483648

        if (reminderValues.find { it.toLong() == currentReminder.millisecondsBefore / 1000L } == null) {
            // reminder is not one of standard ones - we have to show custom idalog
            return showAddReminderCustomAllDayDialog(act, listener, currentReminder, existingReminderView)
        }

        val builder = AlertDialog.Builder(act)

        val adapter = ArrayAdapter<String>(act, R.layout.simple_list_item_medium)

        adapter.addAll(reminderNames.toMutableList())

        builder.setCancelable(true)

        builder.setAdapter(adapter) {
            _, which ->
            if (which in 0..reminderValues.size-1) {

                val reminderSeconds = reminderValues[which]
                if (reminderSeconds != enterManuallyValue) {

                    val reminderTimeMillis = reminderSeconds.toLong() * 1000L

                    if (existingReminderView != null)
                        listener.modifyReminder(existingReminderView, CalendarEventModel.ReminderEntry.valueOf((reminderTimeMillis / Consts.MINUTE_IN_MILLISECONDS).toInt()))
                    else
                        listener.addReminder(CalendarEventModel.ReminderEntry.valueOf((reminderTimeMillis / Consts.MINUTE_IN_MILLISECONDS).toInt()), isForAllDay = true)
                } else {
                    showAddReminderCustomAllDayDialog(act, listener, currentReminder, existingReminderView)
                }
            }
        }

        builder.setNegativeButton(android.R.string.cancel) {
            _: DialogInterface?, _: Int ->
        }

        builder.show()
    }
}

fun EventReminderRecord.toLocalizedString(ctx: Context, isAllDay: Boolean): String =
    ReminderEditorUtils.eventReminderMillisToLocalizedString(ctx, isAllDay, this.millisecondsBefore, this.method)

fun EventReminderRecord.toModelEntry(): CalendarEventModel.ReminderEntry {
    return CalendarEventModel.ReminderEntry.valueOf((this.millisecondsBefore / Consts.MINUTE_IN_MILLISECONDS).toInt(), this.method)
}

fun CalendarEventModel.ReminderEntry.toEventReminderRecord(): EventReminderRecord {
    return EventReminderRecord(this.millisecondsBefore, this.method)
}

val CalendarEventModel.ReminderEntry.millisecondsBefore: Long
    get() = this.minutes * Consts.MINUTE_IN_MILLISECONDS

fun CalendarEventModel.ReminderEntry.toLocalizedString(ctx: Context, isAllDay: Boolean): String =
        ReminderEditorUtils.eventReminderMillisToLocalizedString(ctx, isAllDay, this.millisecondsBefore, this.method)
