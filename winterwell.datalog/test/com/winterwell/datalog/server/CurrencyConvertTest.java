package com.winterwell.datalog.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.Map;

import org.junit.Test;

import com.goodloop.data.CurrencyConvertor;
import com.goodloop.data.KCurrency;
import com.winterwell.datalog.DataLogConfig;
import com.winterwell.datalog.DataLogEvent;
import com.winterwell.datalog.DataLogHttpClient;
import com.winterwell.datalog.DataLogRemoteStorage;
import com.winterwell.datalog.Dataspace;
import com.winterwell.es.client.ESConfig;
import com.winterwell.es.client.ESHttpClient;
import com.winterwell.es.client.GetRequest;
import com.winterwell.json.JSONObject;
import com.winterwell.nlp.query.SearchQuery;
import com.winterwell.utils.Dep;
import com.winterwell.utils.Printer;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.io.ConfigFactory;
import com.winterwell.utils.time.Time;

public class CurrencyConvertTest {
	
	@Test
	public void testFetchSaveLoad() throws IOException {
		init();

		CurrencyConvertor cc = new CurrencyConvertor(KCurrency.GBP, KCurrency.USD, new Time());
		DataLogEvent e = cc.fetchCurrRate();
		Printer.out(e);
		Utils.sleep(1500);
		
		DataLogEvent e2 = cc.loadCurrDataFromES();
		assert e != null;
		assert e2 != null;
		assert e.getProp("GBP2USD").equals(e2.getProp("GBP2USD")) : e2;
	}

	@Test
	public void testLoad() throws IOException {
		init();

		CurrencyConvertor cc = new CurrencyConvertor(KCurrency.GBP, KCurrency.USD, new Time());
		
		DataLogEvent e2 = cc.loadCurrDataFromES();
		assert e2 != null;
	}
	
	private void init() {
//		DataLogConfig dlc = ConfigFactory.get().getConfig(DataLogConfig.class);
		
		DataLogConfig dlc = new DataLogConfig();
		Dep.set(DataLogConfig.class, dlc);
	}

	public static Double currConvert(Object curr, String amount) {
		
		// TODO Fetch event currRate from ES with the latest date
		DataLogHttpClient d = new DataLogHttpClient(new Dataspace("fx"));
		SearchQuery sq = new SearchQuery("evt:currrate");
		System.out.println(d.getEvents(sq , 1));
		
		Double currRate = 1.00;
		if (curr == "GBP") {
			currRate = 1.34;
		} else if (curr == "EUR") {
			currRate = 1.15;
		}
		Double amountusd =  Double.valueOf(curr.toString()).doubleValue() * currRate;
		
		return amountusd;
	}
}
