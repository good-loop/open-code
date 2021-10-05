package com.goodloop.data;

import java.util.Currency;

/**
 * // ISO 4217 £
 * https://www.iso.org/iso-4217-currency-codes.html
 * 
 * 
 * ?? Refactor to replace with {@link Currency}??
 * 
 * @author daniel
 *
 */
public enum KCurrency {

	GBP("£"), USD("$"), AUD("A$"), EUR("€"), MXN("MX$"), JPY("￥"), CNY("￥"),
	
	/**
	 * HACK: allow Money objects to also represent %s and other multipliers. 
	 * @deprecated Not actually, but use with caution 
	 */
	MULTIPLY("x");
	
	public final String symbol;

	KCurrency(String symbol) {
		this.symbol = symbol;
	}
	
}
