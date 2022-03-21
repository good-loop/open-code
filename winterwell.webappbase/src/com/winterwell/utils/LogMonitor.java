package com.winterwell.utils;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.apache.commons.lang3.StringUtils;

import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.time.RateCounter;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;
import com.winterwell.web.app.Emailer;
import com.winterwell.web.email.EmailConfig;
import com.winterwell.web.email.SimpleMessage;

public class LogMonitor {
	private final static String[]ignoreKeywords = {"IGNORE", "MINOR"};
	private final static String[]alertKeywords = {"ERROR", "SERIOUS", "RepeatDonationActor"};
	
	private static ArrayList<String> alertList = new ArrayList<String>();
	
	private static LocalDateTime getDateTime(String line) {
		String lastLineDateTime = line.substring(0, 24);
		return LocalDateTime.parse(lastLineDateTime, DateTimeFormatter.ofPattern("MMM d, u h:m:s a", Locale.ENGLISH));
	}
	
	public static void main(String[] args) throws InterruptedException {
		Proc proc = new Proc("tail -f -n 100 /home/wing/Desktop/read.txt");
		String outputString = new String();
		LocalDateTime lastDateTime = null;
		
		while (true) {
			proc.start();
			// proc.waitfor() is not working
			TimeUnit.SECONDS.sleep(2);
			outputString = proc.getOutput();
			proc.close();
			String[] outputLines = outputString.split(System.getProperty("line.separator"));
			
			// Record time of last line
			lastDateTime = getDateTime(outputLines[outputLines.length -1]);
			System.out.println(lastDateTime);
			
			for (String line : outputLines) {
				if (getDateTime(line).isBefore(lastDateTime)) continue;
				if (StringUtils.indexOfAny(line, ignoreKeywords) != -1) continue;
				if (StringUtils.indexOfAny(line, alertKeywords) != -1) {
					alertList.add(line);
				}
			}
			rc = new RateCounter(TUnit.HOUR.dt);
			
			System.out.println(alertList.size());
		}
		
		
		
		
		// TODO Send Email
	}
	
	public Emailer getEmailer() {
		File propsFile = new File(FileUtils.getWinterwellDir(), "logins/google.good-loop.com/email.properties");
		Properties props = FileUtils.loadProperties(propsFile);		
		
		EmailConfig ec = new EmailConfig();
		ec.emailServer = props.getProperty("emailServer").trim();
		ec.emailFrom = props.getProperty("emailFrom").trim();
		ec.emailPassword = props.getProperty("emailPassword").trim();
		ec.emailPort = 465;
		ec.emailSSL = true;		
		
		return new Emailer(ec);
	}
	
	public void sendEmail(String emailContent, String toEmail) throws AddressException {
		Emailer emailer = getEmailer();
		
		InternetAddress from = emailer.getBotEmail();
		InternetAddress email = new InternetAddress(toEmail) ;
		email.setAddress(toEmail);
		String firstName = toEmail.split("@")[0].toLowerCase();
		String firstNameCap = firstName.substring(0,1).toUpperCase() + firstName.substring(1);
		
		String appName = "LogMonitor";
		String readableDate = LocalDate.now().toString().replace(" 00:00:00 GMT", "");
		String subject = appName+": Report "+readableDate;
		StringBuilder body = new StringBuilder();
		body.append("Hello "+firstNameCap);
		body.append("\r\n\r\n"+emailContent);
		body.append("\r\n\r\nI am a bot, beep boop.");
		
		SimpleMessage msg = new SimpleMessage(from, email, subject, body.toString());
		emailer.send(msg);
	}
}
