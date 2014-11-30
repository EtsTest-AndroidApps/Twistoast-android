/*
 * Twistoast - TimeoStopSchedule
 * Copyright (C) 2013-2014  Baptiste Candellier
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

package fr.outadev.android.timeo;

import java.util.List;

/**
 * Used to store a list of schedules, with their corresponding line, direction, and stop
 * objects.
 *
 * @author outadoc
 */
public class TimeoStopSchedule {

	private TimeoStop stop;
	private List<TimeoSingleSchedule> schedules;

	/**
	 * Create a new schedule.
	 *
	 * @param stop      the stop this schedule corresponds to.
	 * @param schedules a list of the schedules the stop is associated with.
	 */
	public TimeoStopSchedule(TimeoStop stop, List<TimeoSingleSchedule> schedules) {
		this.stop = stop;
		this.schedules = schedules;
	}

	public TimeoStop getStop() {
		return stop;
	}

	public void setStop(TimeoStop stop) {
		this.stop = stop;
	}

	public List<TimeoSingleSchedule> getSchedules() {
		return schedules;
	}

	public void setSchedules(List<TimeoSingleSchedule> schedules) {
		this.schedules = schedules;
	}

	@Override
	public String toString() {
		return "TimeoStopSchedule{" +
				"stop=" + stop +
				", schedules=" + schedules +
				'}';
	}
}