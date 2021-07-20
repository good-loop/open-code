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
	
	public static void main(String[] args) throws IOException {
		ChatRoundaboutMain mymain = new ChatRoundaboutMain();
        mymain.doMain(args);
	}
	
	@Override
	protected void doMainLoop() throws IOException {
		// Create last ran date file if not exist
	    try {
	        File myObj = new File("ChatRoundaboutLastRan.txt");
	        if (myObj.createNewFile()) {
	          System.out.println("File created: " + myObj.getName());
	        } else {
	        }
	      } catch (IOException e) {
	        System.out.println("An error occurred.");
	        e.printStackTrace();
	      }
	    
	    // Reading lastran.txt
	    String lastRanOnFile = "";
	    try {
	        File myObj = new File("ChatRoundaboutLastRan.txt");
	        Scanner myReader = new Scanner(myObj);
	        while (myReader.hasNextLine()) {
	          String data = myReader.nextLine();
	          lastRanOnFile = data;
	        }
	        myReader.close();
	      } catch (IOException e) {
	        System.out.println("An error occurred.");
	        e.printStackTrace();
	      }

	    // Logic: Is it on the same day as last run and is it on Monday
		boolean onLastRD = LocalDate.now().toString() == lastRanOnFile;
		boolean isMonday = LocalDate.now().getDayOfWeek() == DayOfWeek.MONDAY;
		
		if (isMonday && !onLastRD) {
			// Run the service
			new ChatRoundabout().run();
			
			//Write last ran date
			try {
				FileWriter myWriter = new FileWriter("ChatRoundaboutLastRan.txt");
				String lastRan = LocalDate.now().toString();
				myWriter.write(lastRan);
				myWriter.close();
				System.out.println("Last ran date written");
			} catch (IOException e) {
				System.out.println("An error occurred.");
				e.printStackTrace();
			}
		}
		
//		Utils.sleep(86400000);
		Utils.sleep(10000);
	}
	
}
