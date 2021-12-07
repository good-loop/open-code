package com.winterwell.datalog;

import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.winterwell.nlp.query.SearchQuery;
import com.winterwell.utils.Dep;
import com.winterwell.utils.DepContext;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.io.ConfigFactory;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;

public class DataLogHttpClientTest {

	private static DepContext context;


	@BeforeClass
	public static void beforeTests() {
		context = Dep.setContext("DataLogHttpClientTest");
		DataLogConfig dlc = ConfigFactory.get().getConfig(DataLogConfig.class);
		dlc.dataEndpoint = "https://testlg.good-loop.com/data";
		dlc.logEndpoint = "https://testlg.good-loop.com/lg";
		Dep.set(DataLogConfig.class, dlc);
	}
	
	@AfterClass
	public static void afterTests() {
		context.close();
	}
	
	@Test
	public void testGetEvents() {
		// get all
		DataLogHttpClient dlc = new DataLogHttpClient(new Dataspace("gl"));
		List<DataLogEvent> events = dlc.getEvents(null, 5);
		System.out.println(events);
	}

	@Test
	public void testBreakdown() {
		// get all
		DataLogHttpClient dlc = new DataLogHttpClient(new Dataspace("gl"));
		SearchQuery q = new SearchQuery("evt:spend");
		Breakdown breakdown = new Breakdown("pub", "count", KBreakdownOp.sum);
		Map<String, Double> events = dlc.getBreakdown(q, breakdown);
		System.out.println(events);
	}

	
	@Test
	public void testBreakdownCount() {
		DataLogHttpClient dlc = new DataLogHttpClient(new Dataspace("gl"));
		dlc.initAuth("good-loop.com");
		assert dlc.isAuthorised();
		SearchQuery sqd = new SearchQuery("evt:donation");
		List<DataLogEvent> donEvents = dlc.getEvents(sqd, 10);
		// NB: the count field is always present on DataLogEvents
		Breakdown bd = new Breakdown("cid", "count", KBreakdownOp.sum);
		Map<String, Double> dontnForAdvert = dlc.getBreakdown(sqd, bd);	
		System.out.println(dontnForAdvert);
	}
	

	@Test
	public void testSave() {
		DataLogHttpClient dlc = new DataLogHttpClient(new Dataspace("test"));
		dlc.setDebug(true);
		dlc.initAuth("good-loop.com");
		assert dlc.getConfig().logEndpoint.contains("testlg") : dlc.getConfig().logEndpoint;
		Map<String, ?> props = new ArrayMap(
			"huh", 1,
			"os", "Dummy OS"
		);
		String gby = "gby_testsave_1";
		DataLogEvent event = new DataLogEvent(dlc.dataspace, gby, 1, new String[] {"testsave1"}, props);
		event.setTime(new Time().minus(TUnit.WEEK));
		Object ok = dlc.save(event);
		System.out.println(ok);
		Utils.sleep(1000);
		dlc.setPeriod(new Time().minus(TUnit.MONTH), null);
		List<DataLogEvent> events = dlc.getEvents(new SearchQuery("evt:testsave1"), 10);
		System.out.println(events);
		assert ! events.isEmpty();
	}
	
}
