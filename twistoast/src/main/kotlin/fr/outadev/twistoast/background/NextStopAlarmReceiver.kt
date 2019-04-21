/*
 * Twistoast - NextStopAlarmReceiver.kt
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

package fr.outadev.twistoast.background

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import fr.outadev.twistoast.ActivityMain
import fr.outadev.twistoast.ConfigurationManager
import fr.outadev.twistoast.R
import fr.outadev.twistoast.TimeFormatter.formatTime
import fr.outadev.twistoast.model.StopSchedule

/**
 * A broadcast receiver called at regular intervals to check
 * if watched buses are incoming and the user should be notified.
 */
class NextStopAlarmReceiver(appContext: Context,  workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        // TODO make this work
        /*val requestHandler = BusDataRepository()
        val database = StopRepository()

        Log.d(TAG, "checking stop schedules for notifications")

        val stopsToCheck = database.watchedStops
        val stopSchedules = requestHandler.getMultipleSchedules(stopsToCheck).observe(lifecycleOwner, {
            res ->

        })

        when (stopSchedules) {
            is Result.Success -> {
                // Look through each schedule
                stopSchedules.data
                        .filter { it.schedules.isNotEmpty() }
                        .forEach { schedule ->
                            // If there are stops scheduled for this bus
                            val busTime = schedule.schedules[0].scheduleTime
                            updateStopTimeNotification(schedule)

                            // THE BUS IS COMIIIING
                            if (busTime.isBefore(DateTime.now().plus(ALARM_TIME_THRESHOLD_MS.toLong()))) {
                                // Remove from database, and send a notification
                                notifyForIncomingBus(schedule)
                                database.stopWatchingStop(schedule.stop)
                                schedule.stop.isWatched = false

                                Log.d(TAG, "less than two minutes till " + busTime.toString() + ": " + schedule.stop)
                            } else if (schedule.stop.lastETA != null) {
                                // Check if there's more than five minutes of difference between the last estimation and the new
                                // one. If that's the case, send the notification anyways; it may already be too late!

                                // This is to work around the fact that we actually can't know if a bus has passed already,
                                // we have to make assumptions instead; if a bus is announced for 3 minutes, and then for 10
                                // minutes the next time we check, it most likely has passed.

                                if (busTime.isBefore(schedule.stop.lastETA?.plus(5 * 60 * 1000.toLong()))) {
                                    // Remove from database, and send a notification
                                    notifyForIncomingBus(schedule)
                                    database.stopWatchingStop(schedule.stop)
                                    schedule.stop.isWatched = false

                                    Log.d(TAG, "last time we saw ${schedule.stop} the bus was scheduled " +
                                            "for ${schedule.stop.lastETA}, but now the ETA is $busTime, so we're notifying")
                                }
                            } else {
                                database.updateWatchedStopETA(schedule.stop, busTime)
                            }
                        }
            }

            is Result.Failure -> {
                stopSchedules.e.printStackTrace()

                // A network error occurred, or something ;-;
                val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                for (stop in stopsToCheck) {
                    notificationManager.cancel(stop.id)
                }

                notifyNetworkError()
                return Result.retry()
            }

            is Result.Loading -> {

            }
        }

        if (database.watchedStopsCount == 0) {
            BackgroundTasksManager.disableStopAlarmJob()
        }
*/
        return Result.success()
    }

    /**
     * Sends a notification to the user informing them that their bus is incoming.
     *
     * @param schedule the schedule to notify about
     */
    private fun notifyForIncomingBus(schedule: StopSchedule) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val config = ConfigurationManager(applicationContext)

        val notificationIntent = Intent(applicationContext, ActivityMain::class.java)
        val contentIntent = PendingIntent.getActivity(applicationContext, 0, notificationIntent, 0)

        // Get the data we need for the notification
        val stop = schedule.stop.name
        val direction = schedule.stop.line.direction.name
        val lineName = schedule.stop.line.name
        val time = formatTime(applicationContext, schedule.schedules[0].scheduleTime)

        // Make a nice notification to inform the user of the bus's imminence
        val builder = NotificationCompat.Builder(applicationContext)
                .setSmallIcon(R.drawable.ic_directions_bus_white)
                .setContentTitle(applicationContext.getString(R.string.notif_watched_content_title, lineName))
                .setContentText(applicationContext.getString(R.string.notif_watched_content_text, stop, direction))
                .setStyle(NotificationCompat.InboxStyle()
                        .addLine(applicationContext.getString(R.string.notif_watched_line_stop, stop))
                        .addLine(applicationContext.getString(R.string.notif_watched_line_direction, direction))
                        .setSummaryText(applicationContext.getString(R.string.notif_watched_summary_text, time)))
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setColor(ContextCompat.getColor(applicationContext, R.color.icon_color))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setDefaults(NotificationSettings.getNotificationDefaults(config.watchNotificationsVibrate, config.watchNotificationsRing))

        notificationManager.notify(schedule.stop.id, builder.build())
    }

    /**
     * Sends a notification to the user and keeps it updated with the latest bus schedules.
     *
     * @param schedule the bus schedule that will be included in the notification
     */
    private fun updateStopTimeNotification(schedule: StopSchedule) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationIntent = Intent(applicationContext, ActivityMain::class.java)
        val contentIntent = PendingIntent.getActivity(applicationContext, 0, notificationIntent, 0)

        // Get the data we need for the notification
        val stop = schedule.stop.name
        val direction = schedule.stop.line.direction.name
        val lineName = schedule.stop.line.name

        val inboxStyle = NotificationCompat.InboxStyle()
                .setSummaryText(applicationContext.getString(R.string.notif_watched_content_text, lineName, direction))
                .setBigContentTitle(applicationContext.getString(R.string.stop_name, stop))

        for (singleSchedule in schedule.schedules) {
            inboxStyle.addLine(formatTime(applicationContext, singleSchedule.scheduleTime) + " - " + singleSchedule.direction)
        }

        // Make a nice notification to inform the user of the bus's imminence
        val builder = NotificationCompat.Builder(applicationContext)
                .setSmallIcon(R.drawable.ic_directions_bus_white)
                .setContentTitle(applicationContext.getString(R.string.notif_ongoing_content_title, stop, lineName))
                .setContentText(applicationContext.getString(R.string.notif_ongoing_content_text, formatTime(applicationContext, schedule.schedules[0].scheduleTime)))
                .setStyle(inboxStyle)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(ContextCompat.getColor(applicationContext, R.color.icon_color))
                .setContentIntent(contentIntent)
                .setOngoing(true)

        notificationManager.notify(Integer.valueOf(schedule.stop.id)!!, builder.build())
    }

    /**
     * Updates the schedule notification to the user, informing him that there's something wrong with the network.
     */
    private fun notifyNetworkError() {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationIntent = Intent(applicationContext, ActivityMain::class.java)
        val contentIntent = PendingIntent.getActivity(applicationContext, 0, notificationIntent, 0)

        // Make a nice notification to inform the user of an error
        val builder = NotificationCompat.Builder(applicationContext)
                .setSmallIcon(R.drawable.ic_directions_bus_white)
                .setContentTitle(applicationContext.getString(R.string.notif_error_content_title))
                .setContentText(applicationContext.getString(R.string.notif_error_content_text))
                .setColor(ContextCompat.getColor(applicationContext, R.color.icon_color))
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(contentIntent).setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID_ERROR, builder.build())
    }

    companion object {

        private val TAG = NextStopAlarmReceiver::class.java.simpleName

        // If the bus is coming in less than ALARM_TIME_THRESHOLD_MS milliseconds, send a notification.
        const val ALARM_TIME_THRESHOLD_MS = 90 * 1000

        const val NOTIFICATION_ID_ERROR = 42
    }

}
