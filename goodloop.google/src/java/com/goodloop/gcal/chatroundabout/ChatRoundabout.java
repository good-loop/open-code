package com.goodloop.gcal.chatroundabout;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.goodloop.gcal.GCalClient;
import com.google.api.services.calendar.model.Calendar;
import com.google.api.services.calendar.model.Event;
import com.winterwell.utils.Printer;
import com.winterwell.utils.io.CSVReader;
import com.winterwell.utils.time.Time;

public class ChatRoundabout {

	public static void main(String[] args) {
		// TODO use AMain and have a running service which goes each Monday
		new ChatRoundabout().run();
	}
	
	private void run() {
		ArrayList<String> emailList = new ArrayList<String>();
		
		// Get 121 Date (Next Friday)
		LocalDate nextFriday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
		System.out.println(nextFriday);
		
		// Loop through CSV to get email list
		CSVReader r = new CSVReader(new File("data/staff.csv"));
		for (String[] row : r) {
			if (row[4].equals("Employee")) {
				String name = row[0];
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
				emailList.add(email+"	"+row[2]);
			}
		}
		
		// Loop through all email
		for (String i : emailList) {
			String email = i.split("	")[0];
			GCalClient gcc = new GCalClient();
			List<Event> dw = gcc.getEvents(email);
			
			// Check if anyone on holiday
			for (Event event : dw) {
				String events = event.toString();
				if (events.contains(nextFriday.toString()) && events.contains("Holiday")) {
					System.out.println("Hoilday found on Friday: \n" + events);
				}
			}
		}

			
		// TODO filter out if on holiday
		// TODO Filter out if they already have a 121
		// TODO random pairings
		// TODO make events
	}
	
}
