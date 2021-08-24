package com.goodloop.gcal;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import org.junit.Test;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;
import com.winterwell.utils.Printer;
import com.winterwell.utils.Utils;

public class GCalClientTest_ChatRoundabout {
	
    /**
     * Create a 1-to-1 event for a pair of users
     * @param email1 email of first attendee
     * @param email2 email of second attendee
     * @param date Date of event
     * @return event will use in addEvent method
     * @throws IOException
     */
	public Event prepare121(String email1, String email2, LocalDate date) throws IOException {		
		System.out.println("Creating 121 event between " + email1 + "and " + email2);		
		
		String name1 = email1.split("@")[0].substring(0, 1).toUpperCase() + email1.split("@")[0].substring(1);
		String name2 = email2.split("@")[0].substring(0, 1).toUpperCase() + email2.split("@")[0].substring(1);
		
		// Setting event details
		Event event = new Event()
	    .setSummary("#Chat-Roundabout "+Utils.getNonce())
	    .setDescription("Random weekly chat between " + name1 + " and " + name2)
	    ;

		DateTime startDateTime = new DateTime(date.toString() + "T11:30:00.00Z");
		EventDateTime start = new EventDateTime()
		    .setDateTime(startDateTime)
		    .setTimeZone("GMT");
		event.setStart(start);

		DateTime endDateTime = new DateTime(date.toString() + "T11:40:00.00Z");
		EventDateTime end = new EventDateTime()
		    .setDateTime(endDateTime)
		    .setTimeZone("GMT");
		event.setEnd(end);

		EventAttendee[] attendees = new EventAttendee[] {
		    new EventAttendee().setEmail(email1)
		    	.setResponseStatus("tentative"),
		    new EventAttendee().setEmail(email2)
		    	.setResponseStatus("tentative"),
		};
		event.setAttendees(Arrays.asList(attendees));

		EventReminder[] reminderOverrides = new EventReminder[] {
		    new EventReminder().setMethod("email").setMinutes(10),
		    new EventReminder().setMethod("popup").setMinutes(1),
		};
		Event.Reminders reminders = new Event.Reminders()
		    .setUseDefault(false)
		    .setOverrides(Arrays.asList(reminderOverrides));
		event.setReminders(reminders);
		
		return event;
	}
	
	@Test
	public void testMake1to1() throws IOException {
		LocalDate nextFriday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
		System.out.println("Next Friday is: " + nextFriday);
		
		String email1 = "wing@good-loop.com";
		String email2 = "wing@good-loop.com";
		Event preparedEvent = prepare121(email1, email2, nextFriday);
		
		GCalClient gcc = new GCalClient();
		Calendar person1 = gcc.getCalendar(email1);
		String calendarId = person1.getId(); // "primary";
		Event event2 = gcc.addEvent(calendarId, preparedEvent, false, true);
		Printer.out("Saved event to Google Calendar: " + event2.toPrettyString());
	}
}
