package com.goodloop.gcal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.Calendar.CalendarList;
import com.google.api.services.calendar.Calendar.Calendars;
import com.google.api.services.calendar.Calendar.Calendars.Get;
import com.google.api.services.calendar.Calendar.Events.Insert;
import com.google.api.services.calendar.Calendar.Events.Instances;
import com.google.api.services.calendar.Calendar.Events.Patch;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.ConferenceData;
import com.google.api.services.calendar.model.CreateConferenceRequest;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.CellFormat;
import com.google.api.services.sheets.v4.model.Color;
import com.google.api.services.sheets.v4.model.ExtendedValue;
import com.google.api.services.sheets.v4.model.GridCoordinate;
import com.google.api.services.sheets.v4.model.GridProperties;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Response;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.TextFormat;
import com.google.api.services.sheets.v4.model.UpdateCellsRequest;
import com.google.api.services.sheets.v4.model.UpdateSheetPropertiesRequest;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.winterwell.ical.ICalEvent;
import com.winterwell.utils.Printer;
import com.winterwell.utils.TodoException;
import com.winterwell.utils.Utils;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.Period;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.time.TimeUtils;
import com.winterwell.web.app.Logins;

/**
 * See https://developers.google.com/calendar/api
 * 
 * @testedby GCalClientTest
 * 
 * @author Google, daniel
 *
 */
public class GCalClient {
	private static final String APP = "google.good-loop.com";
	private static final String APPLICATION_NAME = "Google Integration for Good-Loop";
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private static final String TOKENS_DIRECTORY_PATH = "tokens";
	private static final String LOGTAG = "GCalClient";
	private static Calendar _service;


	public GCalClient() {
	}
	
	/**
	 * The calendars you have opened (this is a subset of the ones you can read)
	 * @return
	 * @throws IOException
	 */
	public List getCalendarList() throws IOException {
		Calendar service = getService();
		CalendarList clist = service.calendarList();
		com.google.api.services.calendar.Calendar.CalendarList.List lr = clist.list();
		com.google.api.services.calendar.model.CalendarList clist2 = lr.execute();
		List<CalendarListEntry> items = clist2.getItems();
		return items;
	}
	
	/**
	 * Global instance of the scopes required by this quickstart. If modifying these
	 * scopes, delete your previously saved tokens/ folder.
	 */
	private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);
//	    private static final String CREDENTIALS_FILE_PATH = "config/credentials.json";

	/**
	 * Creates an authorized Credential object.
	 * 
	 * @param HTTP_TRANSPORT The network HTTP Transport.
	 * @return An authorized Credential object.
	 * @throws IOException If the credentials.json file cannot be found.
	 */
	private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
		Log.i(LOGTAG, "get credentials...");
		// Load client secrets.
		File credsFile = Logins.getLoginFile(APP, "credentials.json");
		if (credsFile == null) {
			throw new FileNotFoundException(Logins.getLoginsDir()+"/"+APP+"/credentials.json");
		}
		Log.d(LOGTAG, "...read credentials.json "+credsFile);
		InputStream in = new FileInputStream(credsFile);
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));		

		// Build flow and trigger user authorization request.
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
				clientSecrets, SCOPES)
						.setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
						.setAccessType("offline").build();
		int receiverPort = 7149;
		LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(receiverPort).build();
		Log.d(LOGTAG, "...get credentials AuthorizationCodeInstalledApp.authorize() with port "+receiverPort+"...");
		AuthorizationCodeInstalledApp acia = new AuthorizationCodeInstalledApp(flow, receiver);
		Credential cred = acia.authorize("user");
		Log.i(LOGTAG, "...get credentials "+cred);
		return cred;
	}

	static Calendar getService() {
		if (_service != null) return _service;
		Log.i(LOGTAG, "getService...");
		try {
			final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			 _service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
		                .setApplicationName(APPLICATION_NAME)
		                .build();
			return _service;
		} catch(Exception ex) {
			Log.i(LOGTAG, "getService :( "+ex); // make sure its logged
			throw Utils.runtime(ex);
		}
	}

	/**
	 * 
	 * @param w
	 * @return 0 = A
	 */
	public static String getBase26(int w) {
		assert w >= 0 : w;
		int a = 'A';
		if (w < 26) {
//			char c = (char) (a + w);
			return Character.toString(a + w);
		}
		int low = w % 26;
		int high = (w / 26) - 1; // -1 'cos this isnt true base 26 -- there's no proper 0 letter
		return getBase26(high) + getBase26(low);
	}

	/**
	 * 
	 * @param calendarId email or "primary"
	 * @return
	 */
	public com.google.api.services.calendar.model.Calendar getCalendar(String calendarId) {
		try {
			Calendar service = getService();
			com.google.api.services.calendar.model.Calendar c = service.calendars().get(calendarId).execute();
			return c;
		} catch (IOException ex) {
			throw Utils.runtime(ex);
		}
	}

    public List<Event> getEvents(String calendarId) {
        return getEvents(calendarId, null, null);
    }
    
    public List<Event> getEvents(String calendarId, Time start, Time end) {
        try {
            Calendar service = getService();
            Calendar.Events.List listReq = service.events().list(calendarId);
            Period period = new Period(start, end);
            if (start != null) listReq.setTimeMin(new DateTime(start.getTime()));
            if (end != null) listReq.setTimeMax(new DateTime(end.getTime()));
            Events events = listReq.execute();
            List<Event> items = events.getItems();
            
            // WTF? Google handling of recurring events is odd and inefficient
            List<Event> itemsInPeriod = new ArrayList();
            for (Event event : items) {
            	String s = event.getSummary();
				if (Utils.isEmpty(event.getRecurrence())) {
					// a one off event
					Period ePeriod = getPeriod(event);
					if (ePeriod!=null && period.intersects(ePeriod)) {
						itemsInPeriod.add(event);	
					} else {
						// Not in the period! skip
						Log.d(LOGTAG, "Skip "+s+" not in "+period);
					}
					continue;
				}
				// recurring
//				List<String> recur = event.getRecurrence(); // TODO use this instead of an http call
				Instances ris = service.events().instances(calendarId, event.getId());
	            if (start != null) ris.setTimeMin(new DateTime(start.getTime()));
	            if (end != null) ris.setTimeMax(new DateTime(end.getTime()));
	            Events rEvents = ris.execute();
	            List<Event> items2 = rEvents.getItems();
	            for(Event e2 : items2) {
	            	Period ePeriod = getPeriod(e2);
	            	if (ePeriod!=null && period.intersects(ePeriod)) {
						itemsInPeriod.add(e2);	
					} else {
						// Not in the period! skip
						Log.d(LOGTAG, "Skip recurring "+s+" not in "+period);
					}
	            }
			}
            // done
            return itemsInPeriod;
        } catch(Exception ex) {
            throw Utils.runtime(ex);
        }
    }
	
	public static ICalEvent toICalEvent(Event event) {
		ICalEvent e = new ICalEvent(toTime(event.getStart()), toTime(event.getEnd()), event.getSummary());
		e.description = event.getDescription();
		e.location = event.getLocation();
		// TODO more
		return e;
	}
	
	/**
	 * Convert the custom g-cal time object into our simple robust Time object 
	 * TODO test this, inc time zone
	 * @param edt
	 * @return
	 */
	public static Time toTime(EventDateTime edt) {
		DateTime dateTime = edt.getDateTime();
		if (dateTime==null) {
			dateTime = edt.getDate();
		}
		assert dateTime != null : edt;
		long t = dateTime.getValue();
		return new Time(t);
	}

	/**
	 * 
	 * @param calendarId Can be "primary"
	 * @param event
	 * @param sendNotifications
	 * @param addVideoConference
	 * @return
	 */
	public Event addEvent(String calendarId, Event event, boolean sendNotifications, boolean addVideoConference) {
		try {			
			Insert ereq = getService().events().insert(calendarId, event)
					.setSendNotifications(sendNotifications);
			// event!
			Event event2 = ereq.execute();
			// conference?
			if (addVideoConference) {
				// see https://developers.google.com/calendar/api/guides/create-events#conferencing
				Event confPlease = new Event();
				ConferenceData cd = confPlease.getConferenceData();
				if (cd==null) {
					cd = new ConferenceData();					
				}
				cd.setCreateRequest(
						new CreateConferenceRequest().setRequestId(Utils.getNonce())						
				);				
				confPlease.setConferenceData(cd);				
				Patch patchreq = getService().events().patch(calendarId, event2.getId(), confPlease)
						.setSendNotifications(sendNotifications);
				Event presp = patchreq.setConferenceDataVersion(1).execute();
				
				// TODO wrap the urls in link-trackers so we can see if people are attending
//				cd.setEntryPoints(ep);
				
				event2 = presp;
			}
			// insert!
			return event2;
		} catch (IOException e) {
			throw Utils.runtime(e);
		}
	}

	/**
	 * 
	 * @param event
	 * @return warning: can be null!
	 */
	public Period getPeriod(Event event) {
		assert event != null;
		// NB: I _think_ this will get the specific time of this instance for recurring events. ^Dan
		EventDateTime os = event.getOriginalStartTime();
		EventDateTime s = event.getStart();
//		if (s==null) s = event.getStart();
		if (s==null) {
			// a non-event?! They do seem to exist
			return null;
		}
		Time start = toTime(s);
		EventDateTime e = event.getEnd();		
		Time end = e==null? start.plus(TUnit.HOUR) : toTime(e);
		if (start.equals(end) && start.getHour()==0) {
			// Paranoia for an all day event (should be encoded as end=next-day)
			end = TimeUtils.getEndOfDay(end);
		}
		return new Period(start, end);
	}

	/**
	 * 
	 * @param event
	 * @param email
	 * @return true=accepted, false=declined, null=no-answer/tentative
	 */
	public Boolean isAttending(Event event, String email) {
		List<EventAttendee> attendees = event.getAttendees();
		if (attendees==null) {
			return true; // a solo calendar event
		}
		for (EventAttendee attendee : attendees) {
			if ( ! email.equalsIgnoreCase(attendee.getEmail())) continue;
			String rs = attendee.getResponseStatus();
			if ("accepted".equalsIgnoreCase(rs)) {
				return true;
			}
			if ("declined".equalsIgnoreCase(rs)) {
				return false;
			}
		}
		return null;
	}

	public static EventDateTime toEventDateTime(Time time) {
		// ISO8601 and RFC3339 are mostly interchangeable
		// c.f. https://ijmacd.github.io/rfc3339-iso8601/
		DateTime dateTime = DateTime.parseRfc3339(time.toISOString());
		EventDateTime start = new EventDateTime()
		    .setDateTime(dateTime)
		    .setTimeZone("GMT"); // is this needed??
		return start;

	}
	
}
