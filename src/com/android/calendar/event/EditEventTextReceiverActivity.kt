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

package com.android.calendar.event

import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import androidx.appcompat.app.AppCompatActivity
import com.android.calendar.Utils
import com.android.calendar.event.EditEventActivity
import com.github.quarck.calnotify.Consts
import org.qrck.seshat.R

class EditEventTextReceiverActivity(): AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.empty_layout)

        val intent = intent
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                launchNewEventEditor(intent.getStringExtra(Intent.EXTRA_TEXT) ?: "")
            }
        }
        else if (Intent.ACTION_PROCESS_TEXT.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                launchNewEventEditor(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                        ?: "")
            }
        }

        finish()
    }

    private fun launchNewEventEditor(title: String) {

        var startMillis = System.currentTimeMillis()
        startMillis -= (startMillis % (3600 * 1000L))  // Drop minutes, seconds and millis
        startMillis += Consts.NEW_EVENT_DEFAULT_ADD_HOURS * Consts.HOUR_IN_MILLISECONDS
        val endMillis = startMillis + Utils.getDefaultEventDurationInMillis(this)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setClass(this, com.android.calendar.event.EditEventActivity::class.java)
        intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
        intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
        intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
        intent.putExtra(Events.CALENDAR_ID, -1)
        intent.putExtra(Events.TITLE, title)

        startActivity(intent)
    }
}