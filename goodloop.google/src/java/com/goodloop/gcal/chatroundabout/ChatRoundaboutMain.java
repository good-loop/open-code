package com.goodloop.gcal.chatroundabout;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;

import com.winterwell.utils.Utils;
import com.winterwell.web.app.AMain;

public class ChatRoundaboutMain extends AMain {
	
	public static void main(String[] args) throws IOException {
		ChatRoundaboutMain mymain = new ChatRoundaboutMain();
        mymain.doMain(args);
	}
	
	@Override
	protected void doMainLoop() throws IOException {
		// Check if it is Monday morning
		System.out.println("I am running.");
		boolean isMonday = LocalDate.now().getDayOfWeek() == DayOfWeek.MONDAY;
		if (isMonday) {
			// TODO Dance
			new ChatRoundabout().run();
		}
		
//		Utils.sleep(86400000);
		Utils.sleep(30000);
	}
	
}
