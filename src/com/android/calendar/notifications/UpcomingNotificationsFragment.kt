package com.android.calendar.notifications

import android.app.Fragment
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.format.DateUtils
import android.view.*
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.calendar.CalendarController
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.CalNotifyController
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitor
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.eventsstorage.FinishedEventsStorage
import com.github.quarck.calnotify.ui.*
import com.github.quarck.calnotify.utils.adjustCalendarColor
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.textutils.EventFormatter
import kotlinx.coroutines.*
import org.qrck.seshat.R

data class UpcomingEventAlertRecordWrap(
        val isToday: Boolean,
        val event: EventAlertRecord?
)

class UpcomingEventListAdapter(
        val context: Context,
        val cb: UpcomingNotificationsFragment
) : RecyclerView.Adapter<UpcomingEventListAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View)
        : RecyclerView.ViewHolder(itemView) {
        //var eventId: Long = 0;
        var entry: UpcomingEventAlertRecordWrap? = null

        var eventHolder: RelativeLayout? = itemView.findViewById<RelativeLayout>(R.id.card_view_main_holder)
        var eventTitleText = itemView.findViewById<TextView>(R.id.card_view_event_name)

        var eventDateText = itemView.findViewById<TextView>(R.id.card_view_event_date)
        var eventTimeText: TextView = itemView.findViewById<TextView>(R.id.card_view_event_time)

        var snoozedUntilText: TextView? = itemView.findViewById<TextView>(R.id.card_view_snoozed_until)
        val compactViewCalendarColor: View? = itemView.findViewById<View>(R.id.compact_view_calendar_color)

        val headingLayout = itemView.findViewById<RelativeLayout>(R.id.event_card_heading_layout)
        val headingText = itemView.findViewById<TextView>(R.id.event_view_heading_text)

        var calendarColor: ColorDrawable = ColorDrawable(0)

        init {
            eventHolder?.setOnClickListener{
                if (entry != null)
                    cb.onItemClick(eventTitleText, adapterPosition, entry!!);
            }

            itemView.findViewById<RelativeLayout>(R.id.event_card_undo_layout).visibility = View.GONE
            itemView.findViewById<RelativeLayout>(R.id.compact_view_content_layout).visibility = View.VISIBLE
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
            holder.headingLayout.visibility = View.GONE
            holder.eventTitleText.text = cb.getItemTitle(entry) // entry.event.title

            val time = cb.getItemMiddleLine(entry) // eventFormatter.formatDateTimeOneLine(entry.event)
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


class UpcomingNotificationsFragment : Fragment(), CalendarController.EventHandler {
    private val scope = MainScope()

    private lateinit var recyclerView: RecyclerView

    private var adapter: UpcomingEventListAdapter? = null

    private var primaryColor: Int? = Consts.DEFAULT_CALENDAR_EVENT_COLOR
    private var eventFormatter: EventFormatter? = null

    private var statusHandled: String? = null
    private var eventReminderTimeFmt: String? = null
    private var todayHeading: String? = null
    private var otherDayHeading: String? = null
    private var colorSkippedItemBotomLine: Int  = 0x7f3f3f3f
    private var colorNonSkippedItemBottomLine: Int = 0x7f7f7f7f

    private var monitorEntries = mapOf<MonitorEventAlertEntryKey, MonitorEventAlertEntry>()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_upcoming, container, false)

        this.context?.let {
            ctx ->
            primaryColor = ContextCompat.getColor(ctx, R.color.primary)
            eventFormatter  = EventFormatter(ctx)
            adapter = UpcomingEventListAdapter(ctx, this)

            statusHandled = ctx.resources.getString(R.string.event_was_marked_as_finished)
            eventReminderTimeFmt = ctx.resources.getString(R.string.reminder_at_fmt)

            colorSkippedItemBotomLine = ContextCompat.getColor(ctx, R.color.divider)
            colorNonSkippedItemBottomLine = ContextCompat.getColor(ctx, R.color.secondary_text)

            todayHeading = ctx.resources.getString(R.string.today_semi)
            otherDayHeading = ctx.resources.getString(R.string.tomorrow_and_following)
        }

        recyclerView = root.findViewById<RecyclerView>(R.id.list_events)
        recyclerView.adapter = adapter
        adapter?.recyclerView = recyclerView
        recyclerView.isNestedScrollingEnabled = false

        return root
    }

    override fun onResume() {
        DevLog.debug(LOG_TAG, "onResume")
        super.onResume()

        val ctx: Context = this.activity ?: return

        scope.launch {

            val from = System.currentTimeMillis()
            val to = from + Consts.UPCOMING_EVENTS_WINDOW

            val merged = withContext(Dispatchers.IO) {
                monitorEntries =
                        CalendarMonitor(CalendarProvider)
                                .getAlertsForAlertRange(ctx, scanFrom = from, scanTo = to)
                                .associateBy { it.key }

                val events =
                        CalendarProvider
                                .getEventAlertsForInstancesInRange(ctx, from, to)
                                .filter { it.alertTime >= from }
                                .map { UpcomingEventAlertRecordWrap(false, it) }
                                .sortedBy { it.event?.alertTime ?: 0L }
                                .partition { isTodayAlert(it.event) }

                listOf(UpcomingEventAlertRecordWrap(true, null)) + events.first +
                        listOf(UpcomingEventAlertRecordWrap(false, null)) + events.second
            }

            adapter?.setEventsToDisplay(merged)
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
        this.context?.let {
            ctx ->
            startActivity(
                    Intent(ctx, ViewEventActivity::class.java)
                            .putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                            .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                            .putExtra(Consts.INTENT_ALERT_TIME, event.alertTime)
                            .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                            .putExtra(Consts.INTENT_VIEW_FUTURE_EVENT_EXTRA, true)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }

    fun getItemTitle(entry: UpcomingEventAlertRecordWrap): String {
        val event = entry.event
        return event?.title ?: (if (entry.isToday) todayHeading else otherDayHeading) ?: ""
    }

    fun getItemMiddleLine(entry: UpcomingEventAlertRecordWrap): String {
        val event = entry.event ?: return ""
        return eventFormatter?.formatDateTimeOneLine(event) ?: "NULL"
    }

    fun getItemBottomLine(entry: UpcomingEventAlertRecordWrap): Pair<String, Int> {

        val event = entry.event ?: return Pair("", 0)

        val monEntry = monitorEntries.get(event.monitorEntryKey)
        val wasHandled = monEntry?.wasHandled == true
        val reminderLine = eventFormatter?.let { (eventReminderTimeFmt ?: "%s").format(it.formatTimePoint(event.alertTime, noWeekDay = true)) } ?: ""

        return if (wasHandled)
            Pair((statusHandled ?: "/SKIP/") + " " + reminderLine, colorSkippedItemBotomLine)
        else
            Pair(reminderLine, colorNonSkippedItemBottomLine)
    }

    fun getItemColor(entry: UpcomingEventAlertRecordWrap): Int {
        val event = entry.event ?: return 0
        return if (event.color != 0)
            event.color.adjustCalendarColor()
        else
            primaryColor ?: Consts.DEFAULT_CALENDAR_EVENT_COLOR
    }


    override fun onPause() {
        super.onPause()
        DevLog.info(LOG_TAG, "onPause")
    }

    override fun onDetach() {
        super.onDetach()
        DevLog.info(LOG_TAG, "onDetach")
    }


    override fun getSupportedEventTypes(): Long {
        return CalendarController.EventType.EVENTS_CHANGED
    }

    override fun handleEvent(event: CalendarController.EventInfo) {
        if (event.eventType == CalendarController.EventType.EVENTS_CHANGED) {
            eventsChanged()
        }
    }
    override fun eventsChanged() {
    }


    companion object {
        private const val LOG_TAG = "UpcomingEventsFragment"
    }
}