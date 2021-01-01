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

package com.github.quarck.calnotify

import android.content.Context
import com.android.calendar.settings.GeneralPreferences
import com.android.calendar.settings.GeneralPreferences.Companion.getSharedPreferences
import com.github.quarck.calnotify.utils.PersistentStorageBase


class Settings(val context: Context) : PersistentStorageBase(context, "settings") {

    fun getCalendarIsHandled(calendarId: Long): Boolean
            = getBoolean("$CALENDAR_IS_HANDLED_KEY_PREFIX.$calendarId", true)

    fun setCalendarIsHandled(calendarId: Long, enabled: Boolean)
            = setBoolean("$CALENDAR_IS_HANDLED_KEY_PREFIX.$calendarId", enabled)

    val handleEventsWithNoReminders: Boolean
        get() {
            val prefs = getSharedPreferences(context)
            return prefs.getBoolean(GeneralPreferences.KEY_HANDLE_EVENTS_WITH_NO_REMINDERS, false)
        }

    private val defaultReminderTimeMinutes: Int
        get() {
            val prefs = getSharedPreferences(context)
            val defaultReminderString = prefs.getString(GeneralPreferences.KEY_DEFAULT_REMINDER,
                    GeneralPreferences.REMINDER_DEFAULT_TIME.toString())
            return defaultReminderString!!.toInt()
        }

    val defaultReminderTime: Long by lazy { defaultReminderTimeMinutes * 60L * 1000L }

    private val defaultAllDayReminderTimeMinutes: Int
        get() {
            val prefs = getSharedPreferences(context)
            val defaultReminderString = prefs.getString(GeneralPreferences.KEY_DEFAULT_ALL_DAY_REMINDER,
                    GeneralPreferences.REMINDER_DEFAULT_TIME.toString())
            return defaultReminderString!!.toInt()
        }

    val defaultAllDayReminderTime: Long by lazy { defaultAllDayReminderTimeMinutes * 60L * 1000L }

    val notifyOnEmailOnlyEvents: Boolean
        get() {
            val prefs = getSharedPreferences(context)
            return prefs.getBoolean(GeneralPreferences.KEY_HANDLE_EMAIL_ONLY, false)
        }

    var doNotShowBatteryOptimisationWarning: Boolean
        get() = getBoolean(DO_NOT_SHOW_BATTERY_OPTIMISATION, false)
        set(value) = setBoolean(DO_NOT_SHOW_BATTERY_OPTIMISATION, value)

    companion object {
        // Preferences keys
        private const val CALENDAR_IS_HANDLED_KEY_PREFIX = "calendar_handled_"
        private const val DO_NOT_SHOW_BATTERY_OPTIMISATION = "dormi_mi_ne_volas"
    }
}
