package com.goodloop.gcal.chatroundabout;

import java.time.DayOfWeek;
import java.time.LocalDate;

import com.winterwell.utils.Utils;
import com.winterwell.web.app.AMain;

public class ChatRoundaboutMain extends AMain {
	
	@Override
	protected void doMainLoop() throws Exception {
		// Check if it is Monday morning
		boolean isMonday = LocalDate.now().getDayOfWeek() == DayOfWeek.MONDAY;
		if (isMonday) {
			// TODO check last run date
			// TODO Dance
		}
		
		Utils.sleep(36000000);
	}
	
}
