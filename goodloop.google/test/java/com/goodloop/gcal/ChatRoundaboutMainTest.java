package com.goodloop.gcal;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Scanner;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;

import com.winterwell.utils.Utils;

public class ChatRoundaboutMainTest {
	
	private static final String FILENAME = "ChatRoundabout.txt";

	@Test
	public void doMainLoop() throws IOException {
		// Create last ran date file if not exist
		System.out.println("Create last ran date file if not exist...");
	    try {
	        File lastRanFile = new File(FILENAME);
	        if (lastRanFile.createNewFile()) {
	          System.out.println("File created: " + lastRanFile.getName());
	        } else {
	        	System.out.println(lastRanFile.getName() + "Already exist");
	        }
	      } catch (IOException e) {
	        System.out.println("An error occurred.");
	        e.printStackTrace();
	      }
	    
	    // Reading lastran.txt
	    System.out.println("Reading lastran.txt...");
	    String lastRanString = "";
	    try {
	        File lastRanFile = new File(FILENAME);
	        System.out.println("lastRanFile: " + lastRanFile);
	        Scanner myReader = new Scanner(lastRanFile);
	        System.out.println("myReader: " + myReader);
	        System.out.println("myReader.hasNextLine(): " + myReader.hasNextLine());
	        while (myReader.hasNextLine()) {
	          lastRanString = myReader.nextLine();
	        }
	        myReader.close();
	      } catch (IOException e) {
	        System.out.println("An error occurred.");
	        e.printStackTrace();
	      }

	    System.out.println("lastRanString: " + lastRanString);
	    // Logic: Is it on the same day as last run and is it on Monday
	    System.out.println("Today: " + LocalDate.now().toString());
		boolean onLastRD = LocalDate.now().toString().equals(lastRanString);
		// boolean isMonday = LocalDate.now().getDayOfWeek().equals(DayOfWeek.MONDAY);		
		//Override isMonday
		boolean isMonday = true;
		
		System.out.println("onLastRD: " + onLastRD);
		System.out.println("isMonday: " + isMonday);
		
		if (isMonday && !onLastRD) {
			// Run the service
			System.out.println("Chat-Roundabout: I am runnning");
			
			//Write last ran date
			System.out.println("Writing last ran date...");
			try {
				FileWriter myWriter = new FileWriter("ChatRoundabout.txt");
				String lastRan = LocalDate.now().toString();
				System.out.println("lastRan: " + lastRan);
				myWriter.write(lastRan);
				myWriter.close();
				System.out.println("Last ran date written");
			} catch (IOException e) {
				System.out.println("An error occurred.");
				e.printStackTrace();
			}
		} else {
			System.out.println("Chat-Roundabout: Did not ran");
		}
		
		Utils.sleep(10000);
	}
}
