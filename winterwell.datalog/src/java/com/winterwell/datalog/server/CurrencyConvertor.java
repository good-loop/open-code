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
import com.winterwell.utils.time.TimeUtils;
import com.winterwell.web.FakeBrowser;
import com.winterwell.web.LoginDetails;
import com.winterwell.web.app.Logins;

/**
 * FIXME load and stash data
 * @author Wing, daniel
 * @testedby {@link CurrencyConvertorTest}
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
		if (from==to) { // no-op
			return amount;
		}
		DataLogEvent rateFromCache = cache.get(date.toISOStringDateOnly());
		if (rateFromCache == null) {
			DataLogEvent rate = loadCurrDataFromES(date);
			if (rate == null) {
				// looking for today's rate?
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
		
		double conversionVal = convertES2_conversionRate(rateFromCache, from, to);
		if (conversionVal==0) {
			// fallback
			return convert2_hardCoded(amount);
		}
		return amount*conversionVal;
	}

	private double convertES2_conversionRate(DataLogEvent rateFromCache, KCurrency _from, KCurrency _to) {
		String fromXtoY = _from + "2" + _to;
		// Direct?
		double conversionVal = MathUtils.toNum(rateFromCache.getProp(fromXtoY));		
		if (conversionVal!=0) {
			return conversionVal;
		}
		// inverse?
		String invcurrencyConversion = _to + "2" + _from;
		double invconversionVal = MathUtils.toNum(rateFromCache.getProp(invcurrencyConversion));	
		if (invconversionVal!=0) {
			conversionVal = 1/invconversionVal;		
			return conversionVal;
		}
		// via USD
		if (_to != KCurrency.USD && _from!=KCurrency.USD) {
			double from2USD = convertES2_conversionRate(rateFromCache, _from, KCurrency.USD);
			double USD2to = convertES2_conversionRate(rateFromCache, KCurrency.USD,_to);
			if (from2USD!=0 && USD2to!=0) {
				conversionVal = from2USD*USD2to;
				return conversionVal;
			}
		}
		Log.d("CurrencyConvertor", "No data for currency:"+_from+" date: " +date+" - using hardcoded FX");
		return 0;
	}
	
	private static final String currrate = "currrate";
	
	/**
	 * fetch and save-to-remote
	 * @return
	 * @throws IOException
	 */
	public DataLogEvent fetchCurrRate() throws IOException {
		// We can only fetch rate in base currency of EUR due to using free tier API Key
//		URL urlForGetRequest = new URL("http://api.exchangeratesapi.io/v1/latest?access_key="+getAPIKey().apiKey+"&symbols=USD,GBP,AUD,MXN,JPY,HKD,CNY");
		FakeBrowser fb = new FakeBrowser();
		String orgRate = fb.getPage("https://api.exchangerate.host/latest?base=EUR");				
		JSONObject obj = new JSONObject(orgRate);
		
		// Doing Math		
		JSONObject rates = obj.getJSONObject("rates");
		// we use USD as our base currency, and store everything as XXX2USD
		// assume: arbitrage on X->EUR->USD vs X->USD is small and ignorable, as is X->USD->Y vs X->Y
		Double EUR2USD = MathUtils.toNum(rates.get("USD"));
		Map objMap = new ArrayMap("EUR2USD", EUR2USD);
		for(KCurrency currency : KCurrency.values()) {
			if (currency==KCurrency.USD) continue; // don't need USD2USD
			if ( ! rates.has(currency.name())) {
				continue; // skip
			}			
			double EUR2XXX = MathUtils.toNum(rates.get(currency.name()));
			double XXX2USD = (1 / EUR2XXX) * EUR2USD;
			objMap.put(currency.name()+"2USD", XXX2USD);
		}
				
		// long timestamp = Instant.now().getEpochSecond(); // Don't need timestamp now
		
		DataLogEvent event = new DataLogEvent("fx", 1, currrate, objMap);
		DataLogRemoteStorage.saveToRemoteServer(event);
		
		return event;
	}
	

	DataLogEvent loadCurrDataFromES(Time loadingDate) {
		DataLogHttpClient dlc = new DataLogHttpClient(new Dataspace("fx"));
		Time startDay = TimeUtils.getStartOfDay(loadingDate);
		Time endDay = TimeUtils.getEndOfDay(loadingDate);
		dlc.setPeriod(startDay, endDay);
		dlc.initAuth("good-loop.com");
		SearchQuery sq = new SearchQuery("evt:"+currrate);
		dlc.setDebug(true);
		// get a few (should just be 1)
		List<DataLogEvent> rate = dlc.getEvents(sq , 5);		
		if (rate.size() == 0) {
			return null;
		}
		// ??pick the closest to time? But we should only store one per day
		return rate.get(0);
	}
	

}
