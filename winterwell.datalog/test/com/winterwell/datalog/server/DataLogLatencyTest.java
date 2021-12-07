package com.winterwell.datalog.server;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.winterwell.datalog.DataLogConfig;
import com.winterwell.datalog.DataLogEvent;
import com.winterwell.datalog.DataLogHttpClient;
import com.winterwell.datalog.Dataspace;
import com.winterwell.nlp.query.SearchQuery;
import com.winterwell.utils.Dep;
import com.winterwell.utils.DepContext;
import com.winterwell.utils.Printer;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.Period;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.web.FakeBrowser;

/**
 * NB: not using junit cos we dont want to autorun this slow thing
 * @author daniel
 *
 */
public class DataLogLatencyTest {

	private static boolean gby;


	public static void main(String[] args) {
		DataLogLatencyTest dlt = new DataLogLatencyTest();
//		gby = true; // still slow and no logs
		dlt.saveEventLatency_liveServer(); // SLOW :(
//		dlt.saveEventLatency_testServer(); // FAST
//		dlt.saveEventLatency_localServer(); // FAST
		Printer.out("SUCCESS!");
	}

	private void saveEventLatency_testServer() {
		DataLogConfig dlc = new DataLogConfig();
		dlc.logEndpoint = "https://testlg.good-loop.com/lg";
		dlc.dataEndpoint = "https://testlg.good-loop.com/data";
		dlc.debug = true;
		try(DepContext c = Dep.with(DataLogConfig.class, dlc)) {
			saveEventLatency_2();
		}
	}

	

	private void saveEventLatency_localServer() {
		DataLogConfig dlc = new DataLogConfig();
		dlc.logEndpoint = "http://locallg.good-loop.com/lg";
		dlc.dataEndpoint = "http://locallg.good-loop.com/data";
		dlc.debug = true;
		try(DepContext c = Dep.with(DataLogConfig.class, dlc)) {
			saveEventLatency_2();
		}
	}

	

	private void saveEventLatency_liveServer() {
		DataLogConfig dlc = new DataLogConfig();
		dlc.logEndpoint = "https://lg.good-loop.com/lg";
		dlc.dataEndpoint = "https://lg.good-loop.com/data";
		dlc.debug = true;
		try(DepContext c = Dep.with(DataLogConfig.class, dlc)) {
			saveEventLatency_2();
		}
	}

	
	public void saveEventLatency_2() {
		String tag = "latency_"+Utils.getRandomString(6);
		
		Dataspace ds = new Dataspace("test");
		DataLogHttpClient dlhc = new DataLogHttpClient(ds);
		dlhc.setDebug(true);
		dlhc.getConfig().debug = true;
		dlhc.initAuth("good-loop.com");
		
		String _gby = gby? "gby_id_"+tag : null;
		// NB: needs 2+ props to avoid being treated as a simple stat!
		DataLogEvent e = new DataLogEvent(ds, _gby, 1, new String[] {tag}, 
				new ArrayMap("oxid","latencytester","country","GB","city","Edinburgh"));
		dlhc.save(e);
		
		// wait and see...
		Time time = new Time();
		while(true) {
			Utils.sleep(1000);
			SearchQuery q = new SearchQuery("evt:"+tag);
			List<DataLogEvent> es = dlhc.getEvents(q, 5);
			if (es!=null && ! es.isEmpty()) {
				Printer.out(es);
				Printer.out(time.dt(new Time()).convertTo(TUnit.MINUTE));
				break;
			}
			
			SearchQuery q2 = new SearchQuery("oxid:latencytester");
			List<DataLogEvent> es2 = dlhc.getEvents(q2, 5);
			if (es2!=null && ! es2.isEmpty()) {
				Printer.out(es2);
			}
			
			String _gby2 = gby? "gby_id_"+time.getMinutes()+tag : null;
			DataLogEvent en = new DataLogEvent(ds, _gby2, 1, new String[] {tag}, new ArrayMap(
					"oxid","latencytester",
					"cause","worry"));
			dlhc.save(en);

			System.out.println(time.dt(new Time()).convertTo(TUnit.MINUTE));
			Utils.sleep(20000);
		}			
	}
	
	
}
