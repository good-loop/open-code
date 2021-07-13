package com.goodloop.gcal.chatroundabout;

import java.io.File;

import com.winterwell.utils.Printer;
import com.winterwell.utils.io.CSVReader;

public class ChatRoundabout {

	public static void main(String[] args) {
		// TODO use AMain and have a running service which goes each Monday
		new ChatRoundabout().run();
	}

	private void run() {
		// TODO list of emails
		CSVReader r = new CSVReader(new File("data/staff.csv"));
		for (String[] row : r) {
			String name = row[0];
			String firstName = name.split(" ")[0];
			String email = firstName.toLowerCase()+"@good-loop.com";
			Printer.out(email+"	"+row[2]);
		}
		// TODO filter out if on holiday
		// TODO Filter out if they already have a 121
		// TODO random pairings
		// TODO make events
	}
	
}
