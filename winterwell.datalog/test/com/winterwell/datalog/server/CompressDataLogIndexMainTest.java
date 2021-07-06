package com.winterwell.datalog.server;

import static org.junit.Assert.*;

import org.junit.Test;

import com.winterwell.es.client.ESConfig;
import com.winterwell.es.client.ESHttpClient;
import com.winterwell.es.client.SearchRequest;
import com.winterwell.es.client.SearchResponse;
import com.winterwell.es.client.agg.Aggregation;
import com.winterwell.es.client.agg.Aggregations;
import com.winterwell.utils.Printer;

public class CompressDataLogIndexMainTest {

	@Test
	public void testCountQuery() {
		ESHttpClient esc = new ESHttpClient(new ESConfig());		
		SearchRequest s = esc.prepareSearch("testtransform");
		Aggregation agg = Aggregations.sum("count", "count");
		s.addAggregation(agg);
		s.setDebug(true);
		SearchResponse sr = s.get();
		Printer.out(sr.getAggregations());
	}

}
