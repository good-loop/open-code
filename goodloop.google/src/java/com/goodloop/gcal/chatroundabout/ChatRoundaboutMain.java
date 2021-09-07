package com.goodloop.gcal.chatroundabout;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Scanner;
import java.io.File;
import java.io.FileWriter;

import com.winterwell.utils.Utils;
import com.winterwell.utils.time.TUnit;
import com.winterwell.web.app.AMain;

public class ChatRoundaboutMain extends AMain<ChatRoundaboutConfig> {
	
	private static final String FILENAME = "ChatRoundabout.txt";
	
	public static void main(String[] args) throws IOException {
		ChatRoundaboutMain mymain = new ChatRoundaboutMain();
        mymain.doMain(args);
	}
	
	public ChatRoundaboutMain() {
		super("chatroundabout", ChatRoundaboutConfig.class);
	}
	
	@Override
	protected void doMain2() {
		// ChatRoundabout should be idempotent within a given week.
		// A repeated call should (painstakingly) fail to make any events, because they already exist
		try {
			new ChatRoundabout(getConfig()).run();
			pleaseStop = true;
		} catch (IOException e) {
			throw Utils.runtime(e);
		}
	}
	
}
