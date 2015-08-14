/*
 * Twistoast - StopsListFragment
 * Copyright (C) 2013-2015 Baptiste Candellier
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

package fr.outadev.twistoast;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.util.List;

import fr.outadev.android.timeo.IProgressListener;
import fr.outadev.android.timeo.TimeoStop;
import fr.outadev.twistoast.background.NextStopAlarmReceiver;

public class StopsListFragment extends Fragment implements IStopsListContainer {

	//Refresh automatically every 60 seconds.
	private static final long REFRESH_INTERVAL = 60000L;

	private final Handler periodicRefreshHandler = new Handler();
	private Runnable periodicRefreshRunnable;

	private AbsListView stopsListView;
	private SwipeRefreshLayout swipeRefreshLayout;
	private View noContentView;
	private FloatingActionButton fab;

	private List<TimeoStop> stops;

	private Database databaseHandler;
	private ArrayAdapterRealtime listAdapter;
	private boolean autoRefresh;

	private boolean isRefreshing;
	private boolean isInBackground;

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == 0 && resultCode == ActivityNewStop.STOP_ADDED) {
			refreshAllStopSchedules(true);
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// yes hello please, I'd like to be inflated?
		setHasOptionsMenu(true);

		databaseHandler = new Database(DatabaseOpenHelper.getInstance(getActivity()));

		periodicRefreshRunnable = new Runnable() {
			@Override
			public void run() {
				if(autoRefresh) {
					refreshAllStopSchedules(false);
				}
			}
		};

		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
		autoRefresh = sharedPref.getBoolean("pref_auto_refresh", true);

		isRefreshing = false;
		isInBackground = false;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		// Inflate the layout for this fragment
		View view = inflater.inflate(R.layout.fragment_realtime, container, false);

		// get pull to refresh view
		swipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.ptr_layout);
		swipeRefreshLayout.setOnRefreshListener(new OnRefreshListener() {

			@Override
			public void onRefresh() {
				refreshAllStopSchedules(false);
			}

		});

		swipeRefreshLayout.setColorSchemeResources(R.color.twisto_primary, R.color.twisto_secondary,
				R.color.twisto_primary, R.color.twisto_secondary);

		stopsListView = (AbsListView) view.findViewById(R.id.stops_list);
		noContentView = view.findViewById(R.id.view_no_content);

		fab = (FloatingActionButton) view.findViewById(R.id.fab);

		setupListeners();
		return view;
	}

	@Override
	public void onStart() {
		super.onStart();

		if(getView() != null) {
			final AdView adView = (AdView) getView().findViewById(R.id.adView);
			adView.setAdListener(new AdListener() {

				@Override
				public void onAdFailedToLoad(int errorCode) {
					adView.setVisibility(View.GONE);
					super.onAdFailedToLoad(errorCode);
				}

				@Override
				public void onAdLoaded() {
					adView.setVisibility(View.VISIBLE);
					super.onAdLoaded();
				}

			});

			if(getActivity().getResources().getBoolean(R.bool.enableAds)) {
				// if we want ads, check for availability and load them
				int hasGPS = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getActivity());

				if(hasGPS == ConnectionResult.SUCCESS) {
					AdRequest adRequest = new AdRequest.Builder()
							.addTestDevice(getString(R.string.admob_test_device)).build();
					adView.loadAd(adRequest);
				}
			} else {
				// if we don't want ads, remove the view from the layout
				adView.setVisibility(View.GONE);
			}
		}

		refreshAllStopSchedules(true);
	}

	private void setupListeners() {
		// Set up a long click listener for the main stops list that offers actions
		// to be realised on a single item
		stopsListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
				if(!isRefreshing) {
					final TimeoStop currentStop = listAdapter.getItem(position);

					// Menu items
					String contextualMenuItems[] = new String[]{
							getString(R.string.stop_action_delete),
							getString((!currentStop.isWatched()) ? R.string.stop_action_watch : R.string.stop_action_unwatch)
					};

					// Build the long click contextual menu
					AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
					builder.setItems(contextualMenuItems, new DialogInterface.OnClickListener() {

						public void onClick(DialogInterface dialog, int which) {

							if(which == 0) {
								deleteStopAction(currentStop, position);
							} else if(which == 1 && !currentStop.isWatched()) {
								startWatchingStopAction(currentStop);
							} else if(which == 1) {
								stopWatchingStopAction(currentStop);
							}
						}

					});

					builder.show();
				}

				return true;
			}

		});

		fab.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(getActivity(), ActivityNewStop.class);
				startActivityForResult(intent, 0);
			}

		});

		IWatchedStopChangeListener watchedStopStateListener = new IWatchedStopChangeListener() {

			@Override
			public void onStopWatchingStateChanged(TimeoStop dismissedStop, boolean watched) {
				if(dismissedStop == null) {
					return;
				}

				for(TimeoStop stop : stops) {
					if(dismissedStop.equals(stop)) {
						stop.setWatched(watched);
						listAdapter.notifyDataSetChanged();
					}
				}
			}

		};

		NextStopAlarmReceiver.setWatchedStopDismissalListener(watchedStopStateListener);
	}

	private void deleteStopAction(final TimeoStop stop, final int position) {
		// Remove from the database and the interface
		databaseHandler.deleteStop(stop);
		listAdapter.remove(stop);

		if(stop.isWatched()) {
			databaseHandler.stopWatchingStop(stop);
			stop.setWatched(false);
		}

		if(listAdapter.isEmpty()) {
			noContentView.setVisibility(View.VISIBLE);
		}

		listAdapter.notifyDataSetChanged();

		Snackbar.make(stopsListView, R.string.confirm_delete_success, Snackbar.LENGTH_LONG)
				.setAction(R.string.cancel_stop_deletion, new View.OnClickListener() {

					@Override
					public void onClick(View view) {
						Log.i(Utils.TAG, "restoring stop " + stop);

						databaseHandler.addStopToDatabase(stop);
						listAdapter.insert(stop, position);
						listAdapter.notifyDataSetChanged();
					}

				})
				.show();
	}

	private void startWatchingStopAction(final TimeoStop stop) {
		// We wish to get notifications about this upcoming stop
		databaseHandler.addToWatchedStops(stop);
		stop.setWatched(true);
		listAdapter.notifyDataSetChanged();

		// Turn the notifications on
		NextStopAlarmReceiver.enable(getActivity().getApplicationContext());

		Snackbar.make(stopsListView, getString(R.string.notifs_enable_toast, stop.getName()), Snackbar.LENGTH_LONG)
				.setAction(R.string.cancel_stop_deletion, new View.OnClickListener() {

					@Override
					public void onClick(View view) {
						databaseHandler.stopWatchingStop(stop);
						stop.setWatched(false);
						listAdapter.notifyDataSetChanged();

						// Turn the notifications back off if necessary
						if(databaseHandler.getWatchedStopsCount() == 0) {
							NextStopAlarmReceiver.disable(getActivity().getApplicationContext());
						}
					}

				})
				.show();
	}

	private void stopWatchingStopAction(TimeoStop stop) {
		// JUST STOP THESE NOTIFICATIONS ALREADY GHGHGHBLBLBL
		databaseHandler.stopWatchingStop(stop);
		stop.setWatched(false);
		listAdapter.notifyDataSetChanged();

		// Turn the notifications back off if necessary
		if(databaseHandler.getWatchedStopsCount() == 0) {
			NextStopAlarmReceiver.disable(getActivity().getApplicationContext());
		}

		NotificationManager notificationManager = (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(Integer.valueOf(stop.getId()));

		Snackbar.make(stopsListView, getString(R.string.notifs_disable_toast, stop.getName()), Snackbar.LENGTH_LONG)
				.show();
	}

	@Override
	public void onResume() {
		super.onResume();

		isInBackground = false;
		// when the activity is resuming, refresh
		refreshAllStopSchedules(false);
	}

	@Override
	public void onPause() {
		super.onPause();
		// when the activity is pausing, stop refreshing automatically
		Log.i(Utils.TAG, "stopping automatic refresh, app paused");
		isInBackground = true;
		periodicRefreshHandler.removeCallbacks(periodicRefreshRunnable);
	}

	/**
	 * Refreshes the list's schedules and displays them to the user.
	 *
	 * @param reloadFromDatabase true if we want to reload the stops completely, or false if we only want
	 *                           to update the schedules
	 */
	public void refreshAllStopSchedules(boolean reloadFromDatabase) {
		// we don't want to try to refresh if we're already refreshing (causes bugs)
		if(isRefreshing) {
			return;
		} else {
			isRefreshing = true;
		}

		// show the refresh animation
		swipeRefreshLayout.setRefreshing(true);

		// we have to reset the adapter so it correctly loads the stops
		// if we don't do that, bugs will appear when the database has been
		// modified
		if(reloadFromDatabase) {
			stops = databaseHandler.getAllStops();
			listAdapter = new ArrayAdapterRealtime(getActivity(), android.R.layout.simple_list_item_1, stops, this, stopsListView);
			stopsListView.setAdapter(listAdapter);
		}

		// finally, get the schedule
		listAdapter.updateScheduleData();
	}

	@Override
	public void endRefresh(boolean success) {
		// notify the pull to refresh view that the refresh has finished
		isRefreshing = false;
		swipeRefreshLayout.setRefreshing(false);

		noContentView.setVisibility((listAdapter.isEmpty()) ? View.VISIBLE : View.GONE);

		// reset the timer loop, and start it again
		// this ensures the list is refreshed automatically every 60 seconds
		periodicRefreshHandler.removeCallbacks(periodicRefreshRunnable);

		if(!isInBackground) {
			periodicRefreshHandler.postDelayed(periodicRefreshRunnable, REFRESH_INTERVAL);
		}

		if(success) {
			int mismatch = listAdapter.checkSchedulesMismatchCount();

			Log.i(Utils.TAG, "refreshed, " + listAdapter.getCount() + " stops in db");

			if(mismatch > 0) {
				Snackbar.make(stopsListView, R.string.stop_ref_update_message_title, Snackbar.LENGTH_LONG)
						.setAction(R.string.stop_ref_update_message_action, new View.OnClickListener() {

							@Override
							public void onClick(View view) {
								(new ReferenceUpdateTask()).execute();
							}

						})
						.show();
			}
		}
	}

	@Override
	public void loadFragmentForDrawerItem(int index) {
		((ActivityRealtime) getActivity()).loadFragmentForDrawerItem(index);
	}

	private class ReferenceUpdateTask extends AsyncTask<Void, Void, Exception> {

		private ProgressDialog dialog;
		private TimeoStopReferenceUpdater referenceUpdater;

		@Override
		protected Exception doInBackground(Void... params) {
			try {
				referenceUpdater.updateAllStopReferences(stops, new IProgressListener() {

					@Override
					public void onProgress(int current, int total) {
						dialog.setIndeterminate(false);
						dialog.setMax(total);
						dialog.setProgress(current);
					}

				});
			} catch(Exception e) {
				e.printStackTrace();
				return e;
			}

			return null;
		}

		@Override
		protected void onPreExecute() {
			referenceUpdater = new TimeoStopReferenceUpdater(getActivity());
			dialog = new ProgressDialog(getActivity());

			dialog.setTitle(R.string.stop_ref_update_progress_title);
			dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			dialog.setCancelable(false);
			dialog.setIndeterminate(true);
			dialog.show();
		}

		@Override
		protected void onPostExecute(Exception e) {
			dialog.hide();
			refreshAllStopSchedules(true);

			if(e != null) {
				Snackbar.make(stopsListView, R.string.stop_ref_update_error_text, Snackbar.LENGTH_LONG)
						.setAction(R.string.error_retry, new View.OnClickListener() {

							@Override
							public void onClick(View view) {
								(new ReferenceUpdateTask()).execute();
							}

						})
						.show();
			}
		}
	}

}
