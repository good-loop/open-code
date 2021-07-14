package com.goodloop.gcal.chatroundabout;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import com.goodloop.gcal.GCalClient;
import com.google.api.services.calendar.model.Calendar;
import com.winterwell.utils.Printer;
import com.winterwell.utils.io.CSVReader;

public class ChatRoundabout {

	public static void main(String[] args) {
		// TODO use AMain and have a running service which goes each Monday
		new ChatRoundabout().run();
	}

	private void run() {
		ArrayList<String> emailList = new ArrayList<String>();
		
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
		// System.out.println(Arrays.deepToString(emailList.toArray()));
		
		for (String i : emailList) {
			String email = i.split("	")[0];
			GCalClient gcc = new GCalClient();
			Calendar dw = gcc.getCalendar(email);
			System.out.println(dw);
		}
			
		// TODO filter out if on holiday
		// TODO Filter out if they already have a 121
		// TODO random pairings
		// TODO make events
	}
	
}
