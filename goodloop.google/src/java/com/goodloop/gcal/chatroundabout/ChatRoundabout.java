package com.goodloop.gcal.chatroundabout;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import com.goodloop.gcal.GCalClient;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;
import com.winterwell.utils.Printer;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.containers.Pair;
import com.winterwell.utils.io.CSVReader;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.Period;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.time.TimeOfDay;
import com.winterwell.utils.time.TimeUtils;

/**
 * 121s
 * 
 * cross-office 121 on Friday at 11:30
 * Plus a within-team 121 on Friday at 11:00 for Tech
 * 
 * @author daniel
 *
 */
public class ChatRoundabout  {
	
	private static final Boolean LIVE_MODE = false;
	private static final String LOGTAG = null;

	public static void main(String[] args) throws IOException {
		new ChatRoundabout().run();
	}
	
	public void TODOremoveEventsByRegex() {
		
	}
	
	/**
	 * Convert staff.csv into an ArrayList of two items
	 */
	private List<Employee> emailList() {
		ArrayList<Employee> emailList = new ArrayList<>();
		
		CSVReader r = new CSVReader(new File("data/staff.csv"));
		for (String[] row : r) {
			if (row.length < 5) continue;
			if ( ! (""+row[4]).equalsIgnoreCase("employee")) {
				Log.d(LOGTAG, "skip non-employee "+row[0]);
				continue;
			}
			String name = row[0];
			String office = row[2];
			String team = Containers.get(row, 5);
			// Catch Dan A
			String firstName;
			if (name.equals("Daniel Appel")) {
				firstName = "sysadmin";
			} else if (name.equals("Natasha Taylor")) {
				firstName = "tash";
			} else if (name.equals("Abdikarim Mohamed")) {
				firstName = "karim";
			} else {
				firstName = name.split(" ")[0];
			}
			String email = firstName.toLowerCase()+"@good-loop.com";
			emailList.add(new Employee(email, firstName, office, team));
		}
		return emailList;
	}
	
	/**
	 * Check if the person is on holiday or already have 121 assigned
	 * @param nextFriday date of the upcoming 121 event a.k.a next Friday
	 * @param chatSet "team" or "cross-team"
	 * @return true if OK
	 */
	private boolean checkEvent(String email, String chatSet, Period slot) {
		
		// Restrict events around the date of the meeting
		
		// Only getting events 1 days before and after next Friday to try to catch long holidays but not getting too many event results		
		Time start = TimeUtils.getStartOfDay(slot.first.minus(TUnit.DAY));
		Time end = TimeUtils.getStartOfDay(slot.first.plus(TUnit.DAY));
        
		GCalClient client = new GCalClient();
		List<Event> allEvents = client.getEvents(email, start, end);

		for (Event event : allEvents) {
			if (client.isAttending(event, email) == false) {
				// ignore cancelled
				continue;
			}
			String eventItem = event.toString().toLowerCase();
			
			// no clashes
			Period period = client.getPeriod(event);
			if (period.intersects(slot)) {
				Log.d(LOGTAG, "Clash: "+event+" vs "+slot);
				return false;
			}
			
			// TODO no repeat 121s - though the clash check will probably get that
			if (eventItem.contains("chat-roundabout") && eventItem.contains(chatSet)) {
				Log.d(LOGTAG, "Already has a 121: "+event+" vs "+slot);
				return false;
			}
			
			// care with holidays (is this needed??) and TODO all day events??			
			if (eventItem.contains("holiday")) {
				Period p2 = new Period(TimeUtils.getStartOfDay(period.first), TimeUtils.getEndOfDay(period.second));
				if (p2.intersects(slot)) {
					Log.d(LOGTAG, "Holiday Clash: "+event+" vs "+slot);
					return false;
				}
			}
		}
		return true;
	}
	
	/**
	 * Randomly generate 121 pairs between two office. Some people in the larger office will not have 121 event.
	 * @param smallOffice ArrayList of email of the smaller team
	 * @param largeOfficeArrayList of email of the larger team
	 * @return An ArrayList of an ArrayList: a pair = [staff from small office, staff from large office]
	 */
	private ArrayList<Pair<Employee>> getRandomPairs(List<Employee> smallOffice, List<Employee> _largeOffice) {
		// TODO make sure we hit every pairing
		// NB: defensive copy so we can edit locally
		ArrayList<Employee> largeOffice = new ArrayList(_largeOffice);
		ArrayList<Pair<Employee>> randomPairs = new ArrayList<>();
		Random rand = new Random();
		
		for (Employee pairEmail : smallOffice) {
			Employee randomEmail = largeOffice.get(rand.nextInt(largeOffice.size()));
			largeOffice.remove(randomEmail);
			Pair pair = new Pair(pairEmail, randomEmail);
			randomPairs.add(pair);
		}
		
		Log.i(LOGTAG, "Poor guys who won't have 121 this week: "+largeOffice);
		
		return randomPairs;
	}
    
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

	
	void run() throws IOException {
		
		// Get a list of email
		List<Employee> emailList = emailList();
		
		// Get 121 Date (Next Friday)
		LocalDate _nextFriday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
		Time nextFriday = new Time(_nextFriday.toString());
		System.out.println("Next Friday is: " + nextFriday);
						
		// Separate Edinburgh and London team into two list
		ArrayList<Employee> edinburghEmails = new ArrayList<>();
		ArrayList<Employee> londonEmails = new ArrayList<>();		
		for (Employee i : emailList) {
			String office = i.office;
			// ?? Decide what to do with remote team (For now remote team are counted as Edinburgh team)
			if (office.equals("London")) {
				londonEmails.add(i);
			} else {
				edinburghEmails.add(i);
			}
		}
		
		// Cross team
		pairings = createCrossTeamEvents(nextFriday, londonEmails, edinburghEmails);
		
		// Within team
		createTeamEvents(edinburghEmails);
	}

	ChatRoundaboutConfig config;
	
	private void createCrossTeamEvents(Time nextFriday, List<Employee> londonEmails, List<Employee> edinburghEmails) {
		Time s = config.crossTeamTime.set(nextFriday);
		Time e = s.plus(config.duration);
		Period slot = new Period(s, e);
		String chatSet = "cross-team";
		// filter out people who cant make the slot
		londonEmails = Containers.filter(londonEmails, employee -> checkEvent(employee.email, chatSet, slot));
		edinburghEmails = Containers.filter(edinburghEmails, employee -> checkEvent(employee.email, chatSet, slot));
		
		// TODO fetch last weeks 121s
		
		System.out.println("Edinburgh's team size today: " + edinburghEmails.size());
		System.out.println("London's team size today: " + londonEmails.size());
		
		// Logic: which office is larger
		boolean e2l = (edinburghEmails.size() > londonEmails.size());
		
		// Random pairings
		ArrayList<ArrayList<String>> randomPairs = e2l ? getRandomPairs(londonEmails, edinburghEmails) : getRandomPairs(edinburghEmails, londonEmails);
		
		System.out.println(randomPairs);
		return randomPairs;
		
		// Make events
		for (ArrayList<String> pair : randomPairs) {
			String email1 = pair.get(0);
			String email2 = pair.get(1);
			Event preparedEvent = prepare121(email1, email2, nextFriday);
			
			// Save events to Google Calendar, or just do a dry run?
			if (LIVE_MODE) {
				GCalClient gcc = new GCalClient();
				Calendar person1 = gcc.getCalendar(email1);
				String calendarId = person1.getId(); // "primary";
				Event event2 = gcc.addEvent(calendarId, preparedEvent, false, true);
				Printer.out("Saved event to Google Calendar: " + event2.toPrettyString());
			} else {
				Printer.out("\nTESTING \nEvent: " + preparedEvent.getSummary() +
						"\nDescription: " + preparedEvent.getDescription() + 
						"\nTime: " + preparedEvent.getStart() + " - " + preparedEvent.getEnd() +
						"\nAttendees: " + preparedEvent.getAttendees()
				);
			}
		}

	}
}