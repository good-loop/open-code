package com.goodloop.data.charity;

import com.goodloop.data.Money;
import com.winterwell.data.KStatus;
import com.winterwell.es.ESKeyword;
import com.winterwell.es.ESNoIndex;
import com.winterwell.utils.time.Time;

/**
 * This is a successor to SoGive's Output.java class
 * @author daniel
 *
 */
public class Impact {
	
	/**
	 * Charity/NGO ID
	 */
	@ESKeyword
	String charity;
	
	/**
	 * When was this calculation for?
	 */
	Time date;
	
	/**
	 * e.g. "tree(s)
	 */	
	String name;
	
	Money amount;
	
	/**
	 * How many units of this impact have been committed/bought?
	 */
	double n = 1.0;
	
	/**
	 * Multiplier for proportional impacts (eg "10 trees per 1000 impressions" = 0.1)
	 */
	Double rate;
	
	/**
	 * Input for proportional impacts (eg "impressions", "clicks", "installs")
	 */
	String input;
	
	/**
	 * DRAFT: this impact is still running and output blocks should be bought, PUBLISHED: this impact is complete
	 */
	KStatus progress;
	
	/**
	 * Where does this impact calculation come from? a url
	 */
	@ESNoIndex
	String ref;
	
	@ESNoIndex
	String notes;

	public void setDate(Time date) {
		this.date = date;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setAmount(Money amount) {
		this.amount = amount;
	}

	public void setN(double n) {
		this.n = n;
	}

	public void setRef(String ref) {
		this.ref = ref;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public void setCharity(String id) {
		this.charity = id;
	}
	
	
}
