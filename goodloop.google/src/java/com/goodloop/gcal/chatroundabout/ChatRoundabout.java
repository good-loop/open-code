package com.goodloop.gcal.chatroundabout;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import com.winterwell.utils.Dep;
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
	
	private static final Boolean LIVE_MODE = true;
	private static final String LOGTAG = null;
	private static final String CHATSET_CROSS_TEAM = "cross-team";
	private static final String CHATSET_IN_TEAM = "within-team";

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

			// HACK: non-standard emails
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

			emailList.add(new Employee(email, name, office, team));
		}
		Log.d(LOGTAG, "All employees: "+emailList);
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
        
		List<Event> allEvents = client().getEvents(email, start, end);

		for (Event event : allEvents) {
			String summary = event.getSummary();
			if (summary != null && summary.contains("SM")) {
				Log.d(summary);
			}
			Boolean attending = client().isAttending(event, email);
			if (Boolean.FALSE.equals(attending)) {
				// ignore user not going
				continue;
			}
			// ignore event cancelled
			String eStatus = event.getStatus();
			if ("cancelled".equals(eStatus)) {
				continue;
			}
			String eventItem = event.toString().toLowerCase();
			
			// no clashes
			Period period = client().getPeriod(event);
			if (period==null) {
				continue; // non-event - skip
			}
			if (period.first.isBefore(start)) {
				Log.e(LOGTAG, period+" "+event);
			}
			if (period.intersects(slot)) {
				Log.d(LOGTAG, email+" has a Clash: "+summary+" "+period+" vs "+slot);
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
			
			if (period.isWholeDay()) {
				continue; // skipping whole day events (after checking if it is holiday)
			}
		}
		return true;
	}

	/**
	 * NB: the seed is fixed so that same day runs would produce the same output
	 */
	Random rand = new Random(TimeUtils.getStartOfDay(new Time()).getTime());

	/**
	 * Randomly generate 121 pairs between two office. Some people in the larger office will not have 121 event.
	 * @param _smallOffice
	 * @param _largeOffice Can be the same as smallOffice
	 */
	private ArrayList<Pair<Employee>> getRandomPairs(List<Employee> _smallOffice, List<Employee> _largeOffice) {
		// TODO make sure we hit every pairing
		// NB: defensive copy so we can edit locally
		ArrayList<Employee> largeOffice = new ArrayList(_largeOffice);
		ArrayList<Employee> smallOffice = new ArrayList(_smallOffice);
		ArrayList<Pair<Employee>> randomPairs = new ArrayList<>();
		
		Collections.shuffle(smallOffice, rand); // for fairness re being left out
		Collections.shuffle(largeOffice, rand);
		
		while( ! smallOffice.isEmpty()) {
			Employee pairEmail = smallOffice.remove(0);
			largeOffice.remove(pairEmail);
			if (largeOffice.isEmpty()) {
				break; // last person can be left out for in-team
			}
			Employee randomEmail = largeOffice.remove(0);
			smallOffice.remove(randomEmail);
			assert ! pairEmail.equals(randomEmail);
			Pair pair = new Pair(pairEmail, randomEmail);
			randomPairs.add(pair);
		}
		
		Log.i(LOGTAG, "Poor guys who won't have 121 this week: "+largeOffice);
		
		return randomPairs;
	}
    
    /**
     * Create a 1-to-1 event for a pair of users
     * @param chatSet 
     * @param email1 email of first attendee
     * @param email2 email of second attendee
     * @param date Date of event
     * @return event will use in addEvent method
     * @throws IOException
     */
	Event prepare121(Pair<Employee> ab, Period slot, String chatSet) throws IOException {		
		System.out.println("Creating 121 event between " + ab);		
		
		// Setting event details
		Event event = new Event()
	    .setSummary("#ChatRoundabout "+chatSet+" 121 "+ab.first.getFirstName()+" <> "+ab.second.getFirstName())
	    .setDescription("Random short weekly chat between " + ab.first.name + " and " + ab.second.name+". Talk about anything you like.")
	    ;
		
		EventDateTime start = GCalClient.toEventDateTime(slot.first);
		event.setStart(start);
		event.setEnd(GCalClient.toEventDateTime(slot.second));

		EventAttendee[] attendees = new EventAttendee[] {
		    new EventAttendee().setEmail(ab.first.email)
		    	.setResponseStatus("tentative"),
		    new EventAttendee().setEmail(ab.second.email)
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
		
		event.setGuestsCanModify(true);
		
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
			if ("London".equalsIgnoreCase(office)) {
				londonEmails.add(i);
			} else {
				edinburghEmails.add(i);
			}
		}
		
		// Cross team
		createCrossTeamEvents(nextFriday, londonEmails, edinburghEmails);
		
		
		// Within team
		createTeamEvents(nextFriday, edinburghEmails);
	}

	final ChatRoundaboutConfig config;
	
	public ChatRoundabout(ChatRoundaboutConfig config) {
		this.config = config;
	}
	
	private void createCrossTeamEvents(Time nextFriday, List<Employee> londonEmails, List<Employee> edinburghEmails) throws IOException {
		Time s = config.crossTeamTime.set(nextFriday);
		Time e = s.plus(config.duration);
		Period slot = new Period(s, e);
		String chatSet = CHATSET_CROSS_TEAM;
		// filter out people who cant make the slot
		londonEmails = Containers.filter(londonEmails, employee -> checkEvent(employee.email, chatSet, slot));
		edinburghEmails = Containers.filter(edinburghEmails, employee -> checkEvent(employee.email, chatSet, slot));
		
		// TODO fetch last weeks 121s
		
		System.out.println("Edinburgh's team size today: " + edinburghEmails.size());
		System.out.println("London's team size today: " + londonEmails.size());
		
		// Logic: which office is larger
		boolean e2l = (edinburghEmails.size() > londonEmails.size());
		
		// Random pairings
		List<Pair<Employee>> randomPairs = e2l ? getRandomPairs(londonEmails, edinburghEmails) : getRandomPairs(edinburghEmails, londonEmails);
		
		postEventsToCalendar(chatSet, slot, randomPairs);
	}

	

	private void postEventsToCalendar(String chatSet, Period slot, List<Pair<Employee>> randomPairs) throws IOException {
		// Make events
		for (Pair<Employee> ab : randomPairs) {
			Event preparedEvent = prepare121(ab, slot, chatSet);			
			// Save events to Google Calendar, or just do a dry run?
			if (LIVE_MODE) {
				GCalClient gcc = client();
				Calendar person1 = gcc.getCalendar(ab.first.email);
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

	private GCalClient client() {
		GCalClient client = Dep.setIfAbsent(GCalClient.class, new GCalClient());
		return client;
	}

	private void createTeamEvents(Time nextFriday, List<Employee> edinburghEmails) throws IOException {
		Time s = config.inTeamTime.set(nextFriday);
		Time e = s.plus(config.duration);
		Period slot = new Period(s, e);
		String chatSet = CHATSET_IN_TEAM;
		// filter out people who cant make the slot
		edinburghEmails = Containers.filter(edinburghEmails, employee -> checkEvent(employee.email, chatSet, slot));
		
		// TODO fetch last weeks 121s
		
		// Random pairings
		List<Pair<Employee>> randomPairs = getRandomPairs(edinburghEmails, edinburghEmails);
		

		postEventsToCalendar(chatSet, slot, randomPairs);
		
	}

}
