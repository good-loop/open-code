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
import com.winterwell.json.JSONObject;
import com.winterwell.nlp.query.SearchQuery;
import com.winterwell.utils.MathUtils;
import com.winterwell.utils.TodoException;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Cache;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;
import com.winterwell.web.LoginDetails;
import com.winterwell.web.app.Logins;

/**
 * FIXME load and stash data
 * @author Wing, daniel
 * @tesedby {@link CurrencyConvertorTest}
 */
public class CurrencyConvertor {

	
//	private LoginDetails getAPIKey() {
//		// this could be set in logins/logins.currencyconvertor.properties
//		LoginDetails ld = Logins.get("currencyconvertor");
//		return ld;
//	}
	
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
	double convert2_hardCoded(double cidDntn) {
		String currencyConversion = from + "_" + to;
		Double conversionVal = CURRENCY_CONVERSION.get(currencyConversion);
		if (conversionVal==null) {
			// inverse?
			String invcurrencyConversion = to + "_" + from;
			Double invconversionVal = CURRENCY_CONVERSION.get(invcurrencyConversion);	
			if (invconversionVal==null) {
				throw new TodoException("Setup currency conversion for "+currencyConversion);
			}
			conversionVal = 1/invconversionVal;
		}
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
		"GBP_EUR", 1.16,
		"GBP_CAD", 1.58,
		"USD_AUD", 1.380,
		"USD_EUR", 0.85,
		"EUR_AUD", 1.62
	);
	
	// Turns out do not need this part
//	public Time latestDateWithRate(Time latestDate) throws IOException {
//		Time today = new Time();
//		if (loadCurrDataFromES(latestDate) == null) {
//			if (latestDate.toISOStringDateOnly().equalsIgnoreCase(today.toISOStringDateOnly())) {
//				fetchCurrRate();
//				System.out.println("Fetching Today's rate and write into ES.");
//				Utils.sleep(1500);
//				latestDateWithRate(latestDate.minus(1, TUnit.DAY));
//			} else if (latestDate.toISOStringDateOnly().equalsIgnoreCase(today.minus(1, TUnit.DAY).toISOStringDateOnly())) {
//				latestDateWithRate(latestDate.minus(1, TUnit.DAY));
//			} else if (latestDate.toISOStringDateOnly().equalsIgnoreCase(today.minus(2, TUnit.DAY).toISOStringDateOnly())) {
//				return today;
//			}
//		}
//		System.out.println("Reading currency rate of "+latestDate.toISOStringDateOnly());
//		return latestDate;
//	}
	
	static final Cache<String, DataLogEvent> cache = new Cache(100);
	
	/**
	 * New conversion class, fetch currency rate in ES. Uses a cache so it is fast once warmed up.
	 * @param amount
	 * @return
	 * @throws IOException 
	 */
	public double convertES(double amount) throws IOException {
		if (amount==0) { // fast zero
			return 0;
		}
		DataLogEvent rateFromCache = cache.get(date.toISOStringDateOnly());
		if (rateFromCache == null) {
			DataLogEvent rate = loadCurrDataFromES(date);
			if (rate == null) {
				if (date.toISOStringDateOnly().equals(new Time().toISOStringDateOnly())) {
					rate = fetchCurrRate();
					if (rate == null) {
						Log.d("CurrencyConvertor", "fetchCurrRate failed for "+date+" - using hardcoded FX");
						return convert2_hardCoded(amount);
					}
				} else {
					// fail :(
					Log.d("CurrencyConvertor", "No data for "+date+" - using hardcoded FX");
					return convert2_hardCoded(amount);
				}
			}
			cache.put(date.toISOStringDateOnly(), rate);
			rateFromCache = rate;
		} // ./rateFromCache
		String currencyConversion = from + "2" + to;
		double conversionVal = MathUtils.toNum(rateFromCache.getProp(currencyConversion));		
		if (conversionVal==0) {
			// inverse?
			String invcurrencyConversion = to + "2" + from;
			double invconversionVal = MathUtils.toNum(rateFromCache.getProp(invcurrencyConversion));	
			if (invconversionVal==0) {
				Log.d("CurrencyConvertor", "No data for currency:"+from+" date: " +date+" - using hardcoded FX");
				return convert2_hardCoded(amount);
			}
			conversionVal = 1/invconversionVal;
		}
		return amount*conversionVal;
	}
	
	private static final String currrate = "currrate";
	
	public DataLogEvent fetchCurrRate() throws IOException {
		// We can only fetch rate in base currency of EUR due to using free tier API Key
//		URL urlForGetRequest = new URL("http://api.exchangeratesapi.io/v1/latest?access_key="+getAPIKey().apiKey+"&symbols=USD,GBP,AUD,MXN,JPY,HKD,CNY"); 
		URL urlForGetRequest = new URL("https://api.exchangerate.host/latest?base=EUR");
		
		// Minor: FakeBrowser handles http connections nicely
		HttpURLConnection con = (HttpURLConnection) urlForGetRequest.openConnection();
		con.setRequestMethod("GET");
		String orgRate = new String();
		try(BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"))) {
		    StringBuilder response = new StringBuilder();
		    String responseLine = null;
		    while ((responseLine = br.readLine()) != null) {
		        response.append(responseLine.trim());
		    }
		    orgRate = response.toString();
		} catch (Exception e) {
			Log.d("CurrencyConvertor", e);
			return null;
		}
		
		JSONObject obj = new JSONObject(orgRate);
		
		// Doing Math
		JSONObject rates = obj.getJSONObject("rates");
		Double EUR2USD = MathUtils.toNum(rates.get("USD"));
		Double GBP2USD = 1 / MathUtils.toNum(rates.get("GBP")) * EUR2USD;
		Double AUD2USD = 1 / MathUtils.toNum(rates.get("AUD")) * EUR2USD;
		Double MXN2USD = 1 / MathUtils.toNum(rates.get("MXN")) * EUR2USD;
		Double JPY2USD = 1 / MathUtils.toNum(rates.get("JPY")) * EUR2USD;
		Double CNY2USD = 1 / MathUtils.toNum(rates.get("CNY")) * EUR2USD;
		Double HKD2USD = 1 / MathUtils.toNum(rates.get("HKD")) * EUR2USD;
		
		// long timestamp = Instant.now().getEpochSecond(); // Don't need timestamp now
		
		Map objMap = new ArrayMap(
				"EUR2USD", EUR2USD, 
				"GBP2USD", GBP2USD);
		DataLogEvent event = new DataLogEvent("fx", 1, currrate, objMap);
		DataLogRemoteStorage.saveToRemoteServer(event);
		
		con.disconnect();
		return event;
	}

	DataLogEvent loadCurrDataFromES(Time loadingDate) {
		DataLogHttpClient dlc = new DataLogHttpClient(new Dataspace("fx"));
		dlc.initAuth("good-loop.com");
		SearchQuery sq = new SearchQuery("evt:"+currrate+" AND time:"+loadingDate.toISOStringDateOnly());
		dlc.setDebug(true);
		List<DataLogEvent> rate = dlc.getEvents(sq , 1);
		
		if (rate.size() == 0) return null;
		return rate.get(0);
	}
	

}
