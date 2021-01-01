/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calendar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Colors;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.QuickContact;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.FileProvider;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.Time;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.util.Rfc822Token;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarEventModel.Attendee;
import com.android.calendar.CalendarEventModel.ReminderEntry;
import com.android.calendar.alerts.QuickResponseActivity;
import com.android.calendar.event.AttendeesView;
import com.android.calendar.event.EditEventActivity;
import com.android.calendar.event.EditEventHelper;
import com.android.calendar.event.EventColorPickerDialog;
import com.android.calendar.event.EventViewUtils;
import com.android.calendar.icalendar.IcalendarUtils;
import com.android.calendar.icalendar.Organizer;
import com.android.calendar.icalendar.VCalendar;
import com.android.calendar.icalendar.VEvent;
import com.android.calendarcommon2.DateException;
import com.android.calendarcommon2.Duration;
import com.android.calendarcommon2.EventRecurrence;
import com.android.colorpicker.HsvColorComparator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.qrck.seshat.BuildConfig;
import org.qrck.seshat.R;

import static android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY;
import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;
import static android.provider.CalendarContract.EXTRA_EVENT_END_TIME;
import static com.android.calendar.CalendarController.EVENT_EDIT_ON_LAUNCH;

public class EventInfoUtils
{
    public static final boolean DEBUG = false;

    public static final String TAG = "EventInfoFragment";
    public static final String COLOR_PICKER_DIALOG_TAG = "EventColorPickerDialog";
    // Style of view
    public static final int FULL_WINDOW_STYLE = 0;
    public static final int DIALOG_WINDOW_STYLE = 1;
    public static final int COLORS_INDEX_COLOR = 1;
    public static final int COLORS_INDEX_COLOR_KEY = 2;
    protected static final String BUNDLE_KEY_EVENT_ID = "key_event_id";
    protected static final String BUNDLE_KEY_START_MILLIS = "key_start_millis";
    protected static final String BUNDLE_KEY_END_MILLIS = "key_end_millis";
    protected static final String BUNDLE_KEY_IS_DIALOG = "key_fragment_is_dialog";
    protected static final String BUNDLE_KEY_DELETE_DIALOG_VISIBLE = "key_delete_dialog_visible";
    protected static final String BUNDLE_KEY_WINDOW_STYLE = "key_window_style";
    protected static final String BUNDLE_KEY_CALENDAR_COLOR = "key_calendar_color";
    protected static final String BUNDLE_KEY_CALENDAR_COLOR_INIT = "key_calendar_color_init";
    protected static final String BUNDLE_KEY_CURRENT_COLOR = "key_current_color";
    protected static final String BUNDLE_KEY_CURRENT_COLOR_KEY = "key_current_color_key";
    protected static final String BUNDLE_KEY_CURRENT_COLOR_INIT = "key_current_color_init";
    protected static final String BUNDLE_KEY_ORIGINAL_COLOR = "key_original_color";
    protected static final String BUNDLE_KEY_ORIGINAL_COLOR_INIT = "key_original_color_init";
    protected static final String BUNDLE_KEY_ATTENDEE_RESPONSE = "key_attendee_response";
    protected static final String BUNDLE_KEY_USER_SET_ATTENDEE_RESPONSE =
            "key_user_set_attendee_response";
    protected static final String BUNDLE_KEY_TENTATIVE_USER_RESPONSE =
            "key_tentative_user_response";
    protected static final String BUNDLE_KEY_RESPONSE_WHICH_EVENTS = "key_response_which_events";
    protected static final String BUNDLE_KEY_REMINDER_MINUTES = "key_reminder_minutes";
    protected static final String BUNDLE_KEY_REMINDER_METHODS = "key_reminder_methods";

    /**
     * These are the corresponding indices into the array of strings
     * "R.array.change_response_labels" in the resource file.
     */
    static final int UPDATE_SINGLE = 0;
    static final int UPDATE_ALL = 1;
    static final String[] CALENDARS_PROJECTION = new String[]{
            Calendars._ID,           // 0
            Calendars.CALENDAR_DISPLAY_NAME,  // 1
            Calendars.OWNER_ACCOUNT, // 2
            Calendars.CAN_ORGANIZER_RESPOND, // 3
            Calendars.ACCOUNT_NAME, // 4
            Calendars.ACCOUNT_TYPE  // 5
    };
    static final int CALENDARS_INDEX_DISPLAY_NAME = 1;
    static final int CALENDARS_INDEX_OWNER_ACCOUNT = 2;
    static final int CALENDARS_INDEX_OWNER_CAN_RESPOND = 3;
    static final int CALENDARS_INDEX_ACCOUNT_NAME = 4;
    static final int CALENDARS_INDEX_ACCOUNT_TYPE = 5;
    static final String CALENDARS_WHERE = Calendars._ID + "=?";
    static final String CALENDARS_DUPLICATE_NAME_WHERE = Calendars.CALENDAR_DISPLAY_NAME + "=?";
    static final String CALENDARS_VISIBLE_WHERE = Calendars.VISIBLE + "=?";
    static final String[] COLORS_PROJECTION = new String[]{
            Colors._ID, // 0
            Colors.COLOR, // 1
            Colors.COLOR_KEY // 2
    };
    static final String COLORS_WHERE = Colors.ACCOUNT_NAME + "=? AND " + Colors.ACCOUNT_TYPE +
            "=? AND " + Colors.COLOR_TYPE + "=" + Colors.TYPE_EVENT;
    private static final int REQUEST_CODE_COLOR_PICKER = 0;
    private static final String PERIOD_SPACE = ". ";
    private static final String NO_EVENT_COLOR = "";
    // Query tokens for QueryHandler
    private static final int TOKEN_QUERY_EVENT = 1;
    private static final int TOKEN_QUERY_CALENDARS = 1 << 1;
    private static final int TOKEN_QUERY_ATTENDEES = 1 << 2;
    private static final int TOKEN_QUERY_DUPLICATE_CALENDARS = 1 << 3;
    private static final int TOKEN_QUERY_REMINDERS = 1 << 4;
    private static final int TOKEN_QUERY_VISIBLE_CALENDARS = 1 << 5;
    private static final int TOKEN_QUERY_COLORS = 1 << 6;
    private static final int TOKEN_QUERY_ALL = TOKEN_QUERY_DUPLICATE_CALENDARS
            | TOKEN_QUERY_ATTENDEES | TOKEN_QUERY_CALENDARS | TOKEN_QUERY_EVENT
            | TOKEN_QUERY_REMINDERS | TOKEN_QUERY_VISIBLE_CALENDARS | TOKEN_QUERY_COLORS;

    public static final File EXPORT_SDCARD_DIRECTORY = new File(
            Environment.getExternalStorageDirectory(), "CalendarEvents");

    private enum ShareType {
        SDCARD,
        INTENT
    }

    private static final String[] EVENT_PROJECTION = new String[] {
        Events._ID,                  // 0  do not remove; used in DeleteEventHelper
        Events.TITLE,                // 1  do not remove; used in DeleteEventHelper
        Events.RRULE,                // 2  do not remove; used in DeleteEventHelper
        Events.ALL_DAY,              // 3  do not remove; used in DeleteEventHelper
        Events.CALENDAR_ID,          // 4  do not remove; used in DeleteEventHelper
        Events.DTSTART,              // 5  do not remove; used in DeleteEventHelper
        Events._SYNC_ID,             // 6  do not remove; used in DeleteEventHelper
        Events.EVENT_TIMEZONE,       // 7  do not remove; used in DeleteEventHelper
        Events.DESCRIPTION,          // 8
        Events.EVENT_LOCATION,       // 9
        Calendars.CALENDAR_ACCESS_LEVEL, // 10
        Events.CALENDAR_COLOR,       // 11
        Events.EVENT_COLOR,          // 12
        Events.STATUS,               // 13
        Events.HAS_ATTENDEE_DATA,    // 14
        Events.ORGANIZER,            // 15
        Events.HAS_ALARM,            // 16
        Calendars.MAX_REMINDERS,     // 17
        Calendars.ALLOWED_REMINDERS, // 18
        Events.CUSTOM_APP_PACKAGE,   // 19
        Events.CUSTOM_APP_URI,       // 20
        Events.DTEND,                // 21
        Events.DURATION,             // 22
        Events.ORIGINAL_SYNC_ID      // 23 do not remove; used in DeleteEventHelper
    };
    private static final int EVENT_INDEX_ID = 0;
    private static final int EVENT_INDEX_TITLE = 1;
    private static final int EVENT_INDEX_RRULE = 2;
    private static final int EVENT_INDEX_ALL_DAY = 3;
    private static final int EVENT_INDEX_CALENDAR_ID = 4;
    private static final int EVENT_INDEX_DTSTART = 5;
    private static final int EVENT_INDEX_SYNC_ID = 6;
    private static final int EVENT_INDEX_EVENT_TIMEZONE = 7;
    private static final int EVENT_INDEX_DESCRIPTION = 8;
    private static final int EVENT_INDEX_EVENT_LOCATION = 9;
    private static final int EVENT_INDEX_ACCESS_LEVEL = 10;
    private static final int EVENT_INDEX_CALENDAR_COLOR = 11;
    private static final int EVENT_INDEX_EVENT_COLOR = 12;
    private static final int EVENT_INDEX_STATUS = 13;
    private static final int EVENT_INDEX_HAS_ATTENDEE_DATA = 14;
    private static final int EVENT_INDEX_ORGANIZER = 15;
    private static final int EVENT_INDEX_HAS_ALARM = 16;
    private static final int EVENT_INDEX_MAX_REMINDERS = 17;
    private static final int EVENT_INDEX_ALLOWED_REMINDERS = 18;
    private static final int EVENT_INDEX_CUSTOM_APP_PACKAGE = 19;
    private static final int EVENT_INDEX_CUSTOM_APP_URI = 20;
    private static final int EVENT_INDEX_DTEND = 21;
    private static final int EVENT_INDEX_DURATION = 22;
    private static final String[] ATTENDEES_PROJECTION = new String[] {
        Attendees._ID,                      // 0
        Attendees.ATTENDEE_NAME,            // 1
        Attendees.ATTENDEE_EMAIL,           // 2
        Attendees.ATTENDEE_RELATIONSHIP,    // 3
        Attendees.ATTENDEE_STATUS,          // 4
        Attendees.ATTENDEE_IDENTITY,        // 5
        Attendees.ATTENDEE_ID_NAMESPACE     // 6
    };
    private static final int ATTENDEES_INDEX_ID = 0;
    private static final int ATTENDEES_INDEX_NAME = 1;
    private static final int ATTENDEES_INDEX_EMAIL = 2;
    private static final int ATTENDEES_INDEX_RELATIONSHIP = 3;
    private static final int ATTENDEES_INDEX_STATUS = 4;
    private static final int ATTENDEES_INDEX_IDENTITY = 5;
    private static final int ATTENDEES_INDEX_ID_NAMESPACE = 6;
    private static final String ATTENDEES_WHERE = Attendees.EVENT_ID + "=?";
    private static final String ATTENDEES_SORT_ORDER = Attendees.ATTENDEE_NAME + " ASC, "
            + Attendees.ATTENDEE_EMAIL + " ASC";
    private static final String[] REMINDERS_PROJECTION = new String[] {
        Reminders._ID,                      // 0
        Reminders.MINUTES,            // 1
        Reminders.METHOD           // 2
    };

    private static final String REMINDERS_WHERE = Reminders.EVENT_ID + "=?";
    private static final int FADE_IN_TIME = 300;   // in milliseconds
    private static final int LOADING_MSG_DELAY = 600;   // in milliseconds
    private static final int LOADING_MSG_MIN_DISPLAY_TIME = 600;
    private static float mScale = 0; // Used for supporting different screen densities
    private static int mCustomAppIconSize = 32;
    private static int mDialogWidth = 500;
    private static int mDialogHeight = 600;
    private static int DIALOG_TOP_MARGIN = 8;


    private final ArrayList<ConstraintLayout> mReminderViews = new ArrayList<ConstraintLayout>(0);

    public static int getResponseFromButtonId(int buttonId) {
        int response;
        if (buttonId == R.id.response_yes) {
            response = Attendees.ATTENDEE_STATUS_ACCEPTED;
        } else if (buttonId == R.id.response_maybe) {
            response = Attendees.ATTENDEE_STATUS_TENTATIVE;
        } else if (buttonId == R.id.response_no) {
            response = Attendees.ATTENDEE_STATUS_DECLINED;
        } else {
            response = Attendees.ATTENDEE_STATUS_NONE;
        }
        return response;
    }

    public static int findButtonIdForResponse(int response) {
        int buttonId;
        switch (response) {
            case Attendees.ATTENDEE_STATUS_ACCEPTED:
                buttonId = R.id.response_yes;
                break;
            case Attendees.ATTENDEE_STATUS_TENTATIVE:
                buttonId = R.id.response_maybe;
                break;
            case Attendees.ATTENDEE_STATUS_DECLINED:
                buttonId = R.id.response_no;
                break;
            default:
                buttonId = -1;
        }
        return buttonId;
    }

    /**
     * Loads an integer array asset into a list.
     */
    private static ArrayList<Integer> loadIntegerArray(Resources r, int resNum) {
        int[] vals = r.getIntArray(resNum);
        int size = vals.length;
        ArrayList<Integer> list = new ArrayList<Integer>(size);

        for (int i = 0; i < size; i++) {
            list.add(vals[i]);
        }

        return list;
    }

    /**
     * Loads a String array asset into a list.
     */
    private static ArrayList<String> loadStringArray(Resources r, int resNum) {
        String[] labels = r.getStringArray(resNum);
        ArrayList<String> list = new ArrayList<String>(Arrays.asList(labels));
        return list;
    }
}
