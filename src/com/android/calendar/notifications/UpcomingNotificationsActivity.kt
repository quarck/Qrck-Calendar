package com.android.calendar.notifications

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntryKey
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitor
import com.github.quarck.calnotify.ui.ViewEventActivity
import com.github.quarck.calnotify.utils.adjustCalendarColor
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.textutils.EventFormatter
import kotlinx.coroutines.*
import org.qrck.seshat.R

data class UpcomingEventAlertRecordWrap(
        val isToday: Boolean,
        val event: EventAlertRecord?,
        val numItemsInGroup: Int? = null
)

class UpcomingEventListAdapter(
        val context: Context,
        val cb: UpcomingNotificationsActivity
) : RecyclerView.Adapter<UpcomingEventListAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View)
        : RecyclerView.ViewHolder(itemView) {
        //var eventId: Long = 0;
        var entry: UpcomingEventAlertRecordWrap? = null

        var eventHolder: RelativeLayout = itemView.findViewById<RelativeLayout>(R.id.card_view_main_holder)
        var eventTitleText = itemView.findViewById<TextView>(R.id.card_view_event_name)

        var eventDateText = itemView.findViewById<TextView>(R.id.card_view_event_date)
        var eventTimeText: TextView = itemView.findViewById<TextView>(R.id.card_view_event_time)

        var snoozedUntilText: TextView? = itemView.findViewById<TextView>(R.id.card_view_snoozed_until)
        val compactViewCalendarColor: View? = itemView.findViewById<View>(R.id.compact_view_calendar_color)

        val headingLayout: RelativeLayout = itemView.findViewById<RelativeLayout>(R.id.event_card_heading_layout)
        val headingText: TextView = itemView.findViewById<TextView>(R.id.event_view_heading_text)

        val undoLayout: RelativeLayout = itemView.findViewById(R.id.event_card_undo_layout)
        val mainLayout: RelativeLayout = itemView.findViewById(R.id.compact_view_content_layout)

        var calendarColor: ColorDrawable = ColorDrawable(0)

        init {
            eventHolder.setOnClickListener{
                if (entry != null)
                    cb.onItemClick(eventTitleText, adapterPosition, entry!!);
            }

            undoLayout.visibility = View.GONE
            mainLayout.visibility = View.VISIBLE
        }
    }

    private var entries = listOf<UpcomingEventAlertRecordWrap>();

    private var _recyclerView: RecyclerView? = null
    var recyclerView: RecyclerView?
        get() = _recyclerView
        set(value) {
            _recyclerView = value
        }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        //
        if (position < 0 || position >= entries.size)
            return

        val entry = entries[position]

        holder.entry = entry

        if (entry.event != null) {
            holder.mainLayout.visibility = View.VISIBLE
            holder.headingLayout.visibility = View.GONE

            holder.eventTitleText.text = cb.getItemTitle(entry)

            val time = cb.getItemMiddleLine(entry)
            holder.eventDateText.text = time
            holder.eventTimeText.text = ""

            val (bottomText, bottomColor) = cb.getItemBottomLine(entry)
            holder.snoozedUntilText?.text = bottomText
            holder.snoozedUntilText?.setTextColor(bottomColor)
            holder.snoozedUntilText?.visibility = View.VISIBLE;

            holder.calendarColor.color = cb.getItemColor(entry)
            holder.compactViewCalendarColor?.background = holder.calendarColor
        }
        else {
            holder.mainLayout.visibility = View.GONE
            holder.headingLayout.visibility = View.VISIBLE

            holder.headingText.text = cb.getItemTitle(entry) // entry.event.title
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.event_card_compact, parent, false);
        return ViewHolder(view);
    }

    override fun getItemCount(): Int = entries.size

    fun setEventsToDisplay(newEntries: List<UpcomingEventAlertRecordWrap>)
            = synchronized(this) {
        entries = newEntries
        notifyDataSetChanged()
    }
}


class UpcomingNotificationsActivity : AppCompatActivity() {
    private val scope = MainScope()

    private lateinit var recyclerView: RecyclerView

    private lateinit var adapter: UpcomingEventListAdapter

    private var primaryColor: Int = Consts.DEFAULT_CALENDAR_EVENT_COLOR
    private lateinit var eventFormatter: EventFormatter

    private lateinit var statusHandled: String
    private lateinit var eventReminderTimeFmt: String

    private lateinit var todayHeading: String
    private lateinit var todayHeadingEmpty: String
    private lateinit var otherDayHeading: String
    private lateinit var otherDayheadingEmpty: String

    private var colorSkippedItemBotomLine: Int  = 0x7f3f3f3f
    private var colorNonSkippedItemBottomLine: Int = 0x7f7f7f7f

    private var monitorEntries = mapOf<MonitorEventAlertEntryKey, MonitorEventAlertEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        DevLog.info(LOG_TAG, "onCreate")

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_upcoming)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        setSupportActionBar(findViewById<Toolbar?>(R.id.toolbar))
        supportActionBar?.let{
            it.setDisplayHomeAsUpEnabled(true)
            it.setHomeAsUpIndicator(R.drawable.ic_arrow_back)
            it.setDisplayShowHomeEnabled(true)
        }

        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black)

        primaryColor = ContextCompat.getColor(this, R.color.primary)
        eventFormatter  = EventFormatter(this)
        adapter = UpcomingEventListAdapter(this, this)

        statusHandled = this.resources.getString(R.string.event_was_marked_as_finished)
        eventReminderTimeFmt = this.resources.getString(R.string.reminder_at_fmt)

        colorSkippedItemBotomLine = ContextCompat.getColor(this, R.color.divider)
        colorNonSkippedItemBottomLine = ContextCompat.getColor(this, R.color.secondary_text)

        todayHeading = this.resources.getString(R.string.today_semi)
        todayHeadingEmpty = this.resources.getString(R.string.no_more_today)
        otherDayHeading = this.resources.getString(R.string.tomorrow_and_following)
        otherDayheadingEmpty = this.resources.getString(R.string.no_more_other_days)

        recyclerView = findViewById<RecyclerView>(R.id.list_events)
        recyclerView.adapter = adapter
        adapter.recyclerView = recyclerView
        recyclerView.isNestedScrollingEnabled = false
    }

    override fun onResume() {
        DevLog.debug(LOG_TAG, "onResume")
        super.onResume()

        scope.launch {

            val from = System.currentTimeMillis()
            val to = from + Consts.UPCOMING_EVENTS_WINDOW

            val merged = withContext(Dispatchers.IO) {
                monitorEntries =
                        CalendarMonitor(CalendarProvider)
                                .getAlertsForAlertRange(this@UpcomingNotificationsActivity, scanFrom = from, scanTo = to)
                                .associateBy { it.key }

                val events =
                        CalendarProvider
                                .getEventAlertsForInstancesInRange(this@UpcomingNotificationsActivity, from, to)
                                .filter { it.alertTime >= from }
                                .map { UpcomingEventAlertRecordWrap(false, it) }
                                .sortedBy { it.event?.alertTime ?: 0L }
                                .partition { isTodayAlert(it.event) }

                val ret =
                        listOf(UpcomingEventAlertRecordWrap(true, null, events.first.size)) +
                        events.first +
                        listOf(UpcomingEventAlertRecordWrap(false, null, events.second.size)) +
                        events.second

                ret
            }

            adapter.setEventsToDisplay(merged)
        }

    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun isTodayAlert(event: EventAlertRecord?): Boolean {
        val ev = event ?: return false
        return DateUtils.isToday(ev.alertTime)
    }


    // TODO: add an option to view the event, not only to restore it
    fun onItemClick(v: View, position: Int, entry: UpcomingEventAlertRecordWrap) {
        val event = entry.event ?: return

        startActivity(
                Intent(this, ViewEventActivity::class.java)
                        .putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                        .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                        .putExtra(Consts.INTENT_ALERT_TIME, event.alertTime)
                        .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                        .putExtra(Consts.INTENT_VIEW_FUTURE_EVENT_EXTRA, true)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    fun getItemTitle(entry: UpcomingEventAlertRecordWrap): String {
        val event = entry.event
        if (event != null)
            return event.title

        if (entry.isToday) {
            return if (entry.numItemsInGroup ?: 0 > 0) todayHeading else todayHeadingEmpty
        }
        else {
            return if (entry.numItemsInGroup ?: 0 > 0) otherDayHeading else otherDayheadingEmpty
        }
    }

    fun getItemMiddleLine(entry: UpcomingEventAlertRecordWrap): String {
        val event = entry.event ?: return ""
        return eventFormatter.formatDateTimeOneLine(event)
    }

    fun getItemBottomLine(entry: UpcomingEventAlertRecordWrap): Pair<String, Int> {

        val event = entry.event ?: return Pair("", 0)

        val monEntry = monitorEntries.get(event.monitorEntryKey)
        val wasHandled = monEntry?.wasHandled == true
        val reminderLine = eventReminderTimeFmt.format(eventFormatter.formatTimePoint(event.alertTime, noWeekDay = true))

        return if (wasHandled)
            Pair("$statusHandled $reminderLine", colorSkippedItemBotomLine)
        else
            Pair(reminderLine, colorNonSkippedItemBottomLine)
    }

    fun getItemColor(entry: UpcomingEventAlertRecordWrap): Int {
        val event = entry.event ?: return 0
        return if (event.color != 0)
            event.color.adjustCalendarColor()
        else
            primaryColor
    }


    override fun onPause() {
        super.onPause()
        DevLog.info(LOG_TAG, "onPause")
    }

    companion object {
        private const val LOG_TAG = "UpcomingEventsFragment"
    }
}