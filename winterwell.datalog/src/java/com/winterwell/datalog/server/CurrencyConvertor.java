package com.winterwell.datalog.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.goodloop.data.KCurrency;
import com.winterwell.datalog.DataLogEvent;
import com.winterwell.datalog.DataLogHttpClient;
import com.winterwell.datalog.DataLogRemoteStorage;
import com.winterwell.datalog.Dataspace;
import com.winterwell.datalog.server.CurrencyConvertorTest;
import com.winterwell.json.JSONObject;
import com.winterwell.nlp.query.SearchQuery;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;

/**
 * FIXME load and stash data
 * @author Wing, daniel
 * @tesedby {@link CurrencyConvertorTest}
 */
public class CurrencyConvertor {

	private KCurrency from;
	private KCurrency to;
	private Time date;

	public CurrencyConvertor(KCurrency from, KCurrency to, Time date) {
		this.from = from;
		this.to = to;
		this.date = date;
	}

	@Override
	public String toString() {
		return "CurrencyConvertor[from=" + from + ", to=" + to + ", date=" + date + "]";
	}

	/**
	 * Old conversion class, hard coded currency rate
	 * @param cidDntn
	 * @return
	 */
	public double convert(double cidDntn) {
		String currencyConversion = from + "_" + to;
		Double conversionVal = CURRENCY_CONVERSION.get(currencyConversion);
		return cidDntn*conversionVal;
	}
	
	/**
	 * HACK - estimate conversions to handle adding conflicting currencies
	 * Sourced from https://www.x-rates.com/table/?from=GBP&amount=1 Sep 20, 2021 16:43
	 * EUROS sourced from https://www.google.com/search?q=euro+to+pound&oq=euro+to+pound Sep 20, 2021 16:46
	 */
	static final Map<String,Double> CURRENCY_CONVERSION = new ArrayMap(
		"GBP_USD", 1.365,
		"GBP_AUD", 1.885,
		"USD_AUD", 1.380,
		"USD_GBP", 0.732,
		"AUD_GBP", 0.530,
		"AUD_USD", 0.724,
		"EUR_GBP", 0.86,
		"GBP_EUR", 1.16,
		"EUR_AUD", 1.62,
		"AUD_EUR", 0.62,
		"EUR_USD", 1.17,
		"USD_EUR", 0.85
	);
	
	public Time latestDateWithRate(Time latestDate) throws IOException {
		if (loadCurrDataFromES(latestDate) == null) {
			fetchCurrRate();
			Utils.sleep(1500);
			if (loadCurrDataFromES(latestDate) == null) {
				latestDateWithRate(latestDate.minus(1, TUnit.DAY));
			}
		}
		System.out.println("Reading currency rate of "+latestDate);
		return latestDate;
	}
	
	/**
	 * New conversion class, fetch currency rate in ES
	 * @param amount
	 * @return
	 * @throws IOException 
	 */
	public double convertES(double amount) throws IOException {
		DataLogEvent rate = loadCurrDataFromES(latestDateWithRate(date));
		String currencyConversion = from + "2" + to;
		Double conversionVal = new Double(rate.getProp(currencyConversion).toString());
		return amount*conversionVal;
	}
	
	private static final String currrate = "currrate";

	public DataLogEvent fetchCurrRate() throws IOException {
		// We can only fetch rate in base currency of EUR due to using free tier API Key
//		URL urlForGetRequest = new URL("http://api.exchangeratesapi.io/v1/latest?access_key=81dd51bbdbf39740e59cfa5ae3835537&symbols=USD,GBP,AUD,MXN,JPY,HKD,CNY");
		URL urlForGetRequest = new URL("http://api.exchangeratesapi.io/v1/latest?access_key=5ddbce9daf299ed4b46804a0101c5046&symbols=USD,GBP,AUD,MXN,JPY,HKD,CNY"); // API Key for testing
		HttpURLConnection con = (HttpURLConnection) urlForGetRequest.openConnection();
		con.setRequestMethod("GET");
		
		// Minor: FakeBrowser handles http connections nicely
		String orgRate = new String();
		try(BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"))) {
		    StringBuilder response = new StringBuilder();
		    String responseLine = null;
		    while ((responseLine = br.readLine()) != null) {
		        response.append(responseLine.trim());
		    }
		    orgRate = response.toString();
		}
		
		JSONObject obj = new JSONObject(orgRate);
		
		// Doing Math
		Double EUR2USD = Double.parseDouble(obj.getJSONObject("rates").get("USD").toString());
		Double GBP2USD = 1 / Double.parseDouble(obj.getJSONObject("rates").get("GBP").toString()) * EUR2USD;
		Double AUD2USD = 1 / Double.parseDouble(obj.getJSONObject("rates").get("AUD").toString()) * EUR2USD;
		Double MXN2USD = 1 / Double.parseDouble(obj.getJSONObject("rates").get("MXN").toString()) * EUR2USD;
		Double JPY2USD = 1 / Double.parseDouble(obj.getJSONObject("rates").get("JPY").toString()) * EUR2USD;
		Double CNY2USD = 1 / Double.parseDouble(obj.getJSONObject("rates").get("CNY").toString()) * EUR2USD;
		Double HKD2USD = 1 / Double.parseDouble(obj.getJSONObject("rates").get("HKD").toString()) * EUR2USD;
		
		// long timestamp = Instant.now().getEpochSecond(); // Don't need timestamp now
		
		Map objMap = new ArrayMap("EUR2USD", EUR2USD, "GBP2USD", GBP2USD);
		DataLogEvent event = new DataLogEvent("fx", 1, currrate, objMap);
		DataLogRemoteStorage.saveToRemoteServer(event);
		
		con.disconnect();
		return event;
	}

	public DataLogEvent loadCurrDataFromES(Time loadingDate) {
		DataLogHttpClient dlc = new DataLogHttpClient(new Dataspace("fx"));
		dlc.initAuth("good-loop.com");
		SearchQuery sq = new SearchQuery("evt:"+currrate+" AND time:"+loadingDate.toISOStringDateOnly());
		dlc.setDebug(true);
		List<DataLogEvent> rate = dlc.getEvents(sq , 1);
		
		if (rate.size() == 0) return null;
		return rate.get(0);
	}
	

}
