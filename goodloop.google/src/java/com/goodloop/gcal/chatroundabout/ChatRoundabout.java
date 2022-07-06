package com.goodloop.gcal.chatroundabout;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.TimeZone;
import java.util.stream.Collectors;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

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
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.Period;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.time.TimeOfDay;
import com.winterwell.utils.time.TimeUtils;
import com.winterwell.web.ConfigException;
import com.winterwell.web.app.Emailer;
import com.winterwell.web.email.EmailConfig;
import com.winterwell.web.email.SimpleMessage;

/**
 * 121s
 * 
 * cross-office 121 on Friday at 11:30 Plus a within-team 121 on Friday at 11:00
 * for Tech
 * 
 * Now it will prevent same 121 pairs matched for two weeks
 * 
 * @version 1.1.0
 * @author daniel
 * @link ChatRoundaboutMain
 *
 */
public class ChatRoundabout {

	private static final String LOGTAG = "ChatRoundabout";
	static final String CHATSET_CROSS_TEAM = "cross-team";
	static final String CHATSET_IN_TEAM = "within-team";

	public void TODOremoveEventsByRegex() {

	}

	/**
	 * Convert staff.csv into an ArrayList of two items
	 */
	public List<Employee> emailList() {
		ArrayList<Employee> emailList = new ArrayList<>();

		CSVReader r = new CSVReader(new File("data/AllStaffList_20220651135153.csv"));
//		for (String[] row : r) {
//			if (row.length < 5) continue;
//			if ( ! (""+row[4]).equalsIgnoreCase("employee")) {
//				Log.d(LOGTAG, "skip non-employee "+row[0]);
//				continue;
//			}
//			String name = row[0];
//			String office = row[2];
//			String team = Containers.get(row, 5);
//
//			emailList.add(new Employee(name, office, team));
//		}
		for (String[] row : r) {
			if (row.length < 5)
				continue;
			else if (row[0].equals("Employee Id"))
				continue;
			String firstName = row[1];
			String lastName = row[2];
			String office = row[3].split(" ")[0];
			String team = row[4];

			emailList.add(new Employee(firstName, lastName, office, team));
		}
		Log.d(LOGTAG, "All employees: " + emailList);
		return emailList;
	}

	/**
	 * Check if the person is on holiday or already have 121 assigned
	 * 
	 * @param nextFriday date of the upcoming 121 event a.k.a next Friday
	 * @param chatSet    "team" or "cross-team"
	 * @return true if OK
	 */
	private boolean checkEvent(String email) {

		// Restrict events around the date of the meeting

		// Only getting events 1 days before and after next Friday to try to catch long
		// holidays but not getting too many event results
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
			if (period == null) {
				continue; // non-event - skip
			}
			if (period.first.isBefore(start)) {
				Log.e(LOGTAG, period + " " + event);
			}

			// TODO no repeat 121s - though the clash check will probably get that
			// ?? lets turn "chatroundabout" into a string constant (minor)
			if ((eventItem.contains("chatroundabout") || eventItem.contains("chatroundabout"))
					&& eventItem.contains(chatSet)) {
				Log.d(LOGTAG, email + " already has a 121: " + summary + " vs " + slot);
				return false;
			}

			// care with holidays (is this needed??) and TODO all day events??
			if (eventItem.contains("holiday")) {
				Period p2 = new Period(TimeUtils.getStartOfDay(period.first), TimeUtils.getEndOfDay(period.second));
				if (p2.intersects(slot)) {
					Log.d(LOGTAG, email + " has a Holiday Clash: " + summary + " vs " + slot);
					no121reasonForEmployeeEmail.put(email, "holiday: " + summary);
					return false;
				}
			}
			if (period.intersects(slot)) {
				if (period.isWholeDay()) {
					continue; // skipping whole day event
				}
				Log.d(LOGTAG, email + " has a Clash: " + summary + " " + period + " vs " + slot);
				no121reasonForEmployeeEmail.put(email, "clash: " + summary + " at " + period);
				return false;
			}
		}
		return true;
	}

	/**
	 * Check who is the 121 partner last week
	 * 
	 * @param email
	 * @param slot
	 * @return email address
	 */
	private String check121LastWeek(String email, Period slot) {
		// Only getting events 1 days before and after last Friday to try to catch long
		// holidays but not getting too many event results
		Time start = TimeUtils.getStartOfDay(slot.first.minus(8, TUnit.DAY));
		Time end = TimeUtils.getStartOfDay(slot.first.minus(6, TUnit.DAY));

		List<Event> allEvents = client().getEvents(email, start, end);

		for (Event event : allEvents) {
			String summary = event.getSummary();
			if (summary != null && summary.contains("SM")) {
				Log.d(summary);
			}

			String eventItem = event.toString().toLowerCase();

			if (eventItem.contains("chatroundabout")) {
				String[] summarySplit = summary.split(" ");
				String[] attendees = { summary.split(" ")[summarySplit.length - 1],
						summary.split(" ")[summarySplit.length - 3] };
				for (String staff : attendees) {
					if (!email.split("@")[0].equalsIgnoreCase(staff)) {
						return staff.toLowerCase() + "@good-loop.com";
					}
				}
			}

		}
		return null;
	}

	/**
	 * NB: the seed is fixed so that same day runs would produce the same output
	 */
	Random rand = new Random(TimeUtils.getStartOfDay(new Time()).getTime());

	/**
	 * Randomly generate 121 pairs between two office. Some people in the larger
	 * office will not have 121 event.
	 * 
	 * @param _smallOffice
	 * @param _largeOffice Can be the same as smallOffice
	 */
	private ArrayList<Pair<Employee>> getRandomPairs(List<Employee> _smallOffice, List<Employee> _largeOffice,
			Period slot) {
		// TODO make sure we hit every pairing
		// NB: defensive copy so we can edit locally
		ArrayList<Employee> largeOffice = new ArrayList(_largeOffice);
		ArrayList<Employee> smallOffice = new ArrayList(_smallOffice);
		ArrayList<Pair<Employee>> randomPairs = new ArrayList<>();

		Collections.shuffle(smallOffice, rand); // for fairness re being left out
		Collections.shuffle(largeOffice, rand);

		while (!smallOffice.isEmpty()) {
			Employee pairEmail = smallOffice.remove(0);
			largeOffice.remove(pairEmail);
			if (largeOffice.isEmpty()) {
				break; // last person can be left out for in-team
			}
			String lastWeekEmail = check121LastWeek(pairEmail.email, slot);
			if (lastWeekEmail != null && lastWeekEmail.equalsIgnoreCase(largeOffice.get(0).email)
					&& largeOffice.size() > 1) {
				Employee randomEmail = largeOffice.remove(1);
				smallOffice.remove(randomEmail);
				assert !pairEmail.equals(randomEmail);
				Pair pair = new Pair(pairEmail, randomEmail);
				randomPairs.add(pair);
				Log.d(LOGTAG, pairEmail.email + " had 121 events with " + largeOffice.get(0).email
						+ " last week, skipping pair to next person > " + randomEmail.email);
			} else {
				Employee randomEmail = largeOffice.remove(0);
				smallOffice.remove(randomEmail);
				assert !pairEmail.equals(randomEmail);
				Pair pair = new Pair(pairEmail, randomEmail);
				randomPairs.add(pair);

			}

		}

		Log.i(LOGTAG, "Poor guys who won't have 121 this week: " + largeOffice);
		for (Employee e : largeOffice) {
			assert !no121reasonForEmployeeEmail.containsKey(e.email);
			no121reasonForEmployeeEmail.put(e.email, "no partner this week");
		}

		return randomPairs;
	}

	/**
	 * Create a 1-to-1 event for a pair of users
	 * 
	 * @param chatSet
	 * @param email1  email of first attendee
	 * @param email2  email of second attendee
	 * @param date    Date of event
	 * @return event will use in addEvent method
	 * @throws IOException
	 */
	Event prepare121(Pair<Employee> ab, Period slot, String chatSet) throws IOException {
		System.out.println("Creating 121 event between " + ab);

		// Setting event details
		Event event = new Event()
				.setSummary("#ChatRoundabout " + chatSet + " 121 " + ab.first.getFirstName() + " <> "
						+ ab.second.getFirstName())
				.setDescription("Random short weekly chat between " + ab.first.name + " and " + ab.second.name
						+ ". Talk about anything you like.");

		EventDateTime start = GCalClient.toEventDateTime(slot.first);
		event.setStart(start);
		event.setEnd(GCalClient.toEventDateTime(slot.second));

		EventAttendee[] attendees = new EventAttendee[] {
				new EventAttendee().setEmail(ab.first.email).setResponseStatus("tentative"),
				new EventAttendee().setEmail(ab.second.email).setResponseStatus("tentative"), };
		event.setAttendees(Arrays.asList(attendees));

//		Don't need reminder as they're not to the attendees
//		EventReminder[] reminderOverrides = new EventReminder[] {
//		    new EventReminder().setMethod("email").setMinutes(10),
//		    new EventReminder().setMethod("popup").setMinutes(1),
//		};
//		Event.Reminders reminders = new Event.Reminders()
//		    .setUseDefault(false)
//		    .setOverrides(Arrays.asList(reminderOverrides));
//		event.setReminders(reminders);

		event.setGuestsCanModify(true);

		return event;
	}

	String run(Time nextFriday) throws IOException {

		// Get a list of email
		List<Employee> emailList = emailList();

		// Separate Edinburgh and London team into two list
		ArrayList<Employee> edinburghEmails = new ArrayList<>();
		ArrayList<Employee> londonEmails = new ArrayList<>();
		for (Employee i : emailList) {
			String office = i.office;
			// ?? Decide what to do with remote team (For now remote team are counted as
			// Edinburgh team)
			if ("London".equalsIgnoreCase(office)) {
				londonEmails.add(i);
			} else {
				edinburghEmails.add(i);
			}
		}

		// Cross team
		if (CHATSET_CROSS_TEAM.equals(chatSet)) {
			createCrossTeamEvents(nextFriday, londonEmails, edinburghEmails);
		} else if (CHATSET_IN_TEAM.equals(chatSet)) {
			// Within team
			createTeamEvents(nextFriday, edinburghEmails);
		} else {
			throw new IllegalArgumentException(chatSet);
		}

		// output
		// NB the extra whitespace is stripped in the log file but not in sysout
		String logString = new String();
		if (no121reasonForEmployeeEmail.size() == 0) {
			logString = "No 121s for " + chatSet + " at " + slot.first.format("hh:mm") + ":\n\n"
					+ "All Empolyees will have 121 in this slot";
		} else {
			logString = "No 121s for " + chatSet + " at " + slot.first.format("hh:mm") + ":\n\n"
					+ Printer.toString(no121reasonForEmployeeEmail, "\n", ":\t");
		}
		Log.i(LOGTAG, "\n\n" + logString + "\n\n");

		return logString;
	}

	final ChatRoundaboutConfig config;

	public ChatRoundabout(ChatRoundaboutConfig config, String chatSet) {
		this.config = config;
		this.chatSet = chatSet;
	}

	public Emailer getEmailer() {
		File propsFile = new File(FileUtils.getWinterwellDir(), config.emailProperties);
		Properties props = FileUtils.loadProperties(propsFile);

		EmailConfig ec = new EmailConfig();
		ec.emailServer = props.getProperty("emailServer").trim();
		ec.emailFrom = props.getProperty("emailFrom").trim();
		ec.emailPassword = props.getProperty("emailPassword").trim();
		ec.emailPort = 465;
		ec.emailSSL = true;

		return new Emailer(ec);
	}

	Map<String, String> no121reasonForEmployeeEmail = new HashMap();
	String chatSet;
	private Period slot;

	/**
	 * Send Report Email to confirm who and why did they don not have 121 events
	 * this Friday
	 * 
	 * @param emailContent
	 * @param nextFriday
	 * @param sendEmail    Receiver Email
	 * @throws AddressException
	 */
	public void sendEmail(String emailContent, Time nextFriday, String sendEmail) throws AddressException {
		Emailer emailer = getEmailer();

		InternetAddress from = emailer.getBotEmail();
//		InternetAddress email = emailer.getBotEmail();
		InternetAddress email = new InternetAddress(sendEmail);
		email.setAddress(sendEmail);
		String firstName = sendEmail.split("@")[0].toLowerCase();
		String firstNameCap = firstName.substring(0, 1).toUpperCase() + firstName.substring(1);

		String appName = "ChatRoundabout";
		String readableDate = nextFriday.toString().replace(" 00:00:00 GMT", "");
		String subject = appName + ": Weekly Report " + readableDate;
		StringBuilder body = new StringBuilder();
		body.append("Hello " + firstNameCap);
		if (config.reportOnly == true)
			body.append("\r\n\r\nReport Only Mode, no actual events were created.");
		body.append("\r\n\r\nChatRoundabout ran on: " + readableDate);
		body.append("\r\n\r\n" + emailContent);
		body.append("\r\n\r\nI am a bot, beep boop.");

		SimpleMessage msg = new SimpleMessage(from, email, subject, body.toString());
		emailer.send(msg);
	}

	public void sendNotification(String anotherEmail, Period slot, String sendEmail, String htmlLink)
			throws AddressException {
		File propsFile = new File(FileUtils.getWinterwellDir(), config.emailProperties);
		Emailer emailer = getEmailer();

		InternetAddress from = emailer.getBotEmail();
//		InternetAddress email = emailer.getBotEmail();
		InternetAddress email = new InternetAddress(sendEmail);
		email.setAddress(sendEmail);
		String firstName = sendEmail.split("@")[0].toLowerCase();
		String firstNameCap = firstName.substring(0, 1).toUpperCase() + firstName.substring(1);
		String personName = anotherEmail.split("@")[0].toLowerCase();
		String personNameCap = personName.substring(0, 1).toUpperCase() + personName.substring(1);

		String appName = "ChatRoundabout";
		String readableDate = slot.toString();
		String subject = appName + ": Notification";
		StringBuilder body = new StringBuilder();
		body.append("Hello " + firstNameCap);
		body.append("\r\n\r\nThis is an automated message to let you know that you have a Chat Roundabout meeting with "
				+ personNameCap + " from " + readableDate + ".");
		body.append("\r\n\r\nPlease accept the event on your calender here: " + htmlLink);
		body.append(
				"\r\n\r\nIf you're unable to make it, please contact your Chat Roundabout partner to let them know and arrange a new time slot for your 121.");
		body.append("\r\n\r\nEnjoy the rest of your day!");
		body.append("\r\n\r\nI am a bot, beep boop.");

		SimpleMessage msg = new SimpleMessage(from, email, subject, body.toString());
		emailer.send(msg);
	}

	private Period periodTimeZoneAdjust(Time start, Time end) {
		TimeZone tz = TimeZone.getDefault();
		int tzOffset = tz.getDSTSavings();
		if (tzOffset != 0) {
			Log.d(LOGTAG, "Timezone is not GMT, adjusting time slot by " + tzOffset / 3600000 + " hour(s).");
		}
		slot = new Period(start.minus(tzOffset, TUnit.MILLISECOND), end.minus(tzOffset, TUnit.MILLISECOND));
		return slot;
	}

	private void createCrossTeamEvents(Time nextFriday, List<Employee> londonEmails, List<Employee> edinburghEmails)
			throws IOException {
		Time s = config.crossTeamTime.set(nextFriday);
		Time e = s.plus(config.duration);
		slot = periodTimeZoneAdjust(s, e);
		// filter out people who cant make the slot
		londonEmails = Containers.filter(londonEmails, employee -> checkEvent(employee.email));
		edinburghEmails = Containers.filter(edinburghEmails, employee -> checkEvent(employee.email));

		// TODO fetch last weeks 121s

		System.out.println("Edinburgh's team size this Friday: " + edinburghEmails.size());
		System.out.println("London's team size this Friday: " + londonEmails.size());

		// Logic: which office is larger
		boolean e2l = (edinburghEmails.size() > londonEmails.size());

		// Random pairings
		List<Pair<Employee>> randomPairs = e2l ? getRandomPairs(londonEmails, edinburghEmails, slot)
				: getRandomPairs(edinburghEmails, londonEmails, slot);

		postEventsToCalendar(chatSet, slot, randomPairs);
	}

	private void postEventsToCalendar(String chatSet, Period slot, List<Pair<Employee>> randomPairs)
			throws IOException {
		// Make events
		for (Pair<Employee> ab : randomPairs) {
			Event preparedEvent = prepare121(ab, slot, chatSet);
			// Save events to Google Calendar, or just do a dry run?
			if (!config.reportOnly) {
				GCalClient gcc = client();
				Calendar person1 = gcc.getCalendar(ab.first.email);
				String calendarId = person1.getId(); // "primary";
				Event event2 = gcc.addEvent(calendarId, preparedEvent, false, true);
				Log.d(LOGTAG, "Saved event to Google Calendar: " + event2.toPrettyString());
				try {
					sendNotification(ab.first.email, slot, ab.second.email, event2.getHtmlLink());
					sendNotification(ab.second.email, slot, ab.first.email, event2.getHtmlLink());
				} catch (AddressException e) {
					e.printStackTrace();
				}
			} else {
				Printer.out("\nTESTING \nEvent: " + preparedEvent.getSummary() + "\nDescription: "
						+ preparedEvent.getDescription() + "\nTime: " + preparedEvent.getStart() + " - "
						+ preparedEvent.getEnd() + "\nAttendees: " + preparedEvent.getAttendees());
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
		slot = periodTimeZoneAdjust(s, e);
		// filter out people who cant make the slot
		edinburghEmails = Containers.filter(edinburghEmails, employee -> checkEvent(employee.email));

		// TODO fetch last weeks 121s

		// Random pairings
		List<Pair<Employee>> randomPairs = getRandomPairs(edinburghEmails, edinburghEmails, slot);

		postEventsToCalendar(chatSet, slot, randomPairs);

	}

}
