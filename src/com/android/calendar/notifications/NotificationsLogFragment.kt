package com.android.calendar.notifications

import android.app.Fragment
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.*
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.calendar.CalendarController
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.CalNotifyController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventFinishType
import com.github.quarck.calnotify.calendar.FinishedEventAlertRecord
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.eventsstorage.FinishedEventsStorage
import com.github.quarck.calnotify.ui.*
import com.github.quarck.calnotify.utils.adjustCalendarColor
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.textutils.EventFormatter
import com.github.quarck.calnotify.utils.textutils.dateToStr
import kotlinx.coroutines.*
import org.qrck.seshat.R


fun FinishedEventAlertRecord.formatReason(ctx: Context): String =
        when (this.finishType) {
            EventFinishType.ManuallyViaNotification ->
                String.format(ctx.resources.getString(R.string.complete_from_notification), dateToStr(ctx, this.finishTime))

            EventFinishType.ManuallyInTheApp ->
                String.format(ctx.resources.getString(R.string.complete_from_the_app), dateToStr(ctx, this.finishTime))

            EventFinishType.AutoDueToCalendarMove ->
                String.format(ctx.resources.getString(R.string.event_moved_new_time), dateToStr(ctx, this.event.startTime))

            EventFinishType.EventMovedInTheApp ->
                String.format(ctx.resources.getString(R.string.event_moved_new_time), dateToStr(ctx, this.event.startTime))

            EventFinishType.DeletedInTheApp ->
                String.format(ctx.resources.getString(R.string.event_deleted_in_the_app), dateToStr(ctx, this.finishTime))
        }


class NotificationsLogFragment : Fragment(), CalendarController.EventHandler, SimpleEventListCallback<FinishedEventAlertRecord> {
    // TODO: Rename and change types of parameters

    private val scope = MainScope()

    private lateinit var recyclerView: RecyclerView

    private var adapter: SimpleEventListAdapter<FinishedEventAlertRecord>? = null

    private var primaryColor: Int? = Consts.DEFAULT_CALENDAR_EVENT_COLOR
    private var eventFormatter: EventFormatter? = null

    private var bottomLineColor: Int = 0x7f3f3f3f

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        DevLog.info(LOG_TAG, "onCreateView")

        val root = inflater.inflate(R.layout.fragment_finished, container, false)

        this.context?.let {
            ctx ->
            primaryColor = ContextCompat.getColor(ctx, R.color.primary)
            eventFormatter  = EventFormatter(ctx)
            adapter =
                    SimpleEventListAdapter(
                            ctx,
                            R.layout.event_card_compact,
                            this)

            bottomLineColor = ContextCompat.getColor(ctx, R.color.divider)
        }

        recyclerView = root.findViewById<RecyclerView>(R.id.list_events)
        recyclerView.adapter = adapter;
        adapter?.recyclerView = recyclerView

        return root
    }

    override fun onResume() {
        DevLog.debug(LOG_TAG, "onResume")
        super.onResume()

        val ctx = this.activity ?: return

        scope.launch {
            val events = withContext(Dispatchers.IO) {
                FinishedEventsStorage(ctx).use { db ->
                    db.events.sortedByDescending { it.finishTime }.toMutableList()
                }
            }
            adapter?.setEventsToDisplay(events)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // TODO: add an option to view the event, not only to restore it
    override fun onItemClick(v: View, position: Int, entry: FinishedEventAlertRecord) {

        this.context?.let {
            ctx ->

            val popup = PopupMenu(ctx, v)
            val inflater = popup.menuInflater

            inflater.inflate(R.menu.finished_event_popup, popup.menu)

            popup.setOnMenuItemClickListener {
                item ->

                when (item.itemId) {
                    R.id.action_mark_not_finished -> {
                        CalNotifyController.restoreEvent(ctx, entry.event)
                        adapter?.removeEntry(entry)
                        true
                    }
                    else ->
                        false
                }
            }

            popup.show()
        }
    }

    override fun getItemTitle(entry: FinishedEventAlertRecord): String =  entry.event.title

    override fun getItemMiddleLine(entry: FinishedEventAlertRecord): String = eventFormatter?.formatDateTimeOneLine(entry.event) ?: "_NO_FORMATTER_"

    override fun getItemBottomLine(entry: FinishedEventAlertRecord): Pair<String, Int> = Pair(context?.let{ entry.formatReason(it) } ?: "_NO_CONTEXT_", bottomLineColor)

    override fun getItemColor(entry: FinishedEventAlertRecord): Int =
            if (entry.event.color != 0)
                entry.event.color.adjustCalendarColor()
            else
                primaryColor ?: Consts.DEFAULT_CALENDAR_EVENT_COLOR

    override fun getUseBoldTitle(entry: FinishedEventAlertRecord): Boolean = false

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
        private const val LOG_TAG = "FinishedEventsFragment"
    }

}