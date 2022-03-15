package com.winterwell.datalog;

import org.junit.Test;

import com.winterwell.datalog.server.GeoLiteLocator;
import com.winterwell.datalog.server.GeoLiteUpdateTask;

public class GeoLiteLocatorTest {
	@Test
	public void test() {
		// Instantiate GeoLiteLocator - will attempt to load CSVs and init prefix tree
		GeoLiteLocator gll = new GeoLiteLocator();
		// Run GeoLiteUpdateTask - will check if CSVs are absent or out-of-date and init again if needed
		new GeoLiteUpdateTask(gll).run();
		
		// Simple smoke test - Google public DNS should be in the US. 
		String googleDNSCountry = gll.getCountryCode("8.8.8.8");
		assert "US".equals(googleDNSCountry);
		System.out.println("GeoLite2 thinks Google DNS (8.8.8.8) is in country code \"" + googleDNSCountry + "\"");
		
		// An IP that isn't a string of dot separated decimals will throw a NumberFormatException
		try {
			String emptyIPCountry = gll.getCountryCode("");
			// This shouldn't print.
			System.out.println("Empty IP gives country code \"" + emptyIPCountry + "\"");
		} catch (NumberFormatException e) {
			System.out.println("GeoLite2 correctly threw a NumberFormatException for a degenerate IP.");
		}
		
	}
}
