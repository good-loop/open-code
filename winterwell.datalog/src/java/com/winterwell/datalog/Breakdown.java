package com.winterwell.datalog;

import java.util.Arrays;
import java.util.List;

import com.winterwell.es.client.agg.Aggregation;
import com.winterwell.es.client.agg.Aggregations;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.TodoException;

/**
 * 
 * e.g. pub{"count":"sum"}
 * @author daniel
 *
 */
public final class Breakdown {

	/**
	 * e.g. "pub" or ",pub" for top-level + breakdown-by-pub, or "" for just top-level (i.e. the total).
	 * Never null or empty! null is converted into [""]
	 */
	final String[] by;
	/**
	 * e.g. "count" or "price"
	 * Never null
	 */
	final String field;
	/**
	 * Currently assumed to be sum and ignored??
	 */
	final KBreakdownOp op;
	
	public List<String> getBy() {
		return Arrays.asList(by);
	}
	
	/**
	 * 
	 * @param by e.g. "pub" or ",pub" for top-level + breakdown-by-pub. Can be null
	 * NB: a trailing comma will be ignored, but a leading one works.
	 * @param field e.g. "count" or "price"
	 * @param operator e.g. "sum"
	 */
	public Breakdown(String by, String field, KBreakdownOp operator) {
		this.by = by==null? new String[]{""} : by.split(",");
		this.field =field;
		this.op = operator;
		assert ! field.isEmpty();
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for(String b : by) {
			sb.append(b);
			sb.append("{\""+field+"\":\""+op+"\"}");
			sb.append(",");
		}
		StrUtils.pop(sb, 1);
		return sb.toString();
	}
}
