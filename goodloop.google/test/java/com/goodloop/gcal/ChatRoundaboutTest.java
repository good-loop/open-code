package com.goodloop.gcal;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Properties;

import javax.mail.internet.InternetAddress;

import org.junit.Test;

import com.goodloop.gcal.chatroundabout.ChatRoundaboutConfig;
import com.winterwell.utils.Dep;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.time.Time;
import com.winterwell.web.ConfigException;
import com.winterwell.web.app.AMain;
import com.winterwell.web.app.Emailer;
import com.winterwell.web.email.EmailConfig;
import com.winterwell.web.email.SimpleMessage;

public class ChatRoundaboutTest extends AMain<ChatRoundaboutConfig> {
	
	@Test
	public void testEmailer() {
		LocalDate _nextFriday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
		Time nextFriday = new Time(_nextFriday.toString());
		
		String logString = "Testing Emailer";
		
		// Send email
		if (initFlag) return;
		initFlag = true;
		File propsFile = new File(FileUtils.getWinterwellDir(), "open-code/goodloop.google/config/email.properties");
		if ( ! propsFile.exists()) {
			propsFile = new File(FileUtils.getWinterwellDir(), "logins/local.properties");
			if ( ! propsFile.exists()) {
				System.out.println("Please make a file with email login details here: "+propsFile);
				throw new ConfigException("Please symlink the logins/local.properties file or make a file with email login details here: "+propsFile+".");
			}
		}
		Properties props = FileUtils.loadProperties(propsFile);				
		EmailConfig ec = new EmailConfig();
		ec.emailServer = props.getProperty("emailServer").trim();
		ec.emailFrom = props.getProperty("emailFrom").trim();
		ec.emailPassword = props.getProperty("emailPassword").trim();
		ec.emailPort = 465;
		ec.emailSSL = true;		
		
		Emailer emailer = new Emailer(ec);
		String appName = "ChatRoundabout";
		String subject = appName+": Weekly Report "+nextFriday.toISOStringDateOnly();
		StringBuilder body = new StringBuilder();
		body.append("\r\nChatRoundabout ran on :"+nextFriday.toISOStringDateOnly());
		body.append("\r\n\r\n"+logString+"\r\n");
		
		InternetAddress from = emailer.getBotEmail();
		from.setAddress("wing@good-loop.com");
		InternetAddress email = emailer.getBotEmail();
		email.setAddress("wing@good-loop.com");
		SimpleMessage msg = new SimpleMessage(from, email, subject, body.toString());
		emailer.send(msg);
	}
	
	
}
