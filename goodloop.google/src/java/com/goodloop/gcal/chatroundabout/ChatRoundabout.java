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
import com.winterwell.utils.io.CSVReader;

public class ChatRoundabout  {
	
	private static final Boolean LIVE_MODE = false;

	public static void main(String[] args) throws IOException {
		new ChatRoundabout().run();
	}
	
	/**
	 * Convert staff.csv into an ArrayList of two items
	 * @return email + "\t" + office
	 */
	private static ArrayList<String> emailList() {
		ArrayList<String> emailList = new ArrayList<String>();
		
		CSVReader r = new CSVReader(new File("data/staff.csv"));
		for (String[] row : r) {
			if (row[4].equals("Employee")) {
				String name = row[0];
				String office = row[2];
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
				emailList.add(email + "\t" + office);
			}
		}
		return emailList;
	}
	
	/**
	 * return a full eventName String from teamMode
	 * @param teamMode
	 * @return Full eventName String
	 */
	private String team2EventName(String teamMode) {
		// teamMode to event name
		String eventName = new String();
		switch (teamMode) {
		case "el":
			eventName = "Edinburgh/London";
			break;
		case "ed":
			eventName = "Edinburgh";
			break;
		case "cs":
			eventName = "CS";
			break;
		default:
			System.out.println("Error: Wrong teamMode");
			eventName = "";
			break;
		}
		
		return eventName;
	}
	
	/**
	 * Check if anyone inside of the ArrayList is on holiday on next Friday or already have 121 assigned
	 * @param emailList the emailList returned from emailList() in (email + "\t" + office) format
	 * @param nextFriday date of the upcoming 121 event a.k.a next Friday
	 * @param eventName 
	 * @return filtered emailList of same format
	 */
	private ArrayList<String> checkEvent(ArrayList<String> emailList, LocalDate nextFriday, String eventName) {
		// Restrict events around the date of the meeting
		
		// Only getting events 1 days before and after next Friday to try to catch long holidays but not getting too many event results		
		String startString = nextFriday.minusDays(1).atStartOfDay().format(DateTimeFormatter.ISO_DATE_TIME);
        DateTime start = DateTime.parseRfc3339(startString);
    	String endString = nextFriday.plusDays(1).atStartOfDay().format(DateTimeFormatter.ISO_DATE_TIME);
        DateTime end = DateTime.parseRfc3339(endString);
		
		GCalClient client = new GCalClient();
		
		ArrayList<String> filterOutEmail = new ArrayList<String>();
		for (String i : emailList) {
			String email = i.split("\t")[0];
			String office = i.split("\t")[1];
			System.out.println(email);
			
			List<Event> allEvents = client.getEvents(email, start, end);
			for (Event event : allEvents) {
				String eventItem = event.toString().toLowerCase();
				
				if (eventItem.contains("holiday") || eventItem.contains("#chat-roundabout #" + eventName.toLowerCase())) {
					
					boolean formatIsDate = event.getStart().getDate() != null;
					LocalDate startDate = (formatIsDate ? LocalDate.parse(event.getStart().getDate().toString()) : LocalDate.parse(event.getStart().getDateTime().toString().substring(0, 10)));
					LocalDate endDate = (formatIsDate ? LocalDate.parse(event.getEnd().getDate().toString()) : LocalDate.parse(event.getEnd().getDateTime().toString().substring(0, 10)).plusDays(1));
					
					List<LocalDate> filterDays = startDate.datesUntil(endDate).collect(Collectors.toList());
					System.out.println("filterDays: " + filterDays);

					if (filterDays.contains(nextFriday)) {
						System.out.println(email + " is in hoilday or already have 121.");
						if ( !filterOutEmail.contains(email + "\t" + office) ) { 
							filterOutEmail.add(email + "\t" + office); 
						}
					}
				}
			}
		}
		return filterOutEmail;
	}
	
	/**
	 * Check if anyone inside of the ArrayList have 121 event last Friday
	 * @param emailList the emailList returned from emailList() in (email + "\t" + office) format
	 * @param lastFriday date of the last 121 event a.k.a last Friday
	 * @param eventName
	 * @return filtered emailList of same format
	 */
	private ArrayList<String> checkEventLF(ArrayList<String> emailList, LocalDate lastFriday, String eventName) {
		// Last Friday
		DateTime startLF = DateTime.parseRfc3339(lastFriday.atStartOfDay().format(DateTimeFormatter.ISO_DATE_TIME));
		DateTime endLF = DateTime.parseRfc3339(lastFriday.plusDays(1).atStartOfDay().format(DateTimeFormatter.ISO_DATE_TIME));
		
		GCalClient client = new GCalClient();
		
		ArrayList<String> no121LFEmail = new ArrayList<String>();
		
		for (String i : emailList) {
			String email = i.split("\t")[0];
			String office = i.split("\t")[1];
			System.out.println(email);
			boolean no121LF = true;
			
			List<Event> allEventsLF = client.getEvents(email, startLF, endLF);
			for (Event event : allEventsLF) {
				if (event.get("summary") != null) {
					if (event.get("summary").toString().toLowerCase().contains("#chat-roundabout #" + eventName.toLowerCase())) {
						System.out.println(event.get("summary"));
						
						boolean formatIsDate = event.getStart().getDate() != null;
						LocalDate startDate = (formatIsDate ? LocalDate.parse(event.getStart().getDate().toString()) : LocalDate.parse(event.getStart().getDateTime().toString().substring(0, 10)));
						LocalDate endDate = (formatIsDate ? LocalDate.parse(event.getEnd().getDate().toString()) : LocalDate.parse(event.getEnd().getDateTime().toString().substring(0, 10)).plusDays(1));
						
						List<LocalDate> have121Days = startDate.datesUntil(endDate).collect(Collectors.toList());
						if (have121Days.contains(lastFriday)) {
							no121LF = false;
						}
					}
				}
			}
			if (no121LF) {
				System.out.println(email + " have no 121 last Friday.");
				if ( !no121LFEmail.contains(email + "\t" + office) ) { 
					no121LFEmail.add(email + "\t" + office); 
				}
			}
		}
		return no121LFEmail;
	}
	
	/**
	 * Randomly generate 121 pairs between two office. Some people in the larger office will not have 121 event.
	 * @param edinburghEmails ArrayList of email of the Edinburgh team
	 * @param londonEmails of email of the London team
	 * @return An ArrayList of an ArrayList: a pair = [staff from small office, staff from large office]
	 */
	private ArrayList<ArrayList<String>> getRandomPairsBF(ArrayList<String> edinburghEmails, ArrayList<String> londonEmails, boolean e2l) {
		
		ArrayList<ArrayList<String>> randomPairs = new ArrayList<ArrayList<String>>();
		
		if (!e2l) {
			Random rand = new Random();
			String missOffice = "London";
			
			for (String pairEmail : edinburghEmails) {
				if (londonEmails.size() != 0) {
					String randomEmail = londonEmails.get(rand.nextInt(londonEmails.size()));
					londonEmails.remove(randomEmail);
					ArrayList<String> pair = new ArrayList<String>();
					pair.add(pairEmail);
					pair.add(randomEmail);
					randomPairs.add(pair);
				}
			}
			
			for (String missEmail : londonEmails) {
				if (!e2l) {
					emailList.add(missEmail + "\t" + missOffice);
				}
			}
		} else {
			Random rand = new Random();

			String missOffice = "Edinburgh";
			
			for (String pairEmail : londonEmails) {
				if (edinburghEmails.size() != 0) {
					String randomEmail = londonEmails.get(rand.nextInt(londonEmails.size()));
					londonEmails.remove(randomEmail);
					ArrayList<String> pair = new ArrayList<String>();
					pair.add(pairEmail);
					pair.add(randomEmail);
					randomPairs.add(pair);
				}
			}
			
			for (String missEmail : edinburghEmails) {
				if (e2l) {
					emailList.add(missEmail + "\t" + missOffice);
				}
			}
		}
		
		
		return randomPairs;
	}
    
    /**
     * Create a 1-to-1 event for a pair of users
     * @param email1 email of first attendee
     * @param email2 email of second attendee
     * @param date of event
     * @param teamMode el (Edinburgh/London), ed (Edinburgh), cs
     * @return event will use in addEvent method
     * @throws IOException
     */
	public Event prepare121(String email1, String email2, LocalDate date, String teamMode) throws IOException {		
		System.out.println("\nCreating 121 event between " + email1 + "and " + email2);		
		
		// Setting event time
		String startTime = new String();
		String endTime = new String();
		switch (teamMode) {
			case "el":
				startTime = "T11:30:00.00Z";
				endTime = "T11:40:00.00Z";
				break;
			case "ed":
				startTime = "T11:00:00.00Z";
				endTime = "T11:10:00.00Z";
				break;
			case "cs":
				startTime = "T11:00:00.00Z";
				endTime = "T11:10:00.00Z";
				break;
			default:
				System.out.println("Error: Wrong teamMode. Falling back to default event startTime T11:30:00.00Z");
				startTime = "T11:30:00.00Z";
				endTime = "T11:40:00.00Z";
				break;
		}
		
		String name1 = email1.split("@")[0].substring(0, 1).toUpperCase() + email1.split("@")[0].substring(1);
		String name2 = email2.split("@")[0].substring(0, 1).toUpperCase() + email2.split("@")[0].substring(1);
		
		// Setting event details
		Event event = new Event()
	    .setSummary("#Chat-Roundabout "+Utils.getNonce())
	    .setDescription("Random weekly chat between " + name1 + " and " + name2)
	    ;

		DateTime startDateTime = new DateTime(date.toString() + startTime);
		EventDateTime start = new EventDateTime()
		    .setDateTime(startDateTime)
		    .setTimeZone("GMT");
		event.setStart(start);

		DateTime endDateTime = new DateTime(date.toString() + endTime);
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

	public static ArrayList<String> emailList = emailList();
	
	void run() throws IOException {
		// TODO run both "el" and "ed" teammode
		String teamMode = "el";
		
		// Get 121 Date (Next Friday)
		LocalDate nextFriday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
		System.out.println("Next Friday is: " + nextFriday);
		LocalDate lastFriday = LocalDate.now().with(TemporalAdjusters.previous(DayOfWeek.FRIDAY));
		System.out.println("Last Friday is: " + lastFriday);
		
		// Filter out if on holiday or already have a 121/ did not have 121 last week
		for (String holidayEmails : checkEvent(emailList, nextFriday, team2EventName(teamMode))) {
			emailList.remove(holidayEmails);
		}
		
		// Take priorityList out from emailList
		ArrayList<String> priorityList = new ArrayList<String>();
		for (String no121LFEmails : checkEventLF(emailList, lastFriday, team2EventName(teamMode))) {
			priorityList.add(no121LFEmails);
		}
		
		emailList.removeAll(priorityList);
		
		// Separate Edinburgh and London team into two list
		ArrayList<String> edinburghEmails = new ArrayList<String>();
		ArrayList<String> londonEmails = new ArrayList<String>();
		
		for (String i : priorityList) {
			String email = i.split("\t")[0];
			String office = i.split("\t")[1];
			// TODO Decide what to do with remote team (For now remote team are counted as Edinburgh team)
			if (office.equals("London")) {
				londonEmails.add(email);
			} else {
				edinburghEmails.add(email);
			}
		}
		
		System.out.println("Edinburgh's team size of priorityList: " + edinburghEmails.size());
		System.out.println("London's team size of priorityList: " + londonEmails.size());
		
		// Logic: which office is larger
		boolean e2l = (edinburghEmails.size() > londonEmails.size());
		
		// Random pairings between two offices
		ArrayList<ArrayList<String>> randomPairs = new ArrayList<ArrayList<String>>();
		randomPairs.addAll(getRandomPairsBF(edinburghEmails, londonEmails, e2l));
		
		// PriorityList is done, do it again with main emailList
		// TODO make function to stop repeating
		edinburghEmails = new ArrayList<String>();
		londonEmails = new ArrayList<String>();
		
		for (String i : emailList) {
			String email = i.split("\t")[0];
			String office = i.split("\t")[1];
			if (office.equals("London")) {
				londonEmails.add(email);
			} else {
				edinburghEmails.add(email);
			}
		}
		System.out.println(randomPairs);
		
		System.out.println("Edinburgh's team size of emailList: " + edinburghEmails.size());
		System.out.println("London's team size of emailList: " + londonEmails.size());
		e2l = (edinburghEmails.size() > londonEmails.size());
		randomPairs.addAll(getRandomPairsBF(edinburghEmails, londonEmails, e2l));
		
		System.out.println(randomPairs);
		
		// Make events
		for (ArrayList<String> pair : randomPairs) {
			String email1 = pair.get(0);
			String email2 = pair.get(1);
			Event preparedEvent = prepare121(email1, email2, nextFriday, teamMode);
			
			// Save events to Google Calendar, or just do a dry run?
			if (LIVE_MODE) {
				GCalClient gcc = new GCalClient();
				Calendar person1 = gcc.getCalendar(email1);
				String calendarId = person1.getId(); // "primary";
				Event event2 = gcc.addEvent(calendarId, preparedEvent, false, true);
				Printer.out("Saved event to Google Calendar: " + event2.toPrettyString());
			} else {
				Printer.out("TESTING \nEvent: " + preparedEvent.getSummary() +
						"\nDescription: " + preparedEvent.getDescription() + 
						"\nTime: " + preparedEvent.getStart() + " - " + preparedEvent.getEnd() +
						"\nAttendees: " + preparedEvent.getAttendees()
				);
			}
		}
	}
	
}
