package com.winterwell.datalog.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.winterwell.utils.io.CSVReader;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.StopWatch;

/**
 * Consumes MaxMind's free GeoLite2 IPv4 CIDR-to-country CSVs and gives basic IP-to-country geolocation
 * @testedBy GeoLiteLocatorTest
 * @author roscoe
 *
 */
public class GeoLiteLocator {
	static final String LOGTAG = "GeoLiteLocator";
	
	// Binary tree - convert an IP to binary and traverse until you hit a leaf, that's the (probable) country it's in.
	static Node prefixes;
	
	public static final String GEOIP_FILES_PATH = "./geoip/";
	public static final String LOCATIONS_CSV_NAME = "GeoLite2-City-Locations-en.csv";
	public static final String BLOCKS_CSV_NAME = "GeoLite2-City-Blocks-IPv4.csv";
	
	// So we can reject malformed IPs
    Pattern IP_REGEX = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b");
	
	public GeoLiteLocator() {
		if (prefixes != null) return;
		
		prefixes = new Node();
		File locnsFile = new File(GEOIP_FILES_PATH, LOCATIONS_CSV_NAME);
		File blocksFile = new File(GEOIP_FILES_PATH, BLOCKS_CSV_NAME);
		
		try {
			Log.d(LOGTAG, "new GeoLiteLocator - prefix tree...");
			this.constructPrefixTree(locnsFile, blocksFile);
			Log.d(LOGTAG, "...new GeoLiteLocator - prefix tree - Done");
		} catch (FileNotFoundException e) {
			Log.e(LOGTAG, "Init: Missing or misformatted CSV file during init (normal for first run on new server): " + e.toString());
		} catch (ParseException e) {
			Log.e(LOGTAG, "Init: Failed to parse saved GeoLite2 CSV files: " + e.toString());
		}
	}
	
	/**

	 */
	
	/**
	 * Construct the binary IP prefix to country code search tree
	 * @param locnsFile The GeoLite2 location descriptions file
	 * @param blocksFile The GeoLite2 CIDR blocks file
	 * @throws IOException If the CSV changes 
	 */
	void constructPrefixTree(File locnsFile, File blocksFile) throws ParseException, FileNotFoundException {
		// Location desc file header and sample row:
		// gogeoname_id,locale_code,continent_code,continent_name,country_iso_code,country_name,is_in_european_union
		// 49518,en,AF,Africa,RW,Rwanda,0
		// CIDR blocks file Header and sample row:
		// network,geoname_id,registered_country_geoname_id,represented_country_geoname_id,is_anonymous_proxy,is_satellite_provider
		// 1.0.0.0/24,2077456,2077456,,0,0
		
		// throw these separately, we want to know which has failed
		if (!locnsFile.exists()) throw new FileNotFoundException(locnsFile.getAbsolutePath());
		if (!blocksFile.exists()) throw new FileNotFoundException(blocksFile.getAbsolutePath());
		
		// First assemble a mapping of numeric country IDs to two-letter ISO country codes, as that's what we want to store
		CSVReader locnsReader = new CSVReader(locnsFile);

		// Ensure file is in expected format
		locnsReader.setHeaders(locnsReader.next());
		if (!locnsReader.getHeaders().contains("geoname_id") || !locnsReader.getHeaders().contains("country_iso_code")) {
			locnsReader.close();
			throw new ParseException("Cannot init GeoLiteLocator: locations file " + locnsFile.getAbsolutePath() + " missing at least one header of [geoname_id, country_iso_code]", 0);
		}
				
		Map<String, GeoIPBlock> locNumToLocation = new HashMap<String, GeoIPBlock>();
		for (Map<String, String> row : locnsReader.asListOfMaps()) {
			GeoIPBlock lb = new GeoIPBlock(
				row.get("country_iso_code"),
				row.get("subdivision_1_iso_code"),
				row.get("subdivision_2_iso_code"),
				row.get("city_name")
			);
			locNumToLocation.put(row.get("geoname_id"), lb);
		}
		locnsReader.close();
		
		// New root node for search tree - will swap in once complete
		Node newPrefixes = new Node();
				
		// Parse the blocks file and construct searchable tree
		CSVReader blocksReader = new CSVReader(blocksFile);
		
		// Ensure file is in expected format
		blocksReader.setHeaders(blocksReader.next());
		if (!blocksReader.getHeaders().contains("network") || !blocksReader.getHeaders().contains("geoname_id")) {
			blocksReader.close();
			throw new ParseException("Cannot init GeoLiteLocator: blocks file " + blocksFile.getPath() + " missing at least one header of [network, geoname_id]", 0);
		}
		
		
		try {
			GeoIPBlock proxy = new GeoIPBlock("PROXY", null, null, null);
			GeoIPBlock satellite = new GeoIPBlock("SAT", null, null, null);
			
			StopWatch sw = new StopWatch();
			int i = 0;
			
			for (Map<String, String> row : blocksReader.asListOfMaps()) {
				// Split "128.64.32.16/28" into network and prefix-length parts
				String[] cidr = row.get("network").split("/");
				String network = cidr[0];
				
				// Convert decimal-dot IP to binary string & truncate to CIDR prefix length
				String networkBinary = ipToBinary(network).substring(0, Integer.parseInt(cidr[1]));
				
				// Find corresponding ISO country code
				String countryCode;
				
				// Some networks correspond to known proxies or satellite ISPs, so "location" is meaningless
				if (!"0".equals(row.get("is_anonymous_proxy"))) {
					newPrefixes.put(networkBinary, proxy);
				} else if (!"0".equals(row.get("is_satellite_provider"))) {
					newPrefixes.put(networkBinary, satellite);
				} else {
					// Looks like a real place!
					newPrefixes.put(networkBinary, locNumToLocation.get(row.get("geoname_id")));
				}
				i++;
			}
			Log.d(LOGTAG, "GeoLiteLocator init took " + sw.getTime() / 1000.0 + " seconds to ingest " + i + " rows");
		} catch (IllegalArgumentException e) {
			Log.e(LOGTAG, "Bad data in GeoLite2 CSV: Encountered overlapping CIDR blocks");
			return; // leave prefixes as an empty node - will return "" for everything.
		} finally {
			blocksReader.close();
		}
		
		// Construction complete, swap in updated prefix tree
		prefixes = newPrefixes;
	}
	
	private String zeroes = "00000000"; // for padding
	
	/**
	 * Convert dot-decimal representation of an IPV4 address to binary string
	 * @param ip eg "192.168.1.1"
	 * @return eg "11000000101010000000000100000001"
	 */
	private String ipToBinary(String ip) {
		String bin = "";
		for (String byteStr : ip.split("\\.")) {
			Integer byteInt = Integer.parseInt(byteStr);
			String byteBin = Integer.toBinaryString(byteInt);
			bin += (zeroes.substring(0, 8 - byteBin.length()) + byteBin);
		}
		return bin;
	}
	
	/**
	 * If this IP falls within a known network prefix, return its probable ISO country code.
	 * @param ip eg "62.30.12.102"
	 * @return eg "GB". Never null, empty string for failure.
	 * @throws NumberFormatException in the case of a malformed IP
	 */
	public GeoIPBlock getLocation(String ip) throws NumberFormatException {
		Matcher m = IP_REGEX.matcher(ip);
		if (!m.find()) {
			Log.w(LOGTAG, "Couldn't validate IP for geolocation: \"" + ip + "\"");
		} else {
			try {
				Object maybeCC = prefixes.get(ipToBinary(m.group()));
				if (maybeCC != null && maybeCC instanceof GeoIPBlock) return (GeoIPBlock) maybeCC;
			} catch (IndexOutOfBoundsException e) {
				Log.w(LOGTAG, "IP address ended before finding location: " + ip);
			}
		}
		return null;
	}
	
	/**
	 * Structure for storing locations as specified in MaxMind GeoIP2/GeoLite2 city-level IP location database
	 * Doesn't cover all fields! Just the ones we care about.
	 * @author roscoe
	 */
	public class GeoIPBlock {
		/** ISO 3166 country code */
		public String country;
		/** ISO 3166-2 first-level subdivision - eg UK countries, US states */
		public String subdiv1;
		/** ISO 3166-2 second-level subdivision - eg UK counties etc */
		public String subdiv2;
		/** Name of city */
		public String city;
		
		public GeoIPBlock(String _country, String _subdiv1, String _subdiv2, String _city) {
			this.country = _country;
			this.subdiv1 = _subdiv1;
			this.subdiv2 = _subdiv2;
			this.city = _city;
		}
	}
}

/**
 * Binary tree for matching network prefixes (as strings of '0'/'1') to locations.
 * I mean, it can probably be used for a lot of other things. But that's what it does here.
 * @author roscoe
 *
 */
class Node {
	Object zero;
	Object one;
	
	/**
	 *  Traverse in until a leaf node is reached. This can happen before the end of the address string.
	 * @param address String containing only '1' and '0' (ie matching "^[01]*$")
	 * @return The leaf node
	 */
	public Object get(String address) {
		return get(address, 0);
	}
	
	/**
	 *  Traverse in until a leaf node is reached. This can happen before the end of the address string.
	 * @param address String containing only '1' and '0' (ie matching "^[01]*$")
	 * @param index Current progress into the address string
	 * @return The leaf node
	 */
	public Object get(String address, int index) {
		if (address.length() <= index) return null;
		Object next = (address.charAt(index) == '1') ? one : zero;
		if (!(next instanceof Node)) return next;
		return ((Node)next).get(address, index + 1);
	}
	
	/**
	 * Store a value at a binary address in the tree. Will create all intermediate nodes.
	 * @param address String containing only '1' and '0' (ie matching "^[01]*$")
	 * @param obj The object to store
	 */
	public void put(String address, Object obj) {
		put(address, obj, 0);
	}
	
	/**
	 * Store a value at a binary address in the tree. Will create all intermediate nodes.
	 * @param address String containing only '1' and '0' (ie matching "^[01]*$")
	 * @param obj The object to store
	 * @param index Current progress into the address string
	 */
	public void put(String address, Object obj, int index) {
		boolean nextBit = (address.charAt(index) == '1');
		// last index? store obj at this.zero or this.one as appropriate
		if (index == address.length() - 1) {
			// Writing a leaf node over an internal node? Something's wrong with the dataset.
			if ((nextBit && (one instanceof Node)) || (!nextBit && (zero instanceof Node))) {
				throw new IllegalArgumentException("Tried to store a prefix which would overwrite a more specific one");
			}
			if (nextBit) {
				one = obj;
			} else {
				zero = obj;
			}
			return;
		}
		Object nextNode;
		if (nextBit) {
			nextNode = one;
			if (nextNode == null) {
				nextNode = new Node();
				one = nextNode;
			}
		} else {
			nextNode = zero;
			if (nextNode == null) {
				nextNode = new Node();
				zero = nextNode;
			}
		}
		// Writing an internal node over a leaf node? Something's wrong with the dataset.
		if (!(nextNode instanceof Node)) {
			throw new IllegalArgumentException("Tried to store a prefix which would fall within a more general one");
		}
		((Node)nextNode).put(address, obj, index + 1);
	}
}


