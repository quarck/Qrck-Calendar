/*
 * Copyright (C) 2020 Dominik Schürmann <dominik@schuermann.eu>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.android.calendar.settings

import android.annotation.TargetApi
import android.app.backup.BackupManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.provider.CalendarContract
import android.provider.CalendarContract.CalendarCache
import android.provider.SearchRecentSuggestions
import android.provider.Settings
import android.text.TextUtils
import android.util.SparseIntArray
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.preference.*
import com.android.calendar.*
import com.android.calendar.event.EventViewUtils
import com.android.timezonepicker.TimeZoneInfo
import com.android.timezonepicker.TimeZonePickerUtils
import org.qrck.seshat.R
import java.util.*

class GeneralPreferences : PreferenceFragmentCompat(),
        OnSharedPreferenceChangeListener, Preference.OnPreferenceChangeListener,
        TimeZonePickerDialogX.OnTimeZoneSetListener {

    private lateinit var themePref: ListPreference
    private lateinit var colorPref: Preference
    private lateinit var pureBlackNightModePref: CheckBoxPreference
    private lateinit var hideDeclinedPref: CheckBoxPreference
    private lateinit var weekStartPref: ListPreference
    private lateinit var dayWeekPref: ListPreference
    private lateinit var defaultEventDurationPref: ListPreference
    private lateinit var useHomeTzPref: CheckBoxPreference
    private lateinit var homeTzPref: Preference
    private lateinit var defaultReminderPref: ListPreference
    private lateinit var defaultAllDayReminderPref: ListPreference
    private lateinit var handleEmailOnlyEventsPref: CheckBoxPreference
    private lateinit var handleEventsWithNoRemindersPref: CheckBoxPreference

    // >= 26
    private lateinit var notificationPref: Preference
    private lateinit var alarmNotificationPref: Preference

    // < 26
    private lateinit var tzPickerUtils: TimeZonePickerUtils
    private var timeZoneId: String? = null

    // Used to retrieve the color id from the color picker
    private val colorMap = SparseIntArray()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = SHARED_PREFS_NAME
        setPreferencesFromResource(R.xml.general_preferences, rootKey)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activity?.title = getString(R.string.preferences_list_general)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        themePref = preferenceScreen.findPreference(KEY_THEME_PREF)!!
        colorPref = preferenceScreen.findPreference(KEY_COLOR_PREF)!!
        pureBlackNightModePref = preferenceScreen.findPreference(KEY_PURE_BLACK_NIGHT_MODE)!!
        hideDeclinedPref = preferenceScreen.findPreference(KEY_HIDE_DECLINED)!!
        weekStartPref = preferenceScreen.findPreference(KEY_WEEK_START_DAY)!!
        dayWeekPref = preferenceScreen.findPreference(KEY_DAYS_PER_WEEK)!!
        defaultEventDurationPref = preferenceScreen.findPreference(KEY_DEFAULT_EVENT_DURATION)!!
        useHomeTzPref = preferenceScreen.findPreference(KEY_HOME_TZ_ENABLED)!!
        homeTzPref = preferenceScreen.findPreference(KEY_HOME_TZ)!!
        defaultReminderPref = preferenceScreen.findPreference(KEY_DEFAULT_REMINDER)!!
        defaultAllDayReminderPref = preferenceScreen.findPreference(KEY_DEFAULT_ALL_DAY_REMINDER)!!
        handleEmailOnlyEventsPref = preferenceScreen.findPreference(KEY_HANDLE_EMAIL_ONLY)!!
        handleEventsWithNoRemindersPref = preferenceScreen.findPreference(KEY_HANDLE_EVENTS_WITH_NO_REMINDERS)!!

        val prefs = CalendarUtils.getSharedPreferences(activity!!,
                Utils.SHARED_PREFS_NAME)

        notificationPref = preferenceScreen.findPreference(KEY_NOTIFICATION)!!
        alarmNotificationPref = preferenceScreen.findPreference(KEY_NOTIFICATION_ALARM)!!

        defaultEventDurationPref.summary = defaultEventDurationPref.entry
        themePref.summary = themePref.entry
        weekStartPref.summary = weekStartPref.entry
        dayWeekPref.summary = dayWeekPref.entry
        defaultReminderPref.summary = defaultReminderPref.entry
        defaultAllDayReminderPref.summary = defaultAllDayReminderPref.entry

        // This triggers an asynchronous call to the provider to refresh the data in shared pref
        timeZoneId = Utils.getTimeZone(activity, null)

        // Utils.getTimeZone will return the currentTimeZone instead of the one
        // in the shared_pref if home time zone is disabled. So if home tz is
        // off, we will explicitly read it.
        if (!prefs.getBoolean(KEY_HOME_TZ_ENABLED, false)) {
            timeZoneId = prefs.getString(KEY_HOME_TZ, TimeZone.getDefault().id)
        }

        tzPickerUtils = TimeZonePickerUtils(activity)

        val timezoneName = tzPickerUtils.getGmtDisplayName(activity, timeZoneId,
                System.currentTimeMillis(), false)
        homeTzPref.summary = timezoneName ?: timeZoneId

        val tzpd = activity!!.supportFragmentManager
                .findFragmentByTag(FRAG_TAG_TIME_ZONE_PICKER) as TimeZonePickerDialogX?
        tzpd?.setOnTimeZoneSetListener(this)

        initializeColorMap()
    }

    private fun showColorPickerDialog() {
        val colorPickerDialog = ColorPickerDialogX()
        val selectedColorName = Utils.getSharedPreference(activity, KEY_COLOR_PREF, "teal")
        val selectedColor = ContextCompat.getColor(context!!, DynamicTheme.getColorId(selectedColorName))
        colorPickerDialog.initialize(R.string.preferences_color_pick,
                intArrayOf(ContextCompat.getColor(context!!, R.color.colorPrimary),
                        ContextCompat.getColor(context!!, R.color.colorBluePrimary),
                        ContextCompat.getColor(context!!, R.color.colorPurplePrimary),
                        ContextCompat.getColor(context!!, R.color.colorRedPrimary),
                        ContextCompat.getColor(context!!, R.color.colorOrangePrimary),
                        ContextCompat.getColor(context!!, R.color.colorGreenPrimary)),
                selectedColor, 3, 2)
        colorPickerDialog.setOnColorSelectedListener { colour ->
            Utils.setSharedPreference(activity, KEY_COLOR_PREF, DynamicTheme.getColorName(colorMap.get(colour)))
        }
        colorPickerDialog.show(parentFragmentManager, "colorpicker")
    }

    private fun initializeColorMap() {
        colorMap.put(ContextCompat.getColor(context!!, R.color.colorPrimary), R.color.colorPrimary)
        colorMap.put(ContextCompat.getColor(context!!, R.color.colorBluePrimary), R.color.colorBluePrimary)
        colorMap.put(ContextCompat.getColor(context!!, R.color.colorOrangePrimary), R.color.colorOrangePrimary)
        colorMap.put(ContextCompat.getColor(context!!, R.color.colorGreenPrimary), R.color.colorGreenPrimary)
        colorMap.put(ContextCompat.getColor(context!!, R.color.colorRedPrimary), R.color.colorRedPrimary)
        colorMap.put(ContextCompat.getColor(context!!, R.color.colorPurplePrimary), R.color.colorPurplePrimary)
    }

    private fun showTimezoneDialog() {
        val arguments = Bundle().apply {
            putLong(TimeZonePickerDialogX.BUNDLE_START_TIME_MILLIS, System.currentTimeMillis())
            putString(TimeZonePickerDialogX.BUNDLE_TIME_ZONE, Utils.getTimeZone(activity, null))
        }

        val fm = activity!!.supportFragmentManager
        var tzpd: TimeZonePickerDialogX? = fm.findFragmentByTag(FRAG_TAG_TIME_ZONE_PICKER) as TimeZonePickerDialogX?
        tzpd?.dismiss()

        tzpd = TimeZonePickerDialogX()
        tzpd.arguments = arguments
        tzpd.setOnTimeZoneSetListener(this)
        tzpd.show(fm, FRAG_TAG_TIME_ZONE_PICKER)
    }

    override fun onStart() {
        super.onStart()
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        setPreferenceListeners(this)
    }

    /**
     * Sets up all the preference change listeners to use the specified listener.
     */
    private fun setPreferenceListeners(listener: Preference.OnPreferenceChangeListener) {
        themePref.onPreferenceChangeListener = listener
        colorPref.onPreferenceChangeListener = listener
        pureBlackNightModePref.onPreferenceChangeListener = listener
        hideDeclinedPref.onPreferenceChangeListener = listener
        weekStartPref.onPreferenceChangeListener = listener
        dayWeekPref.onPreferenceChangeListener = listener
        defaultEventDurationPref.onPreferenceChangeListener = listener
        useHomeTzPref.onPreferenceChangeListener = listener
        homeTzPref.onPreferenceChangeListener = listener
        defaultReminderPref.onPreferenceChangeListener = listener
        defaultAllDayReminderPref.onPreferenceChangeListener = listener
        handleEmailOnlyEventsPref.onPreferenceChangeListener = listener
        handleEventsWithNoRemindersPref.onPreferenceChangeListener = listener
    }

    override fun onStop() {
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        val a = activity ?: return

        BackupManager.dataChanged(a.packageName)

        when (key) {
            KEY_THEME_PREF -> {
                Utils.sendUpdateWidgetIntent(a)
                a.recreate()
            }
            KEY_COLOR_PREF -> {
                Utils.sendUpdateWidgetIntent(a)
                a.recreate()
            }
        }
        //pureBlackNightMode refresh condition
        if (themePref.value == "system" && DynamicTheme.isSystemInDarkTheme(a)) {
            Utils.sendUpdateWidgetIntent(a)
            a.recreate()
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        when (preference) {
            useHomeTzPref -> {
                val useHomeTz = newValue as Boolean
                val tz: String? = if (useHomeTz) {
                    timeZoneId
                } else {
                    CalendarCache.TIMEZONE_TYPE_AUTO
                }
                Utils.setTimeZone(activity, tz)
                return true
            }
            themePref -> {
                themePref.value = newValue as String
                themePref.summary = themePref.entry
            }
            hideDeclinedPref -> {
                hideDeclinedPref.isChecked = newValue as Boolean
                val intent = Intent(Utils.getWidgetScheduledUpdateAction(activity))
                intent.setDataAndType(CalendarContract.CONTENT_URI, Utils.APPWIDGET_DATA_TYPE)
                activity!!.sendBroadcast(intent)
                return true
            }
            weekStartPref -> {
                weekStartPref.value = newValue as String
                weekStartPref.summary = weekStartPref.entry
            }
            dayWeekPref -> {
                dayWeekPref.value = newValue as String
                dayWeekPref.summary = dayWeekPref.entry
            }
            defaultEventDurationPref -> {
                defaultEventDurationPref.value = newValue as String
                defaultEventDurationPref.summary = defaultEventDurationPref.entry
            }
            defaultReminderPref -> {
                defaultReminderPref.value = newValue as String
                defaultReminderPref.summary = defaultReminderPref.entry
            }
            defaultAllDayReminderPref -> {
                defaultAllDayReminderPref.value = newValue as String
                defaultAllDayReminderPref.summary = defaultAllDayReminderPref.entry
            }
            else -> {
                return true
            }
        }
        return false
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        when (preference!!.key) {
            KEY_COLOR_PREF -> {
                showColorPickerDialog()
                return true
            }
            KEY_HOME_TZ -> {
                showTimezoneDialog()
                return true
            }
            KEY_CLEAR_SEARCH_HISTORY -> {
                clearSearchHistory()
                return true
            }
            KEY_NOTIFICATION -> {
                showNotificationChannel()
                return true
            }
            KEY_NOTIFICATION_ALARM -> {
                showAlarmNotificationChannel()
                return true
            }
            else -> return super.onPreferenceTreeClick(preference)
        }
    }

    private fun showDbCopy() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.component = ComponentName("com.android.providers.calendar",
                "com.android.providers.calendar.CalendarDebugActivity")
        startActivity(intent)
    }

    private fun clearSearchHistory() {
        val suggestions = SearchRecentSuggestions(activity,
                Utils.getSearchAuthority(activity),
                CalendarRecentSuggestionsProvider.MODE)
        suggestions.clearHistory()
        Toast.makeText(activity, R.string.search_history_cleared, Toast.LENGTH_SHORT).show()
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun showNotificationChannel() {
        com.github.quarck.calnotify.notification.NotificationChannelManager.launchChannelSettings(activity!!, false)
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun showAlarmNotificationChannel() {
        com.github.quarck.calnotify.notification.NotificationChannelManager.launchChannelSettings(activity!!, true)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onTimeZoneSet(tzi: TimeZoneInfo) {
        val timeZoneDisplayName = tzPickerUtils.getGmtDisplayName(
                activity, tzi.mTzId, System.currentTimeMillis(), false)
        homeTzPref.summary = timeZoneDisplayName
        Utils.setTimeZone(activity, tzi.mTzId)
    }

    companion object {
        // Preference keys
        const val KEY_THEME_PREF = "pref_theme"
        const val KEY_COLOR_PREF = "pref_color"
        const val KEY_PURE_BLACK_NIGHT_MODE = "pref_pure_black_night_mode"
        const val KEY_HIDE_DECLINED = "preferences_hide_declined"
        const val KEY_WEEK_START_DAY = "preferences_week_start_day"
        const val KEY_SHOW_WEEK_NUM = "preferences_show_week_num"
        const val KEY_DAYS_PER_WEEK = "preferences_days_per_week"
        const val KEY_MDAYS_PER_WEEK = "preferences_mdays_per_week"
        const val KEY_CLEAR_SEARCH_HISTORY = "preferences_clear_search_history"
        const val KEY_ALERTS_CATEGORY = "preferences_alerts_category"
        const val KEY_NOTIFICATION = "preferences_notification"
        const val KEY_NOTIFICATION_ALARM = "preferences_notification_alarm"
        const val KEY_DEFAULT_REMINDER = "preferences_default_reminder"
        const val KEY_DEFAULT_ALL_DAY_REMINDER = "preferences_default_all_day_reminder"
        const val KEY_HANDLE_EMAIL_ONLY = "preferences_handle_email_only_events"
        const val KEY_HANDLE_EVENTS_WITH_NO_REMINDERS = "preferences_handle_events_with_no_reminders"
        const val DEFAULT_USE_ONGOING = true
        const val DEFAULT_REMINDER_STRING = "15"
        const val DEFAULT_ALL_DAY_REMINDER_STRING = "960"
        const val REMINDER_DEFAULT_TIME = 15 // in minutes
        const val ALL_DAY_REMINDER_DEFAULT_TIME = 960 // in minutes
        const val KEY_DEFAULT_CELL_HEIGHT = "preferences_default_cell_height"
        const val KEY_VERSION = "preferences_version"
        /** Key to SharePreference for default view (CalendarController.ViewType)  */
        const val KEY_START_VIEW = "preferred_startView"
        /**
         * Key to SharePreference for default detail view (CalendarController.ViewType)
         * Typically used by widget
         */
        const val KEY_DETAILED_VIEW = "preferred_detailedView"
        const val KEY_DEFAULT_CALENDAR = "preference_defaultCalendar"

        /** Key to preference for default new event duration (if provider doesn't indicate one)  */
        const val KEY_DEFAULT_EVENT_DURATION = "preferences_default_event_duration"
        const val EVENT_DURATION_DEFAULT = "60"

        // These must be in sync with the array preferences_week_start_day_values
        const val WEEK_START_DEFAULT = "-1"
        const val WEEK_START_SATURDAY = "7"
        const val WEEK_START_SUNDAY = "1"
        const val WEEK_START_MONDAY = "2"
        // Default preference values
        const val DEFAULT_DEFAULT_START = "-2"
        const val DEFAULT_START_VIEW = CalendarController.ViewType.WEEK
        const val DEFAULT_DETAILED_VIEW = CalendarController.ViewType.DAY
        const val DEFAULT_SHOW_WEEK_NUM = false
        // This should match the XML file.
        const val DEFAULT_RINGTONE = "content://settings/system/notification_sound"
        // The name of the shared preferences file. This name must be maintained for historical
        // reasons, as it's what PreferenceManager assigned the first time the file was created.
        const val SHARED_PREFS_NAME = "com.android.calendar_preferences"
        const val SHARED_PREFS_NAME_NO_BACKUP = "com.android.calendar_preferences_no_backup"
        private const val KEY_HOME_TZ_ENABLED = "preferences_home_tz_enabled"
        private const val KEY_HOME_TZ = "preferences_home_tz"
        private const val FRAG_TAG_TIME_ZONE_PICKER = "TimeZonePicker"

        internal const val REQUEST_CODE_ALERT_RINGTONE = 42

        /** Return a properly configured SharedPreferences instance  */
        fun getSharedPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        }

        /** Set the default shared preferences in the proper context */
        fun setDefaultValues(context: Context) {
            PreferenceManager.setDefaultValues(context, SHARED_PREFS_NAME, Context.MODE_PRIVATE,
                    R.xml.general_preferences, true)
        }
    }
}
