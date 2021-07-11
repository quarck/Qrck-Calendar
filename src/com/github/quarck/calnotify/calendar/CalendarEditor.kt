//
//   Calendar Notifications Plus
//   Copyright (C) 2017 Sergey Parshin (s.parshin.sc@gmail.com)
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

package com.github.quarck.calnotify.calendar

import android.content.Context
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.CalNotifyController
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.permissions.PermissionsManager

class CalendarEditor(val provider: CalendarProvider) {

    fun createEvent(context: Context, calendarId: Long, calendarOwnerAccount: String, details: CalendarEventDetails): Long {

        DevLog.info(LOG_TAG, "Request to create an event")

        if (!PermissionsManager.hasAllPermissions(context)) {
            DevLog.error(LOG_TAG, "createEvent: no permissions");
            return -1L;
        }

        val eventId = provider.createEvent(context, calendarId, calendarOwnerAccount, details)

        if (eventId != -1L) {
            DevLog.info(LOG_TAG, "Created new event, id $eventId")
            CalNotifyController.CalendarMonitor.onEventEditedByUs(context, eventId);
        }
        else {
            DevLog.info(LOG_TAG, "Failed to create a new event")
        }

        return eventId
    }

    fun moveEventForward(context: Context, event: EventAlertRecord, newStartTime: Long): EventAlertRecord? {

        if (!PermissionsManager.hasAllPermissions(context)) {
            DevLog.error(LOG_TAG, "moveEventForward: no permissions");
            return null
        }

        if (newStartTime <= event.startTime) {
            DevLog.error(LOG_TAG, "moveEventForward: disallowing move backward ${event.startTime} -> ${newStartTime}")
            return null
        }

        val newEndTime = event.endTime + (newStartTime - event.startTime)
        val currentTime = System.currentTimeMillis()

        if (currentTime + Consts.ALARM_THRESHOLD > newStartTime) {
            DevLog.error(LOG_TAG, "moveEventForward: new start time is already in the past: ${newStartTime} vs current ${currentTime}")
            return null
        }

        // Get full event details from the provider, if failed - construct a failback version
        val oldDetails =
                provider.getEvent(context, event.eventId)?.details
                        ?: CalendarEventDetails(
                        title = event.title,
                        desc = "",
                        location = event.location,
                        timezone = "",
                        startTime = event.startTime,
                        endTime = event.endTime,
                        isAllDay = event.isAllDay,
                        reminders = listOf<EventReminderRecord>(EventReminderRecord.minutes(15)),
                        color = event.color
                )

        DevLog.info(LOG_TAG, "Moving event ${event.eventId} from ${event.startTime} / ${event.endTime} to $newStartTime / $newEndTime")

        val ret = provider.moveEvent(context, event.eventId, newStartTime, newEndTime)
        if (ret) {
            DevLog.info(LOG_TAG, "Provider move event for ${event.eventId} result: $ret")
            DevLog.info(LOG_TAG, "Adding move request into DB: move: ${event.eventId} ${oldDetails.startTime} / ${oldDetails.endTime} -> $newStartTime / $newEndTime")

            if (event.eventId != -1L) {
                CalNotifyController.CalendarMonitor.onEventEditedByUs(context, event.eventId);
            }

            return event.copy(
                    startTime = newStartTime,
                    endTime = newEndTime,
                    instanceStartTime = newStartTime,
                    instanceEndTime = newEndTime
            )
        } else {
            return null
        }
    }

    fun moveRepeatingForwardAsCopy(context: Context, calendar: CalendarRecord, event: EventAlertRecord, newStartTime: Long): EventAlertRecord? {

        if (!PermissionsManager.hasAllPermissions(context)) {
            DevLog.error(LOG_TAG, "moveRepeatingForwardAsCopy: no permissions");
            return null
        }

        if (newStartTime <= event.instanceStartTime) {
            DevLog.error(LOG_TAG, "moveRepeatingForwardAsCopy: disallowing move backward ${event.instanceStartTime} -> ${newStartTime}")
            return null
        }

        val newEndTime = event.instanceEndTime + (newStartTime - event.instanceStartTime)
        val currentTime = System.currentTimeMillis()

        if (currentTime + Consts.ALARM_THRESHOLD > newStartTime) {
            DevLog.error(LOG_TAG, "moveRepeatingForwardAsCopy: new start time is already in the past: ${newStartTime} vs current ${currentTime}")
            return null
        }


        // Get full event details from the provider, if failed - construct a failback version
        val oldEvent = provider.getEvent(context, event.eventId) ?: return null

        DevLog.info(LOG_TAG, "Moving event ${event.eventId} from ${event.instanceStartTime} / ${event.instanceEndTime} to $newStartTime / $newEndTime")

        val details = oldEvent.details.copy(
                startTime = newStartTime,
                endTime = newEndTime,
                duration = null,
                rRule = "",
                rDate = "",
                exRRule = "",
                exRDate = ""
        )

        val ret = createEvent(context, calendar.calendarId, calendar.owner, details)
        if (ret != -1L) {
            return event.copy(
                    eventId = ret,
                    startTime = newStartTime,
                    endTime = newEndTime,
                    instanceStartTime = newStartTime,
                    instanceEndTime = newEndTime
            )
        }

        return null
    }

//    fun updateEvent(context: Context, eventToEdit: EventRecord, details: CalendarEventDetails): Boolean {
//
//        if (!PermissionsManager.hasAllPermissions(context)) {
//            DevLog.error(LOG_TAG, "updateEvent: no permissions");
//            return false;
//        }
//
//        val ret = provider.updateEvent(context, eventToEdit, details)
//        if (ret) {
//            DevLog.info(LOG_TAG, "Successfully updated provider, event ${eventToEdit.eventId}")
//        }
//        else {
//            DevLog.error(LOG_TAG, "Failed to updated provider, event ${eventToEdit.eventId}")
//        }
//
//        DevLog.info(LOG_TAG, "Adding edit request into DB: ${eventToEdit.eventId} ")
//
//        if (ret && (eventToEdit.startTime != details.startTime)) {
//
//            DevLog.info(LOG_TAG, "Event ${eventToEdit.eventId} was moved, ${eventToEdit.startTime} != ${details.startTime}, checking for notification auto-dismissal")
//
//            val newEvent = provider.getEvent(context, eventToEdit.eventId)
//
//            if (newEvent != null) {
//                CalNotifyController.onCalendarEventMovedWithinApp(
//                        context,
//                        eventToEdit,
//                        newEvent
//                )
//            }
//        }
//
//        if (eventToEdit.eventId != -1L) {
//            CalNotifyController.CalendarMonitor.onEventEditedByUs(context, eventToEdit.eventId);
//        }
//
//        return ret
//    }

    companion object {
        const val LOG_TAG = "CalendarChangeMonitor"
    }
}