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

package com.github.quarck.calnotify.app

import com.github.quarck.calnotify.calendar.EventAlertRecord


class TagsManager {

    private fun hasTag(text: String, tag: String): Boolean {

        var ret = false

        val pos = text.indexOf(tag, ignoreCase = true)
        if (pos != -1) {
            val nextCharAfterTag = pos + tag.length;
            ret =
                if (nextCharAfterTag < text.length)
                    !text[nextCharAfterTag].isLetterOrDigit()
                else
                    true

        }

        return ret
    }

    private fun EventAlertRecord.hasTag(tag: String) =
            hasTag(this.title, tag) || hasTag(this.desc, tag)

    fun parseEventTags(event: EventAlertRecord) {
        event.isAlarm = event.hasTag(ALARM_TAG)
    }

    companion object {
        const val ALARM_TAG = "#alarm"
    }
}