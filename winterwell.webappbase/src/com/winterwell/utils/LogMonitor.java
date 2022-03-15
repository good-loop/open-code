package com.winterwell.utils;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.apache.commons.lang3.StringUtils;

import com.winterwell.utils.io.FileUtils;
import com.winterwell.web.app.Emailer;
import com.winterwell.web.email.EmailConfig;
import com.winterwell.web.email.SimpleMessage;

public class LogMonitor {
	private final static String[]ignoreKeywords = {"IGNORE", "MINOR"};
	private final static String[]alertKeywords = {"ERROR", "SERIOUS", "RepeatDonationActor"};
	
	private static ArrayList<String> alertList = new ArrayList<String>();
	
	public static void main(String[] args) throws InterruptedException {
		Proc proc = new Proc("tail -f /home/wing/Desktop/sogive.log.1");
		proc.start();
		// proc.waitfor() is not working
		TimeUnit.SECONDS.sleep(1);
		proc.close();
		String out = proc.getOutput();
		String[] lines = out.split(System.getProperty("line.separator"));
		for (String line : lines) {
			if (StringUtils.indexOfAny(line, ignoreKeywords) != -1) return;
			if (StringUtils.indexOfAny(line, alertKeywords) != -1) {
				alertList.add(line);
			}
		}
		
		System.out.println(alertList.size());
		
		// If there are things to alert
		if (alertList.size() > 0) {
			// TODO Send Email
		}
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
