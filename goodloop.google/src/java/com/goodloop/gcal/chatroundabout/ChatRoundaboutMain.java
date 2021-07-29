package com.goodloop.gcal.chatroundabout;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Scanner;
import java.io.File;
import java.io.FileWriter;

import com.winterwell.utils.Utils;
import com.winterwell.web.app.AMain;

public class ChatRoundaboutMain extends AMain {
	
	private static final String FILENAME = "ChatRoundabout.txt";
	
	public static void main(String[] args) throws IOException {
		ChatRoundaboutMain mymain = new ChatRoundaboutMain();
        mymain.doMain(args);
	}
	
	@Override
	protected void doMainLoop() throws IOException {
		
		// Create last ran date file if not exist
	    try {
	        File lastRanFile = new File(FILENAME);
	        if (lastRanFile.createNewFile()) {
	          System.out.println("File created: " + lastRanFile.getName());
	        } 
	      } catch (IOException e) {
	        System.out.println("An error occurred.");
	        e.printStackTrace();
	      }
	    
	    // Reading lastran.txt
	    String lastRanString = "";
	    try {
	        File lastRanFile = new File(FILENAME);
	        Scanner myReader = new Scanner(lastRanFile);
	        while (myReader.hasNextLine()) {
	          lastRanString = myReader.nextLine();;
	        }
	        myReader.close();
	      } catch (IOException e) {
	        System.out.println("An error occurred.");
	        e.printStackTrace();
	      }

	    // Logic: Is it on the same day as last run and is it on Monday
		boolean onLastRD = LocalDate.now().toString().equals(lastRanString);
		boolean isMonday = LocalDate.now().getDayOfWeek().equals(DayOfWeek.MONDAY);
		
//		System.out.println("onLastRD: " + onLastRD);
//		System.out.println("isMonday: " + isMonday);
		
		if (isMonday && !onLastRD) {
			// Run the service
			System.out.println("ChatRoundabout: Running");
			new ChatRoundabout().run();
			
			//Write last ran date
			try {
				FileWriter myWriter = new FileWriter("ChatRoundabout.txt");
				String lastRan = LocalDate.now().toString();
				myWriter.write(lastRan);
				myWriter.close();
				System.out.println("Last ran date written");
			} catch (IOException e) {
				System.out.println("An error occurred.");
				e.printStackTrace();
			}
		} else {
			System.out.println("ChatRoundabout: Today is not Monday or Today is the same day as last ran date, Abort.");
		}
		
		Utils.sleep(3600000);
	}
	
}
