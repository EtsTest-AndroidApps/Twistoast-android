/*
 * Twistoast - NextStopAlarmReceiver
 * Copyright (C) 2013-2016 Baptiste Candellier
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

package fr.outadev.twistoast.background;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.joda.time.DateTime;

import java.util.List;

import fr.outadev.android.transport.timeo.TimeoRequestHandler;
import fr.outadev.android.transport.timeo.TimeoSingleSchedule;
import fr.outadev.android.transport.timeo.TimeoStop;
import fr.outadev.android.transport.timeo.TimeoStopSchedule;
import fr.outadev.twistoast.ActivityMain;
import fr.outadev.twistoast.ConfigurationManager;
import fr.outadev.twistoast.Database;
import fr.outadev.twistoast.DatabaseOpenHelper;
import fr.outadev.twistoast.IWatchedStopChangeListener;
import fr.outadev.twistoast.R;
import fr.outadev.twistoast.TimeFormatter;
import fr.outadev.twistoast.utils.Utils;

/**
 * A broadcast receiver called at regular intervals to check
 * if watched buses are incoming and the user should be notified.
 */
public class NextStopAlarmReceiver extends BroadcastReceiver {

    // If the bus is coming in less than ALARM_TIME_THRESHOLD_MS milliseconds, send a notification.
    public static final int ALARM_TIME_THRESHOLD_MS = 90 * 1000;
    private static final int ALARM_FREQUENCY = 60 * 1000;
    private static final int ALARM_TYPE = AlarmManager.ELAPSED_REALTIME_WAKEUP;

    private static final int NOTIFICATION_ID_ERROR = 42;
    private static IWatchedStopChangeListener sWatchedStopStateListener = null;
    private Context mContext;

    /**
     * Enables the regular checks performed every minute by this receiver.
     * They should be disabled once not needed anymore, as they can be battery and network hungry.
     *
     * @param context a context
     */
    static void enable(Context context) {
        Log.d(Utils.TAG, "enabling " + NextStopAlarmReceiver.class.getSimpleName());

        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.setInexactRepeating(ALARM_TYPE,
                SystemClock.elapsedRealtime() + 1000, ALARM_FREQUENCY, getBroadcast(context));
    }

    /**
     * Disables the regular checks performed every minute by this receiver.
     *
     * @param context a context
     */
    static void disable(Context context) {
        Log.d(Utils.TAG, "disabling " + NextStopAlarmReceiver.class.getSimpleName());

        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.cancel(getBroadcast(context));
    }

    /**
     * Returns the PendingIntent that will be called by the alarm every minute.
     *
     * @param context a context
     * @return the PendingIntent corresponding to this class
     */
    public static PendingIntent getBroadcast(Context context) {
        Intent intent = new Intent(context, NextStopAlarmReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    public static void setWatchedStopDismissalListener(IWatchedStopChangeListener watchedStopStateListener) {
        NextStopAlarmReceiver.sWatchedStopStateListener = watchedStopStateListener;
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        this.mContext = context;

        (new AsyncTask<Void, Void, List<TimeoStopSchedule>>() {

            private Database mDatabase;
            private List<TimeoStop> mStopsToCheck;

            @Override
            protected List<TimeoStopSchedule> doInBackground(Void... params) {
                try {
                    mStopsToCheck = mDatabase.getWatchedStops();
                    return TimeoRequestHandler.getMultipleSchedules(mStopsToCheck);
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void onPreExecute() {
                mDatabase = new Database(DatabaseOpenHelper.getInstance(context));
                Log.d(Utils.TAG, "checking stop schedules for notifications");
            }

            @Override
            protected void onPostExecute(List<TimeoStopSchedule> stopSchedules) {
                if (stopSchedules != null) {

                    // Look through each schedule
                    for (TimeoStopSchedule schedule : stopSchedules) {

                        // If there are stops scheduled for this bus
                        if (schedule.getSchedules() != null && !schedule.getSchedules().isEmpty()) {
                            DateTime busTime = schedule.getSchedules().get(0).getScheduleTime();

                            updateStopTimeNotification(schedule);

                            // THE BUS IS COMIIIING
                            if (busTime.isBefore(DateTime.now().plus(ALARM_TIME_THRESHOLD_MS))) {
                                // Remove from database, and send a notification
                                notifyForIncomingBus(schedule);
                                mDatabase.stopWatchingStop(schedule.getStop());
                                schedule.getStop().setWatched(false);

                                Log.d(Utils.TAG, "less than two minutes till " + busTime.toString() + ": " + schedule.getStop());
                            } else if (schedule.getStop().getLastETA() != null) {
                                // Check if there's more than five minutes of difference between the last estimation and the new
                                // one. If that's the case, send the notification anyways; it may already be too late!

                                // This is to work around the fact that we actually can't know if a bus has passed already,
                                // we have to make assumptions instead; if a bus is announced for 3 minutes, and then for 10
                                // minutes the next time we check, it most likely has passed.

                                if (busTime.isBefore(schedule.getStop().getLastETA().plus(5 * 60 * 1000))) {
                                    // Remove from database, and send a notification
                                    notifyForIncomingBus(schedule);
                                    mDatabase.stopWatchingStop(schedule.getStop());
                                    schedule.getStop().setWatched(false);

                                    Log.d(Utils.TAG, "last time we saw " + schedule.getStop() + " the bus was scheduled for " +
                                            schedule.getStop().getLastETA() + ", but now the ETA is " + busTime + ", so we're " +
                                            "notifying");
                                }

                            } else {
                                mDatabase.updateWatchedStopETA(schedule.getStop(), busTime);
                            }
                        }
                    }
                } else {
                    // A network error occurred, or something ;-;
                    if (mStopsToCheck != null) {
                        NotificationManager notificationManager =
                                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                        for (TimeoStop stop : mStopsToCheck) {
                            notificationManager.cancel(Integer.valueOf(stop.getId()));
                        }
                    }

                    notifyNetworkError();
                }

                if (mDatabase.getWatchedStopsCount() == 0) {
                    NextStopAlarmReceiver.disable(context.getApplicationContext());
                }
            }

        }).execute();
    }

    /**
     * Sends a notification to the user informing them that their bus is incoming.
     *
     * @param schedule the schedule to notify about
     */
    private void notifyForIncomingBus(TimeoStopSchedule schedule) {
        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        ConfigurationManager config = new ConfigurationManager(mContext);

        Intent notificationIntent = new Intent(mContext, ActivityMain.class);
        PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0, notificationIntent, 0);

        // Get the data we need for the notification
        String stop = schedule.getStop().getName();
        String direction = schedule.getStop().getLine().getDirection().getName();
        String lineName = schedule.getStop().getLine().getName();
        String time = TimeFormatter.formatTime(mContext, schedule.getSchedules().get(0).getScheduleTime());

        // Make a nice notification to inform the user of the bus's imminence
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(mContext)
                        .setSmallIcon(R.drawable.ic_directions_bus_white)
                        .setContentTitle(mContext.getString(R.string.notif_watched_content_title, lineName))
                        .setContentText(mContext.getString(R.string.notif_watched_content_text, stop, direction))
                        .setStyle(new NotificationCompat.InboxStyle()
                                .addLine(mContext.getString(R.string.notif_watched_line_stop, stop))
                                .addLine(mContext.getString(R.string.notif_watched_line_direction, direction))
                                .setSummaryText(mContext.getString(R.string.notif_watched_summary_text, time)))
                        .setCategory(NotificationCompat.CATEGORY_EVENT)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setColor(mContext.getResources().getColor(R.color.icon_color))
                        .setContentIntent(contentIntent)
                        .setAutoCancel(true)
                        .setOnlyAlertOnce(true)
                        .setDefaults(NotificationSettings.getNotificationDefaults(mContext,
                                config.getWatchNotificationsVibrate(), config.getWatchNotificationsRing()));

        notificationManager.notify(Integer.valueOf(schedule.getStop().getId()), builder.build());

        // We want the rest of the application to know that this stop is not being watched anymore
        if (sWatchedStopStateListener != null) {
            sWatchedStopStateListener.onStopWatchingStateChanged(schedule.getStop(), false);
        }
    }

    /**
     * Sends a notification to the user and keeps it updated with the latest bus schedules.
     *
     * @param schedule the bus schedule that will be included in the notification
     */
    private void updateStopTimeNotification(TimeoStopSchedule schedule) {
        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        Intent notificationIntent = new Intent(mContext, ActivityMain.class);
        PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0, notificationIntent, 0);

        // Get the data we need for the notification
        String stop = schedule.getStop().getName();
        String direction = schedule.getStop().getLine().getDirection().getName();
        String lineName = schedule.getStop().getLine().getName();

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle()
                .setSummaryText(mContext.getString(R.string.notif_watched_content_text, lineName, direction))
                .setBigContentTitle(mContext.getString(R.string.stop_name, stop));

        for (TimeoSingleSchedule singleSchedule : schedule.getSchedules()) {
            inboxStyle.addLine(TimeFormatter.formatTime(mContext, singleSchedule.getScheduleTime()) + " - " + singleSchedule.getDirection());
        }

        // Make a nice notification to inform the user of the bus's imminence
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(mContext)
                        .setSmallIcon(R.drawable.ic_directions_bus_white)
                        .setContentTitle(mContext.getString(R.string.notif_ongoing_content_title, stop, lineName))
                        .setContentText(mContext.getString(R.string.notif_ongoing_content_text,
                                TimeFormatter.formatTime(mContext, schedule.getSchedules().get(0).getScheduleTime())))
                        .setStyle(inboxStyle)
                        .setCategory(NotificationCompat.CATEGORY_EVENT)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setColor(mContext.getResources().getColor(R.color.icon_color))
                        .setContentIntent(contentIntent)
                        .setOngoing(true);

        notificationManager.notify(Integer.valueOf(schedule.getStop().getId()), builder.build());
    }

    /**
     * Updates the schedule notification to the user, informing him that there's something wrong with the network.
     */
    private void notifyNetworkError() {
        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        Intent notificationIntent = new Intent(mContext, ActivityMain.class);
        PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0, notificationIntent, 0);

        // Make a nice notification to inform the user of an error
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(mContext)
                        .setSmallIcon(R.drawable.ic_directions_bus_white)
                        .setContentTitle(mContext.getString(R.string.notif_error_content_title))
                        .setContentText(mContext.getString(R.string.notif_error_content_text))
                        .setColor(mContext.getResources().getColor(R.color.icon_color))
                        .setCategory(NotificationCompat.CATEGORY_ERROR)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setContentIntent(contentIntent)
                        .setAutoCancel(true);

        notificationManager.notify(NOTIFICATION_ID_ERROR, builder.build());
    }

}
