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
	
	public void make121(String email1, Event event, boolean test ) throws IOException {
		GCalClient gcc = new GCalClient();
		Calendar person1 = gcc.getCalendar(email1);
		
		if (test) {
			Printer.out("\nTESTING \nEvent: " + event.getSummary() + "\nDescription: " + event.getDescription() + 
					"\nTime: " + event.getStart() + event.getEnd() + "\nAttendess: " + event.getAttendees());
		} else {
			String calendarId = person1.getId(); // "primary";
			Event event2 = gcc.addEvent(calendarId, event, false, true);
			Printer.out(event2.toPrettyString());
		}
	}
	
	
	@Test
	public void testMake1to1() throws IOException {
		LocalDate nextFriday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
		System.out.println("Next Friday is: " + nextFriday);
		
		// Manually prepare 121 for testing
		String email1 = "wing@good-loop.com";
		String email2 = "daniel@good-loop.com";
		
		System.out.println("Creating 121 event between " + email1 + "and " + email2);		
		
		String name1 = email1.split("@")[0].substring(0, 1).toUpperCase() + email1.split("@")[0].substring(1);
		String name2 = email2.split("@")[0].substring(0, 1).toUpperCase() + email2.split("@")[0].substring(1);
		
		// Setting event details
		Event event = new Event()
	    .setSummary("FOR TEST Please Kindly Ignore #Chat-Roundabout "+Utils.getNonce())
	    .setDescription("For test only/ Random weekly chat between " + name1 + " and " + name2)
	    ;

		DateTime startDateTime = new DateTime(nextFriday.toString() + "T11:30:00.00Z");
		EventDateTime start = new EventDateTime()
		    .setDateTime(startDateTime)
		    .setTimeZone("GMT");
		event.setStart(start);

		DateTime endDateTime = new DateTime(nextFriday.toString() + "T11:40:00.00Z");
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
		
		// Creating the event
		make121(email1, event, false);
	}
}
