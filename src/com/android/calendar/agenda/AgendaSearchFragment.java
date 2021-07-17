package com.android.calendar.agenda;

import android.content.Context;

public class AgendaSearchFragment extends AgendaFragment
{

    public AgendaSearchFragment() {
        super();
    }

    public AgendaSearchFragment(long timeMillis, boolean usedForSearch) {
        super(timeMillis, usedForSearch);
    }


    @Override
    public String getEventsQueryFilterString(Context ctx) {
        return "";
    }
}