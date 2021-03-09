package com.winterwell.utils.time;

import org.junit.Test;

public class TimeParserTest {

	@Test
	public void testParseExponentialFormatEpochTime() {
		String st = "1.611854108e12";
		TimeParser tp = new TimeParser();
		Time t = tp.parseExperimental(st);
//		System.out.println(t.toISOString());
//		System.out.println(t.getTime());
		assert t.toISOString().equals("2021-01-28T17:15:08Z");
	}
	
	@Test
	public void testParsePeriodEndOf() {
		TimeParser tp = new TimeParser();
		tp.setNow(new Time(2020,11,5));
		{
			Time eom = tp.parseExperimental("end of month", null);
			assert Math.abs(eom.diff(new Time(2020,12,1))) < 1000 : eom;
		}
		{
			Time eom = tp.parseExperimental("end-of-month", null);
			assert Math.abs(eom.diff(new Time(2020,12,1))) < 1000 : eom;
		}
		{
			Time eow = tp.parseExperimental("end-of-week", null);
			assert eow.equals(new Time(2020,11,9)) : eow.toISOString();
		}
	}


	@Test
	public void testParseApr2020() {
		TimeParser tp = new TimeParser();
		Time t = tp.parseExperimental("Apr 2020", null);
		assert t.getMonth() == 4;
		assert t.toISOString().equals("") : t.toISOString();
	}
}
