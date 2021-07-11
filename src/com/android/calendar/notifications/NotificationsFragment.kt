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
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.calendar.CalendarController
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.CalNotifyController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventFinishType
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.eventsstorage.FinishedEventsStorage
import com.github.quarck.calnotify.ui.*
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.md5state
import kotlinx.coroutines.*
import org.qrck.seshat.R

class NotificationsFragment : Fragment(), CalendarController.EventHandler, EventListCallback {
    // TODO: Rename and change types of parameters

    private val scope = MainScope()

    private lateinit var recyclerView: RecyclerView
    private lateinit var reloadLayout: RelativeLayout

    private var refreshLayout: SwipeRefreshLayout? = null

    private var adapter: EventListAdapter? = null
    private var lastEventsSummary = md5state(0, 0, 0, 0)

    private var lastEventDismissalScrollPosition: Int? = null

    private var emptyView: TextView? = null

    private val undoDisappearSensitivity: Float by lazy {
        resources.getDimension(R.dimen.undo_dismiss_sensitivity)
    }

    private val dataUpdatedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            onDataUpdated(causedByUser = intent?.getBooleanExtra(Consts.INTENT_IS_USER_ACTION, false)
                    ?: false)
        }
    } // DataUpdatedReceiverNG(this)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DevLog.info(LOG_TAG, "onCreate")
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        DevLog.info(LOG_TAG, "onCreateView")

        val root = inflater.inflate(R.layout.fragment_notifications, container, false)

        refreshLayout = root.findViewById<SwipeRefreshLayout?>(R.id.cardview_refresh_layout)
        refreshLayout?.setOnRefreshListener {
            reloadLayout.visibility = View.GONE;
            reloadData()

            val ctx = this.activity
            ctx?.let {
                val calendarController = CalendarController.getInstance(ctx);
                calendarController.refreshCalendars();
            }
        }

        this.context?.let {
            adapter = EventListAdapter(it, this)
        }

        recyclerView = root.findViewById<RecyclerView>(R.id.list_events)
        recyclerView.adapter = adapter
        adapter?.recyclerView = recyclerView

        reloadLayout = root.findViewById<RelativeLayout>(R.id.activity_main_reload_layout)

        emptyView = root.findViewById(R.id.empty_view)

        return root

    }

    override fun onResume() {
        super.onResume()
        DevLog.info(LOG_TAG, "onResume")

        context?.registerReceiver(dataUpdatedReceiver, IntentFilter(Consts.DATA_UPDATED_BROADCAST))

        reloadData()

        context?.let { ctx ->
            scope.launch(Dispatchers.Default) {
                CalNotifyController.onMainActivityResumed(ctx)
            }
        }

        this.activity?.invalidateOptionsMenu()
    }

    override fun onPause() {
        DevLog.info(LOG_TAG, "onPause")
        context?.unregisterReceiver(dataUpdatedReceiver)
        super.onPause()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, menuInflater)
        DevLog.info(LOG_TAG, "onCreateOptionsMenu")
        menuInflater.inflate(R.menu.notifications_menu, menu)

        val menuItem = menu.findItem(R.id.action_snooze_all)
        if (menuItem != null) {
            menuItem.isEnabled = (adapter?.itemCount ?: 0) > 0
            menuItem.title =
                    resources.getString(
                            if (adapter?.hasActiveEvents == true) R.string.snooze_all else R.string.change_all)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        this.context?.let { ctx ->
            when (item.itemId) {
                R.id.action_snooze_all ->
                    startActivity(
                            Intent(ctx, SnoozeAllActivity::class.java)
                                    .putExtra(Consts.INTENT_SNOOZE_ALL_IS_CHANGE, !(adapter?.hasActiveEvents ?: false))
                                    )

                R.id.action_upcoming -> {
                    startActivity(
                            Intent(ctx, UpcomingNotificationsActivity::class.java))
                }

                R.id.action_log -> {
                    startActivity(
                            Intent(ctx, NotificationsLogActivity::class.java))
                }
            }
        }


        return super.onOptionsItemSelected(item)
    }

    private suspend fun loadCurrentEvents(ctx: Context, skipPurge: Boolean = false) = withContext(Dispatchers.IO) {

        if (!skipPurge) {
            FinishedEventsStorage(ctx).use {
                it.purgeOld(System.currentTimeMillis(), Consts.BIN_KEEP_HISTORY_MILLISECONDS)
            }
        }

        val events = EventsStorage(ctx).use { db ->
            db.events.sortedWith(
                    Comparator { lhs, rhs ->
                        if (lhs.snoozedUntil < rhs.snoozedUntil)
                            return@Comparator -1;
                        else if (lhs.snoozedUntil > rhs.snoozedUntil)
                            return@Comparator 1;

                        if (lhs.lastStatusChangeTime > rhs.lastStatusChangeTime)
                            return@Comparator -1;
                        else if (lhs.lastStatusChangeTime < rhs.lastStatusChangeTime)
                            return@Comparator 1;

                        return@Comparator 0;

                    }).toTypedArray()
        }

        val summary = md5state(0, 0, 0, 0)

        for (e in events) {
            summary.xor(e.contentMd5)
        }

        Pair(events, summary)
    }


    private fun reloadData() {
        val ctx = this.context ?: return

        scope.launch {
            val (events, eventsSummary) = loadCurrentEvents(ctx)
            adapter?.setEventsToDisplay(events);
            lastEventsSummary = eventsSummary
            onNumEventsUpdated()
            refreshLayout?.isRefreshing = false
        }
    }

    override fun onScrollPositionChange(newPos: Int) {

        val undoSense = lastEventDismissalScrollPosition
        if (undoSense != null) {
            if (Math.abs(undoSense - newPos) > undoDisappearSensitivity) {
                lastEventDismissalScrollPosition = null
                adapter?.clearUndoState()
            }
        }
    }

    private fun onNumEventsUpdated() {
        val hasEvents = (adapter?.itemCount ?: 0) > 0
        emptyView?.visibility = if (hasEvents) View.GONE else View.VISIBLE;
        this.activity?.invalidateOptionsMenu();
    }


    override fun onEventClick(v: View, position: Int, eventId: Long) {
        DevLog.info(LOG_TAG, "onItemClick, pos=$position, eventId=$eventId")

        val event = adapter?.getEventAtPosition(position, eventId)

        this.context?.let { ctx ->
            if (event != null) {
                startActivity(
                        Intent(ctx, ViewEventActivity::class.java)
                                .putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, event.notificationId)
                                .putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                                .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

            }
        }
    }

    // Item was already removed from UI, we just have to dismiss it now
    override fun onEventMarkDone(event: EventAlertRecord) {

        this.context?.let { ctx ->
            DevLog.info(LOG_TAG, "onItemRemoved: Removing event id ${event.eventId} from DB and dismissing notification id ${event.notificationId}")
            CalNotifyController.dismissEvent(ctx, EventFinishType.ManuallyInTheApp, event)
            lastEventDismissalScrollPosition = adapter?.scrollPosition
            lastEventsSummary.xor(event.contentMd5)
            onNumEventsUpdated()
        }
    }

    override fun onEventReschedule(event: EventAlertRecord) {
        this.context?.let {
            ctx ->
            ViewEventActivity.rescheduleEvent(ctx, event) {}
        }
    }

    override fun onEventUnmarkDone(event: EventAlertRecord) {
        this.context?.let { ctx ->
            DevLog.info(LOG_TAG, "onItemRestored, eventId=${event.eventId}")
            CalNotifyController.restoreEvent(ctx, event)
            lastEventsSummary.xor(event.contentMd5)
            onNumEventsUpdated()
        }
    }

    fun onDataUpdated(causedByUser: Boolean) {
        if (causedByUser) {
            reloadData()
            return
        }

        val act = this.activity ?: return

        scope.launch {
            val (_, eventsSummary) = loadCurrentEvents(act, true)
            DevLog.debug(LOG_TAG, "onDataUpdated: last summary: $lastEventsSummary, new summary: $eventsSummary")
            if (lastEventsSummary != eventsSummary) {
                withContext(Dispatchers.Main) {
                    reloadLayout.visibility = View.VISIBLE
                }
            }
        }
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
        onDataUpdated(causedByUser = false)
    }

    companion object {
        const val LOG_TAG = "NotificationsActivity"
    }
}