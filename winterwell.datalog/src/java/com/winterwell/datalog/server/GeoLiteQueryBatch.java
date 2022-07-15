package com.winterwell.datalog.server;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.winterwell.datalog.DataLog;
import com.winterwell.datalog.DataLogConfig;
import com.winterwell.datalog.DataLogImpl;
import com.winterwell.datalog.Dataspace;
import com.winterwell.datalog.ESDataLogSearchBuilder;
import com.winterwell.datalog.ESStorage;
import com.winterwell.datalog.server.GeoLiteLocator;
import com.winterwell.es.client.ESHttpClient;
import com.winterwell.es.client.SearchRequest;
import com.winterwell.es.client.SearchResponse;
import com.winterwell.nlp.query.SearchQuery;
import com.winterwell.utils.io.ConfigFactory;
import com.winterwell.utils.time.Dt;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.time.TimeUtils;

/**
 * Very rough utility for getting geolocation breakdowns out of
 * legacy DataLog events without country data built in.
 * Connects to a DataLog server, gets time-separated blocks of
 * impressions, and runs all logged IPs through a local geolocation
 * DB. Prints a breakdown of country locations.
 * 
 * Usage:
 * - Set up ES tunnelling to a target server (eg datalognode-01) with $winterwell/config/scripts/elasticsearch-ssh-tunnel.sh
 * - Edit esUrl in ESConfig.java to use port 9900, so it uses the tunnel instead of local ES.
 * - Set start/end time to cover campaign duration
 * - Set time step, if examining a high-volume search where 10,000/day is too few
 * - Set up search query - eg adid:f9FSsduu or campaign:my-campaign-id
 * - Run
 * @author roscoe
 *
 */
public class GeoLiteQueryBatch {
	public static void main(String[] args) {
		Time start = TimeUtils.parseExperimental("2022-02-18T00:00:00.000Z");
		Time end = new Time();
		Dt step = new Dt(1, TUnit.DAY);
		String search = "evt:pixel+AND+campaign:CaptifyNS";
		Dataspace d = new Dataspace("green");
		
		// Set up the geolocator
		GeoLiteLocator gll = new GeoLiteLocator();
		GeoLiteUpdateTask glut = new GeoLiteUpdateTask(gll);
		glut.run();
		
		// Set up to query ES
		DataLogConfig dlConfig = ConfigFactory.get().getConfig(DataLogConfig.class);
		DataLogImpl dl = new DataLogImpl(dlConfig);
		ESStorage ess = (ESStorage) dl.getStorage();
		ESHttpClient esc = ess.client(d);
		esc.debug = false; // quiet curl logging
		
		// Keep a map of country codes to impression numbers + running count of all impressions inspected
		Map<String, Integer> countryCounts = new HashMap<String, Integer>();
		Integer allCount = 0;
		
		while (start.isBefore(end)) {
			System.out.println("Getting country breakdown for " + start.toString());
			ESDataLogSearchBuilder essb = new ESDataLogSearchBuilder(esc, d);		
			essb.setBreakdown(Arrays.asList("none"))
				.setQuery(new SearchQuery(search))
				.setNumResults(10)
				.setStart(start)
				.setEnd(start.plus(step));
			
			SearchRequest srq = essb.prepareSearch();		
			srq.setDebug(false);
			srq.setSize(10000);
			// Search!
			SearchResponse sr = srq.get();
			sr.check();
			
			// For every hit, get hit._source.ip and attempt to look up country
			List<Map> hits = sr.getHits();
			for (Map hit : hits) {
				Map src = (Map) hit.get("_source");
				String ips = (String) src.get("ip");
				for (String ip : ips.split(", ")) {
					GeoLiteLocator.GeoIPBlock block = gll.getLocation(ip);
					if (block == null) continue;
					Integer count = countryCounts.get(block.country);
					if (count == null) count = 0;
					countryCounts.put(block.country, count + 1);
					allCount++;
					break;
				}
			}
			start = start.plus(step);
		}
		
		final Double finalCount = Double.valueOf(allCount);
		System.out.println("Total: " + allCount);
		countryCounts.entrySet().stream()
			.sorted((a, b) -> b.getValue() - a.getValue())
			.forEach((e) -> 
				System.out.println(String.format(
					"%s: %d (%.2f%%)",
					e.getKey(),
					e.getValue(),
					(e.getValue() / finalCount) * 100
				))
			);
	}

}
