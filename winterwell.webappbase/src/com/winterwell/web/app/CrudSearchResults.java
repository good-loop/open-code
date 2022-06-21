package com.winterwell.web.app;

import java.util.List;

public class CrudSearchResults<T> {

	List<T> hits;
	
	Integer total;

	public List<T> getHits() {
		return hits;
	}
	
	public int getTotal() {
		return total==null? (hits==null? 0 : hits.size()) : total; // NB: slightly paranoid code
	}
}
