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

public class DataLogLatencyTest {

	public static void main(String[] args) {
		DataLogLatencyTest dlt = new DataLogLatencyTest();
		dlt.saveEventLatency_testServer();
	}

//	@Test
	public void saveEventLatency_testServer() {
		DataLogConfig dlc = new DataLogConfig();
		dlc.logEndpoint = "https://testlg.good-loop.com/lg";
		dlc.dataEndpoint = "https://testlg.good-loop.com/data";
		dlc.debug = true;
		String tag = "latency_"+Utils.getRandomString(6);
		
		try(DepContext c = Dep.with(DataLogConfig.class, dlc)) {
			Dataspace ds = new Dataspace("test");
			DataLogHttpClient dlhc = new DataLogHttpClient(ds);
			
			DataLogEvent e = new DataLogEvent(tag, 1);
			Object ok = dlhc.save(e);
			
			// wait and see...
			Time time = new Time();
			while(true) {
				Utils.sleep(10000);
				SearchQuery q = new SearchQuery("evt:"+tag);
				List<DataLogEvent> es = dlhc.getEvents(q, 1);
				if ( ! es.isEmpty()) {
					Printer.out(es);
					Printer.out(time.dt(new Time()));
					break;
				}
				System.out.println(time.dt(new Time()).convertTo(TUnit.MINUTE));
			}			
		}
	}
	
	
}
