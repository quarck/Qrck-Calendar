//
//   Calendar Notifications Plus
//   Copyright (C) 2020 Sergey Parshin (s.parshin.sc@gmail.com)
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

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.android.calendar.Utils
import org.qrck.seshat.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.notification.NotificationChannelManager
import com.github.quarck.calnotify.prefs.CalendarsActivity
import com.github.quarck.calnotify.prefs.DefaultManualAllDayNotificationPreference
import com.github.quarck.calnotify.prefs.DefaultManualNotificationPreference
import com.github.quarck.calnotify.prefs.preferences

class MainActivitySettingsFragment : Fragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        context?.let {
            ctx ->
            val settings = Settings(ctx)
            val (_, root) = preferences(ctx, inflater, container) {

                item(R.string.title_calendars_activity) {
                    startActivity(Intent(ctx, CalendarsActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                }

                header(R.string.main_notifications)

                item(R.string.regular_notification_settings) {
                    NotificationChannelManager.launchSystemSettingForChannel(ctx, NotificationChannelManager.SoundState.Normal)
                }

                item(R.string.alarm_notification_settings) {
                    NotificationChannelManager.launchSystemSettingForChannel(ctx, NotificationChannelManager.SoundState.Alarm)
                }

                header(R.string.calendar_handling_options)

                switch(R.string.handle_email_only_events_title, R.string.handle_email_only_events_summary) {
                    initial(settings.notifyOnEmailOnlyEvents)
                    onChange{settings.notifyOnEmailOnlyEvents = it}
                }

                switch(R.string.handle_events_with_no_reminders, R.string.handle_events_with_no_reminders_summary) {
                    initial(settings.handleEventsWithNoReminders)
                    onChange{settings.handleEventsWithNoReminders = it}

                    depending {

                        item(R.string.default_reminder_time, R.string.default_reminder_time_summary_short) {
                            DefaultManualNotificationPreference(
                                    ctx,
                                    layoutInflater,
                                    settings.defaultReminderTimeMinutes,
                                    { settings.defaultReminderTimeMinutes = it }
                            ).create().show()
                        }

                        item(R.string.default_all_day_reminder_time, R.string.default_all_day_reminder_time_summary_short) {
                            DefaultManualAllDayNotificationPreference(
                                    ctx,
                                    layoutInflater,
                                    settings.defaultAllDayReminderTimeMinutes,
                                    { settings.defaultAllDayReminderTimeMinutes = it }
                            ).create().show()
                        }
                    }
                }



            }

            return root
        }
        return null
    }
}