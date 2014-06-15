package fr.outadev.android.timeo;

import java.util.Arrays;

/**
 * Used to store a schedule, with its corresponding line, direction, and stop
 * objects.
 * 
 * @author outadoc
 * 
 */
public class TimeoScheduleObject {

	public TimeoScheduleObject(TimeoIDNameObject line, TimeoIDNameObject direction, TimeoIDNameObject stop, String[] schedule) {
		this.line = line;
		this.direction = direction;
		this.stop = stop;
		this.schedule = schedule;
	}

	public TimeoIDNameObject getLine() {
		return line;
	}

	public void setLine(TimeoIDNameObject line) {
		this.line = line;
	}

	public TimeoIDNameObject getDirection() {
		return direction;
	}

	public void setDirection(TimeoIDNameObject direction) {
		this.direction = direction;
	}

	public TimeoIDNameObject getStop() {
		return stop;
	}

	public void setStop(TimeoIDNameObject stop) {
		this.stop = stop;
	}

	public String[] getSchedule() {
		return schedule;
	}

	public void setSchedule(String[] schedule) {
		this.schedule = schedule;
	}

	public String getMessageTitle() {
		return messageTitle;
	}

	public void setMessageTitle(String messageTitle) {
		this.messageTitle = messageTitle;
	}

	public String getMessageBody() {
		return messageBody;
	}

	public void setMessageBody(String messageBody) {
		this.messageBody = messageBody;
	}

	@Override
	public String toString() {
		if(messageTitle != null && messageBody != null) {
			return "TimeoScheduleObject [line=" + line + ", direction=" + direction + ", stop=" + stop + ", schedule="
			        + Arrays.toString(schedule) + ", messageTitle=" + messageTitle + ", messageBody=" + messageBody + "]";
		} else {
			return "TimeoScheduleObject [line=" + line + ", direction=" + direction + ", stop=" + stop + ", schedule="
			        + Arrays.toString(schedule) + "]";
		}

	}

	@Override
	public TimeoScheduleObject clone() {
		return new TimeoScheduleObject((line != null) ? line.clone() : null, (direction != null) ? direction.clone() : null,
		        (stop != null) ? stop.clone() : null, (schedule != null) ? schedule.clone() : null);
	}

	private TimeoIDNameObject line;
	private TimeoIDNameObject direction;
	private TimeoIDNameObject stop;

	private String[] schedule;

	private String messageTitle;
	private String messageBody;

}
