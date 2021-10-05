package com.goodloop.data;

import java.util.Map;

import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.time.Time;

/**
 * FIXME load and stash data
 * @author daniel
 *
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

	

}
