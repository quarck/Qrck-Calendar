package com.android.calendar.agenda;

import android.content.Context;
import android.provider.CalendarContract;

import com.github.quarck.calnotify.Settings;

public class AgendaTasksFragment extends AgendaFragment
{
    public AgendaTasksFragment() {
        super();
    }

    public AgendaTasksFragment(long timeMillis, boolean usedForSearch) {
        super(timeMillis, usedForSearch);
    }

    @Override
    public String getEventsQueryFilterString(Context ctx) {

        long[] taskCalendarIds = new Settings(getActivity()).getTaskCalendarIds();
        if (taskCalendarIds.length == 0)
            return "";

        StringBuilder sb = new StringBuilder("(");

        for (int idx = 0; idx < taskCalendarIds.length; ++ idx) {
            long calendarId = taskCalendarIds[idx];
            if (idx > 0)
                sb.append(" OR ");
            sb.append(CalendarContract.Instances.CALENDAR_ID + "==");
            sb.append(calendarId);
        }

        sb.append(")");

        return sb.toString();
    }
}