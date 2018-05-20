/*
 * Twistoast - FragmentRealtime.kt
 * Copyright (C) 2013-2018 Baptiste Candellier
 *
 * Twistoast is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Twistoast is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.outadev.twistoast

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.*
import androidx.navigation.fragment.findNavController
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import fr.outadev.twistoast.background.BackgroundTasksManager
import fr.outadev.twistoast.extensions.getAlertMessage
import fr.outadev.twistoast.model.*
import fr.outadev.twistoast.persistence.IStopRepository
import fr.outadev.twistoast.persistence.StopRepository
import fr.outadev.twistoast.providers.BusDataRepository
import fr.outadev.twistoast.providers.IBusDataRepository
import kotlinx.android.synthetic.main.fragment_realtime.*
import kotlinx.android.synthetic.main.view_no_content.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.support.v4.onUiThread
import org.jetbrains.anko.uiThread

class FragmentRealtime : Fragment(), IStopsListContainer {

    private val periodicRefreshHandler = Handler()

    private lateinit var periodicRefreshRunnable: Runnable
    private lateinit var databaseHandler: IStopRepository
    private lateinit var config: ConfigurationManager
    private lateinit var referenceUpdater: TimeoStopReferenceUpdater
    private lateinit var requestHandler: IBusDataRepository

    private var stopsList: MutableList<Stop> = mutableListOf()
    private val schedules: MutableMap<Stop, StopSchedule> = mutableMapOf()

    private var listAdapter: RecyclerAdapterRealtime? = null

    override var isRefreshing: Boolean = false
    private var isInBackground: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // yes hello please, I'd like to be inflated?
        setHasOptionsMenu(true)

        config = ConfigurationManager()
        databaseHandler = StopRepository()
        referenceUpdater = TimeoStopReferenceUpdater()
        requestHandler = BusDataRepository()

        periodicRefreshRunnable = Runnable {
            if (config.autoRefresh) {
                refreshAllStopSchedules(false)
            }
        }

        isRefreshing = false
        isInBackground = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_realtime, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefreshContainer!!.setOnRefreshListener { refreshAllStopSchedules(false) }
        swipeRefreshContainer!!.setColorSchemeResources(
                R.color.twisto_primary, R.color.twisto_secondary,
                R.color.twisto_primary, R.color.twisto_secondary)

        val layoutManager = GridLayoutManager(activity, 3)

        context?.let {
            stopsRecyclerView.addItemDecoration(DividerItemDecoration(it, DividerItemDecoration.VERTICAL))
            stopsRecyclerView.layoutManager = layoutManager
        }

        stopsRecyclerView.viewTreeObserver.addOnGlobalLayoutListener {
            stopsRecyclerView?.let {
                val viewWidth = it.measuredWidth
                val cardViewWidth = resources.getDimension(R.dimen.schedule_row_max_size)
                val newSpanCount = Math.floor((viewWidth / cardViewWidth).toDouble()).toInt()

                if (newSpanCount >= 1) {
                    layoutManager.spanCount = newSpanCount
                    layoutManager.requestLayout()
                }
            }
        }

        stopsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                when {
                    dy > 0 -> // Scroll Down
                        if (floatingActionButton.isShown) {
                            floatingActionButton.hide()
                        }
                    dy < 0 -> // Scroll Up
                        if (!floatingActionButton.isShown) {
                            floatingActionButton.show()
                        }
                }
            }
        })

        registerForContextMenu(stopsRecyclerView)

        setupAdvertisement()
        setupListeners()
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)

        // If we're refreshing, abort
        if (isRefreshing || listAdapter?.longPressedItemPosition == null)
            return

        // Contextual menu (delete or watch stop) creation
        activity?.menuInflater?.inflate(R.menu.stops_list_contextual, menu)

        // Get the bus stop that summoned this menu
        val position = listAdapter!!.longPressedItemPosition
        val stop = listAdapter!!.getItem(position!!)

        // Show the "watch this stop" if it's not watched already, and vice-versa
        menu?.findItem(R.id.menu_stop_watch)?.isVisible = !stop?.isWatched!!
        menu?.findItem(R.id.menu_stop_unwatch)?.isVisible = stop.isWatched
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        // If we're refreshing, abort
        if (isRefreshing || listAdapter?.longPressedItemPosition == null) return true

        // Get the bus stop that summoned this menu
        val position = listAdapter!!.longPressedItemPosition
        val stop = listAdapter!!.getItem(position!!) ?: return true

        when (item.itemId) {
            R.id.menu_stop_delete -> {
                deleteStopAction(stop, position)
                return true
            }

            R.id.menu_stop_watch -> {
                startWatchingStopAction(stop)
                return true
            }

            R.id.menu_stop_unwatch -> {
                stopWatchingStopAction(stop)
                return true
            }

            else -> return super.onContextItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.realtime_list, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sortby_line -> {
                config.listSortOrder = SortBy.LINE
                refreshAllStopSchedules(true)
                return true
            }

            R.id.sortby_stop -> {
                config.listSortOrder = SortBy.STOP
                refreshAllStopSchedules(true)
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()
        refreshAllStopSchedules(true)
    }

    override fun onResume() {
        super.onResume()

        isInBackground = false
        // when the activity is resuming, refresh
        refreshAllStopSchedules(false)
    }

    override fun onPause() {
        super.onPause()

        // when the activity is pausing, stop refreshing automatically
        Log.i(TAG, "stopping automatic refresh, app paused")

        isInBackground = true
        periodicRefreshHandler.removeCallbacks(periodicRefreshRunnable)
    }

    override fun onUpdatedStopReferences() {
        refreshAllStopSchedules(true)
    }

    private fun setupListeners() {
        floatingActionButton.setOnClickListener {
            findNavController().navigate(R.id.dest_new_stop)
        }

        adView.adListener = object : AdListener() {

            override fun onAdFailedToLoad(errorCode: Int) {
                adView?.visibility = View.GONE
                super.onAdFailedToLoad(errorCode)
            }

            override fun onAdLoaded() {
                adView?.visibility = View.VISIBLE
                super.onAdLoaded()
            }

        }

        val watchedStopStateListener = object : IWatchedStopChangeListener {

            override fun onStopWatchingStateChanged(stop: Stop, watched: Boolean) {
                stopsList.filter { it == stop }.forEach {
                    it.isWatched = watched
                }

                listAdapter?.notifyDataSetChanged()
            }

        }
    }

    private fun setupAdvertisement() {
        if (!resources.getBoolean(R.bool.enableAds) || config.adsAreRemoved) {
            // If we don't want ads, hide the view
            adView?.visibility = View.GONE
        } else {
            // If we want ads, check for availability and load them
            val hasGPS = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)

            if (hasGPS == ConnectionResult.SUCCESS) {
                val adRequest = AdRequest.Builder()
                        .addTestDevice(getString(R.string.admob_test_device))
                        .build()

                adView?.loadAd(adRequest)
            }
        }
    }

    private fun deleteStopAction(stop: Stop, position: Int) {
        // Remove from the database and the interface
        databaseHandler.deleteStop(stop)
        stopsList.remove(stop)

        if (stop.isWatched) {
            databaseHandler.stopWatchingStop(stop)
            stop.isWatched = false
        }

        if (stopsList.isEmpty()) {
            setNoContentViewVisible(true)
        }

        listAdapter?.notifyDataSetChanged()

        Snackbar.make(stopsRecyclerView, R.string.confirm_delete_success, Snackbar.LENGTH_LONG).setAction(R.string.cancel_stop_deletion) {
            Log.i(RecyclerAdapterRealtime.TAG, "restoring stop $stop")

            databaseHandler.addStopToDatabase(stop)
            stopsList.add(position, stop)
            listAdapter?.notifyDataSetChanged()
        }.show()
    }

    private fun startWatchingStopAction(stop: Stop) {
        // We wish to get notifications about this upcoming stop
        databaseHandler.addToWatchedStops(stop)
        stop.isWatched = true
        listAdapter?.notifyDataSetChanged()

        // Turn the notifications on
        context?.applicationContext?.let { BackgroundTasksManager.enableStopAlarmJob() }

        Snackbar.make(stopsRecyclerView, getString(R.string.notifs_enable_toast, stop.name), Snackbar.LENGTH_LONG).setAction(R.string.cancel_stop_deletion) {
            databaseHandler.stopWatchingStop(stop)
            stop.isWatched = false
            listAdapter?.notifyDataSetChanged()

            // Turn the notifications back off if necessary
            if (databaseHandler.watchedStopsCount == 0) {
                context?.applicationContext?.let { appCtx -> BackgroundTasksManager.disableStopAlarmJob() }
            }
        }.show()
    }

    private fun stopWatchingStopAction(stop: Stop) {
        // JUST STOP THESE NOTIFICATIONS ALREADY GHGHGHBLBLBL
        databaseHandler.stopWatchingStop(stop)
        stop.isWatched = false
        listAdapter?.notifyDataSetChanged()

        // Turn the notifications back off if necessary
        if (databaseHandler.watchedStopsCount == 0) {
            context?.applicationContext?.let { BackgroundTasksManager.disableStopAlarmJob() }
        }

        val notificationManager = activity?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(Integer.valueOf(stop.id)!!)

        Snackbar.make(stopsRecyclerView, getString(R.string.notifs_disable_toast, stop.name), Snackbar.LENGTH_LONG).show()
    }

    /**
     * Refreshes the list's schedules and displays them to the user.
     *
     * @param reloadFromDatabase true if we want to reload the stops completely, or false if we only want
     * to update the schedules
     */
    fun refreshAllStopSchedules(reloadFromDatabase: Boolean) {
        // we don't want to try to refresh if we're already refreshing (causes bugs)
        if (isRefreshing) {
            return
        } else {
            isRefreshing = true
        }

        // show the refresh animation
        if (swipeRefreshContainer != null)
            swipeRefreshContainer.isRefreshing = true

        // we have to reset the adapter so it correctly loads the stops
        // if we don't do that, bugs will appear when the database has been
        // modified
        if (reloadFromDatabase && activity != null) {
            val criteria = config.listSortOrder

            stopsList = databaseHandler.getAllStops(criteria).toMutableList()
            listAdapter = RecyclerAdapterRealtime(stopsList, schedules)
            stopsRecyclerView.adapter = listAdapter
        }

        // finally, get the schedule
        updateScheduleData()
    }

    override fun endRefresh(success: Boolean) {
        // notify the pull to refresh view that the refresh has finished
        isRefreshing = false

        if (swipeRefreshContainer != null)
            swipeRefreshContainer.isRefreshing = false

        noContentView?.visibility = if (listAdapter?.itemCount == 0) View.VISIBLE else View.GONE

        // reset the timer loop, and start it again
        // this ensures the list is refreshed automatically every 60 seconds
        periodicRefreshHandler.removeCallbacks(periodicRefreshRunnable)

        if (!isInBackground) {
            periodicRefreshHandler.postDelayed(periodicRefreshRunnable, REFRESH_INTERVAL)
        }

        Log.i(TAG, "refreshed, " + listAdapter!!.itemCount + " stops in db")
    }

    override fun setNoContentViewVisible(visible: Boolean) {
        noContentView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * Fetches every stop schedule from the API and reloads everything.
     */
    private fun updateScheduleData() {
        // start refreshing schedules
        doAsync {
            // Get the schedules and put them in a list
            var scheduleMap: Map<Stop, StopSchedule>
            var schedulesList = requestHandler.getMultipleSchedules(stopsList)

            var didUpdate = false

            when (schedulesList) {
                is Result.Success -> {
                    scheduleMap = schedulesList.data.associateBy({ it.stop }, { it })

                    val outdated = requestHandler.checkForOutdatedStops(stopsList, schedulesList.data)

                    when (outdated) {
                        is Result.Success -> {
                            // If there are outdated reference numbers, update those stops
                            if (outdated.data > 0) {
                                val nbUpdated = referenceUpdater.updateAllStopReferences(stopsList)

                                when (nbUpdated) {
                                    is Result.Success -> {
                                        if (nbUpdated.data > 0) {
                                            // If there were stops updated successfully, reload data to get the new ones
                                            schedulesList = requestHandler.getMultipleSchedules(stopsList)

                                            when (schedulesList) {
                                                is Result.Success -> {
                                                    scheduleMap = schedulesList.data.associateBy({ it.stop }, { it })
                                                    didUpdate = true
                                                }
                                                is Result.Failure -> displayErrors(schedulesList.e)
                                            }
                                        }
                                    }

                                    is Result.Failure -> displayErrors(nbUpdated.e)
                                }
                            }
                        }

                        is Result.Failure -> displayErrors(outdated.e)
                    }

                    uiThread {
                        schedules.clear()
                        schedules.putAll(scheduleMap)

                        listAdapter?.notifyDataSetChanged()
                        endRefresh(scheduleMap.isNotEmpty())

                        if (didUpdate) {
                            Log.i(TAG, "Updated some references, refreshing stops")

                            // If there were stops to update and they were updated successfully
                            onUpdatedStopReferences()
                        }
                    }
                }

                is Result.Failure -> displayErrors(schedulesList.e)
            }
        }
    }

    private fun displayErrors(e: Throwable) {
        onUiThread {
            endRefresh(false)

            when (e) {
                is BlockingMessageException -> {
                    if (!isDetached && view != null) {
                        // It's it's a blocking message, display it in a dialog
                        e.getAlertMessage(context).show()
                    }
                }

                is DataProviderException -> {
                    if (!isDetached && view != null && stopsRecyclerView != null) {
                        // If there are details to the error, display them. Otherwise, only display the error code

                        val message: String = if (!e.message?.trim { it <= ' ' }!!.isEmpty()) {
                            getString(R.string.error_toast_twisto_detailed, e.errorCode, e.message)
                        } else {
                            getString(R.string.error_toast_twisto, e.errorCode)
                        }

                        Snackbar.make(stopsRecyclerView, message, Snackbar.LENGTH_LONG)
                                .setAction(R.string.error_retry) { updateScheduleData() }.show()
                    }
                }

                else -> {
                    if (!isDetached && view != null && stopsRecyclerView != null) {
                        Snackbar.make(stopsRecyclerView, R.string.loading_error, Snackbar.LENGTH_LONG)
                                .setAction(R.string.error_retry) { updateScheduleData() }.show()
                    }
                }
            }
        }
    }

    companion object {
        //Refresh automatically every 60 seconds.
        private const val REFRESH_INTERVAL = 60000L
        private val TAG = FragmentRealtime::class.java.simpleName
    }

}
